package org.col.admin.task.importer;

import org.col.admin.task.importer.acef.AcefNeoInserter;
import org.col.admin.task.importer.dwca.DwcaInserter;
import org.col.admin.task.importer.neo.NeoDb;
import org.col.admin.task.importer.neo.NotUniqueRuntimeException;
import org.col.admin.task.importer.neo.model.*;
import org.col.admin.task.importer.neo.traverse.Traversals;
import org.col.api.model.Classification;
import org.col.api.vocab.DataFormat;
import org.col.api.vocab.Issue;
import org.col.api.vocab.Origin;
import org.gbif.nameparser.api.Rank;
import org.neo4j.graphdb.*;
import org.neo4j.helpers.collection.Iterables;
import org.neo4j.helpers.collection.Iterators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 *
 */
public class Normalizer implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(Normalizer.class);
  private final DataFormat format;
  private final File sourceDir;
  private final NeoDb store;
  private InsertMetadata meta;

  public Normalizer(NeoDb store, File sourceDir, DataFormat format) {
    this.sourceDir = sourceDir;
    this.store = store;
    this.format = format;
  }

  /**
   * Run the normalizer and close the store at the end.
   *
   * @throws NormalizationFailedException
   */
  @Override
  public void run() throws NormalizationFailedException {
    run(true);
  }

  /**
   * Run the normalizer.
   *
   * @param closeStore Should the store be closed after running or on exception?
   * @throws NormalizationFailedException
   */
  public void run(boolean closeStore) throws NormalizationFailedException {
    LOG.info("Start normalization of {}", store);
    try {
      // batch import verbatim records
      insertData();
      // insert normalizer db relations, create implicit nodes if needed and parse names
      normalize();
      // matches names and taxon concepts and builds metrics per name/taxon
      matchAndCount();
      LOG.info("Normalization succeeded");

    } catch (Exception e){
      LOG.error("Normalizer failed", e);
      throw e;

    } finally {
      if (closeStore) {
        store.close();
        LOG.info("Normalizer store shut down");
      }
    }
  }

  private void matchAndCount() {

  }

  private void normalize() {
    // cleanup synonym & parent relations
    cutSynonymCycles();
    relinkSynonymChains();
    preferSynonymOverParentRel();

    // process the denormalized classifications of accepted taxa
    applyDenormedClassification();

    // set correct ROOT and PROPARTE labels for easier access
    store.updateLabels();

    // updates the taxon instances with infos derived from neo4j relations
    store.updateTaxonStoreWithRelations();

    LOG.info("Relation setup completed.");
  }

  /**
   * Applies the classification given as denormalized higher taxa terms to accepted taxa
   * after the parent / accepted relations have been applied.
   * We need to be careful as the classification coming in first via the parentNameUsage(ID) terms
   * is variable and must not always include a rank.
   *
   * The classification is not applied to basionymGroup!
   */
  private void applyDenormedClassification() {
    LOG.info("Start processing higher denormalized classification ...");
    if (!meta.isDenormedClassificationMapped()) {
      LOG.info("No higher classification mapped");
      return;
    }

    store.process(Labels.ALL,10000, new NeoDb.NodeBatchProcessor() {
      @Override
      public void process(Node n) {
        if (n.hasLabel(Labels.TAXON)) {
          RankedName rn = NeoProperties.getRankedName(n);
          // the highest current parent of n
          RankedName highest = findHighestParent(n);
          // only need to apply classification if highest exists and is not already a kingdom, the denormed classification cannot add to it anymore!
          if (highest != null && highest.rank != Rank.KINGDOM) {
            NeoTaxon t = store.get(n);
            if (t.classification != null) {
              applyClassification(highest, t.classification);
            }
          }
        }
      }

      @Override
      public void commitBatch(int counter) {
        LOG.info("Higher classifications processed for {} taxa", counter);
      }
    });
  }

  private RankedName findHighestParent(Node n) {
    // the highest current parent of n
    RankedName highest = null;
    if (meta.isParentNameMapped()) {
      // verify if we already have a classification, that it ends with a known rank
      Node p = Iterables.lastOrNull(Traversals.PARENTS.traverse(n).nodes());
      highest = p == null ? null : NeoProperties.getRankedName(p);
      if (highest != null
          && !highest.node.equals(n)
          && !highest.rank.notOtherOrUnranked()
      ) {
        LOG.debug("Node {} already has a classification which ends in an uncomparable rank.", n.getId());
        addIssueRemark(n, null, Issue.CLASSIFICATION_NOT_APPLIED);
        return null;
      }
    }
    if (highest == null) {
      // otherwise use this node
      highest = NeoProperties.getRankedName(n);
    }
    return highest;
  }

  /**
   * Applies the classification lc to the given RankedName taxon
   * @param taxon
   * @param cl
   */
  private void applyClassification(RankedName taxon, Classification cl) {
    // first modify classification to only keep those ranks we want to apply!
    // exclude lowest rank from classification to be applied if this taxon is rankless and has the same name
    if (taxon.rank == null || taxon.rank.isUncomparable()) {
      Rank lowest = cl.getLowestExistingRank();
      if (lowest != null && cl.getByRank(lowest).equalsIgnoreCase(taxon.name)) {
        cl.setByRank(lowest, null);
      }
    }
    // ignore same rank from classification if accepted
    if (!taxon.node.hasLabel(Labels.SYNONYM) && taxon.rank != null) {
      cl.setByRank(taxon.rank, null);
    }
    // ignore genus and below for basionymGroup
    // http://dev.gbif.org/issues/browse/POR-2992
    if (taxon.node.hasLabel(Labels.SYNONYM)) {
      cl.setGenus(null);
      cl.setSubgenus(null);
    }

    // now reconstruct the given classification as linked neo4j nodes
    // reusing existing nodes if possible, otherwise creating new ones
    // and at the very end apply that classification to the taxon.node
    Node parent = null;
    Rank parentRank = null;
    // from kingdom to subgenus
    for (Rank hr : Classification.RANKS) {
      if ((taxon.rank == null || !taxon.rank.higherThan(hr)) && cl.getByRank(hr) != null) {
        // test for existing usage with that name & rank
        boolean found = false;
        for (Node n : store.byScientificName(cl.getByRank(hr), hr)) {
          if (parent == null) {
            // make sure node does also not have a higher linnean rank parent
            Node p = Iterables.firstOrNull(Traversals.CLASSIFICATION.traverse(n).nodes());
            if (p == null) {
              // aligns!
              parent = n;
              parentRank = hr;
              found = true;
              break;
            }

          } else {
            // verify the parents for the next higher rank are the same
            // we dont want to apply a contradicting classification with the same name
            Node p = Traversals.parentOf(n);
            Node p2 = Traversals.parentWithRankOf(n, parentRank);
            if ((p != null && p.equals(parent)) || (p2 != null && p2.equals(parent))) {
              parent = n;
              parentRank = hr;
              found = true;
              break;
            }
          }
        }
        if (!found) {
          // persistent new higher taxon if not found
          Node lowerParent = store.createAccepted(Origin.DENORMED_CLASSIFICATION, cl.getByRank(hr), hr).node;
          // insert parent relationship?
          store.assignParent(parent, lowerParent);
          parent = lowerParent;
          parentRank = hr;
        }
      }
    }
    // finally apply lowest parent to initial node
    store.assignParent(parent, taxon.node);
  }

  /**
   * Sanitizes synonym relations and cuts cycles at lowest rank
   */
  private void cutSynonymCycles() {
    LOG.info("Cleanup synonym cycles");
    final String query = "MATCH (s:ALL)-[sr:SYNONYM_OF]->(x)-[:SYNONYM_OF*]->(s) RETURN sr LIMIT 1";

    int counter = 0;
    try (Transaction tx = store.getNeo().beginTx()) {
      Result result = store.getNeo().execute(query);;
      while (result.hasNext()) {
        Relationship sr = (Relationship) result.next().get("sr");

        Node syn = sr.getStartNode();

        NeoTaxon su = store.get(syn);
        su.addIssue(Issue.CHAINED_SYNOYM);
        su.addIssue(Issue.PARENT_CYCLE);
        store.put(su);
        // this is serious. Report id
        String taxonID = NeoProperties.getTaxonID(syn);

        RankedName created = store.createPlaceholder(Origin.MISSING_ACCEPTED, Issue.CHAINED_SYNOYM, taxonID);
        store.createSynonymRel(syn, created.node);
        sr.delete();

        if (counter++ % 100 == 0) {
          LOG.debug("Synonym cycles cut so far: {}", counter);
        }
        result = store.getNeo().execute(query);;
      }
      tx.success();
    }
    LOG.info("{} synonym cycles resolved", counter);
  }

  /**
   * Sanitizes synonym relations relinking synonym of basionymGroup to make sure basionymGroup always point to a direct accepted taxon.
   */
  private void relinkSynonymChains() {
    LOG.info("Relink synonym chains to single accepted");
    final String query = "MATCH (s:ALL)-[sr:SYNONYM_OF*]->(x)-[:SYNONYM_OF]->(t:TAXON) " +
        "WHERE NOT (t)-[:SYNONYM_OF]->() " +
        "RETURN sr, t LIMIT 1";

    int counter = 0;
    try (Transaction tx = store.getNeo().beginTx()) {
      Result result = store.getNeo().execute(query);
      while (result.hasNext()) {
        Map<String, Object> row = result.next();
        Node acc = (Node) row.get("t");
        for (Relationship sr : (Collection<Relationship>) row.get("sr")) {
          Node syn = sr.getStartNode();
          addIssue(syn, Issue.CHAINED_SYNOYM);
          store.createSynonymRel(syn, acc);
          sr.delete();
          counter++;
        }
        if (counter++ % 100 == 0) {
          LOG.debug("Synonym chains cut so far: {}", counter);
        }
        result = store.getNeo().execute(query);
      }
      tx.success();
    }
    LOG.info("{} synonym chains resolved", counter);
  }


  /**
   * Sanitizes relations by preferring synonym relations over parent rels.
   * (Re)move parent relationship for basionymGroup.
   * If basionymGroup are parents of other taxa relinks relationship to the accepted
   * presence of both confuses subsequent imports, see http://dev.gbif.org/issues/browse/POR-2755
   */
  private void preferSynonymOverParentRel() {
    LOG.info("Cleanup relations, preferring synonym over parent relations");
    int parentOfRelDeleted = 0;
    int parentOfRelRelinked = 0;
    int childOfRelDeleted = 0;
    int childOfRelRelinkedToAccepted = 0;
    try (Transaction tx = store.getNeo().beginTx()) {
      for (Node syn : Iterators.loop(store.getNeo().findNodes(Labels.SYNONYM))) {
        // if the synonym is a parent of another child taxon - relink accepted as parent of child
        Set<Node> accepted = Traversals.acceptedOf(syn);
        for (Relationship pRel : syn.getRelationships(RelType.PARENT_OF, Direction.OUTGOING)) {
          Node child = pRel.getOtherNode(syn);
          if (accepted.contains(child)) {
            // accepted is also the parent. Simply delete the parent rel in this case
            pRel.delete();
            parentOfRelDeleted++;
          } else {
            pRel.delete();
            String synonymName = NeoProperties.getScientificNameWithAuthor(syn);
            if (accepted.size() > 1) {
              // multiple accepted taxa. We will take the first, but log an issue!
              LOG.warn("{} accepted taxa for synonym {} with a child {}. Relink child to first accepted only!", accepted.size(), synonymName, NeoProperties.getScientificNameWithAuthor(child));
            }
            store.assignParent(accepted.iterator().next(), child);
            parentOfRelRelinked++;
            addRemark(child, "Parent relation taken from synonym " + synonymName);
          }
        }
        // remove parent rel for basionymGroup
        for (Relationship pRel : syn.getRelationships(RelType.PARENT_OF, Direction.INCOMING)) {
          // before we delete the relation make sure the accepted nodes have a parent rel or is ROOT
          for (Node acc : accepted) {
            if (acc.hasRelationship(RelType.PARENT_OF, Direction.INCOMING)) {
              // just delete
              childOfRelDeleted++;

            } else {
              Node parent = pRel.getOtherNode(syn);
              // relink if parent is not the accepted and parent rank is higher than accepted or null
              if (!parent.equals(acc)) {
                Rank parentRank = NeoProperties.getRank(parent, Rank.UNRANKED);
                Rank acceptedRank = NeoProperties.getRank(acc, Rank.UNRANKED);
                if (parentRank == Rank.UNRANKED || parentRank.higherThan(acceptedRank)) {
                  String synName = NeoProperties.getScientificNameWithAuthor(syn);
                  LOG.debug("Relink parent rel of synonym {}", synName);
                  childOfRelRelinkedToAccepted++;
                  store.assignParent(parent, acc);
                  addRemark(acc, "Parent relation taken from synonym " + synName);
                }
              }
            }
          }
          pRel.delete();
        }
      }
      tx.success();
    }
    LOG.info("Synonym relations cleaned up. "
            + "{} childOf relations deleted, {} childOf rels relinked to accepted,"
            + "{} parentOf relations deleted, {} parentOf rels moved from synonym to accepted",
        childOfRelDeleted, childOfRelRelinkedToAccepted, parentOfRelDeleted, parentOfRelRelinked);
  }

  private NeoTaxon addRemark(Node node, String remark) {
    return addIssueRemark(node, remark, null);
  }

  private NeoTaxon addIssue(Node node, Issue issue) {
    return addIssueRemark(node, null, issue);
  }

  /**
   * Reads a name usage from the kvp store, adds issues and or remarks and persists it again.
   * Only use this method if you just have a node a no usage instance yet at hand.
   */
  private NeoTaxon addIssueRemark(Node n, @Nullable String remark, @Nullable Issue issue) {
    //TODO: consider to store issues & remarks in neo4j so we do not have to load/store the full taxon instance
    NeoTaxon t = store.get(n);
    if (issue != null) {
      t.addIssue(issue);
    }
    if (remark != null) {
      t.addRemark(remark);
    }
    store.put(t);
    return t;
  }

  private void insertData() throws NormalizationFailedException {
    // closing the batch inserter opens the normalizer db again for regular access via the store
    try {
      NeoInserter inserter;
      switch (format) {
        case DWCA:
          inserter = new DwcaInserter(store, sourceDir);
          break;
        case ACEF:
          inserter = new AcefNeoInserter(store, sourceDir);
          break;
        default:
          throw new NormalizationFailedException("Unsupported data format " + format);
      }
      meta = inserter.insertAll();

    } catch (NotUniqueRuntimeException e) {
      throw new NormalizationFailedException(e.getProperty() + " values not unique: " + e.getKey(), e);

    } catch (IOException e) {
      throw new NormalizationFailedException("IO error: " + e.getMessage(), e);
    }
  }
}