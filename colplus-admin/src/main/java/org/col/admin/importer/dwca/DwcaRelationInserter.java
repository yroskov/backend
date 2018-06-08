package org.col.admin.importer.dwca;

import java.util.Collections;
import java.util.List;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import org.col.admin.importer.InsertMetadata;
import org.col.admin.importer.NormalizationFailedException;
import org.col.admin.importer.neo.NeoDb;
import org.col.admin.importer.neo.model.*;
import org.col.api.model.Name;
import org.col.api.model.TermRecord;
import org.col.api.vocab.Issue;
import org.col.api.vocab.Origin;
import org.col.parser.NameParser;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.nameparser.api.Rank;
import org.neo4j.graphdb.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class DwcaRelationInserter implements NeoDb.NodeBatchProcessor {
  private static final Logger LOG = LoggerFactory.getLogger(DwcaRelationInserter.class);

  private static final List<Splitter> COMMON_SPLITTER = Lists.newArrayList();
  static {
    for (char del : "[|;, ]".toCharArray()) {
      COMMON_SPLITTER.add(Splitter.on(del).trimResults().omitEmptyStrings());
    }
  }

  private final NeoDb store;
  private final InsertMetadata meta;

  public DwcaRelationInserter(NeoDb store, InsertMetadata meta) {
    this.store = store;
    this.meta = meta;
  }

  @Override
  public void process(Node n) {
    try {
      NeoTaxon t = store.get(n);
      if (t.taxon.getVerbatimKey() != null) {
        TermRecord v = store.getVerbatim(t.taxon.getVerbatimKey());
        insertAcceptedRel(t, v);
        insertParentRel(t, v);
        insertBasionymRel(t, v);
      }

      store.put(t);

    } catch (Exception e) {
      LOG.error("error processing explicit relations for {} {}", n, NeoProperties.getScientificNameWithAuthor(n), e);
    }
  }

  /**
   * Creates synonym_of relationship based on the verbatim dwc:acceptedNameUsageID and dwc:acceptedNameUsage term values.
   * Assumes pro parte synonyms are dealt with before and the remaining accepted identifier refers to a single taxon only.
   *
   * @param t the full neotaxon to process
   */
  private void insertAcceptedRel(NeoTaxon t, TermRecord v) {
    List<RankedName> accepted = Collections.emptyList();
    if (meta.isAcceptedNameMapped()) {
      accepted = lookupByIdOrName(v, t, DwcTerm.acceptedNameUsageID, Issue.ACCEPTED_ID_INVALID, DwcTerm.acceptedNameUsage, Origin.VERBATIM_ACCEPTED);
      for (RankedName acc : accepted) {
        store.createSynonymRel(t.node, acc.node);
      }
    }

    // if status is synonym but we aint got no idea of the accepted flag it
    if (accepted.isEmpty() && (t.isSynonym() || v.hasIssue(Issue.ACCEPTED_ID_INVALID))) {
      t.addIssue(Issue.ACCEPTED_NAME_MISSING);
      // now remove any denormed classification from this synonym to avoid parent relations
      //t.classification = null;
      t.node.addLabel(Labels.SYNONYM);
      t.node.removeLabel(Labels.TAXON);
    }
  }

  /**
   * Sets up the parent relations using the parentNameUsage(ID) term values.
   * The denormed, flat classification is used in a next step later.
   */
  private void insertParentRel(NeoTaxon t, TermRecord v) {
    if (v != null && meta.isParentNameMapped()) {
      RankedName parent = lookupSingleByIdOrName(v, t, DwcTerm.parentNameUsageID, Issue.PARENT_ID_INVALID, DwcTerm.parentNameUsage, Origin.VERBATIM_PARENT);
      if (parent != null) {
        store.assignParent(parent.node, t.node);
      }
    }
  }

  private void insertBasionymRel(NeoTaxon t, TermRecord v) {
    if (v != null && meta.isOriginalNameMapped()) {
      RankedName bas = lookupSingleByIdOrName(v, t, DwcTerm.originalNameUsageID, Issue.BASIONYM_ID_INVALID, DwcTerm.originalNameUsage, Origin.VERBATIM_BASIONYM);
      if (bas != null) {
        bas.node.createRelationshipTo(t.node, RelType.BASIONYM_OF);
      }
    }
  }

  private RankedName lookupSingleByIdOrName(TermRecord v, NeoTaxon t, DwcTerm idTerm, Issue invlidIdIssue, DwcTerm nameTerm, Origin createdNameOrigin) {
    List<RankedName> names = lookupByIdOrName(v, t, false, idTerm, invlidIdIssue, nameTerm, createdNameOrigin);
    return names.isEmpty() ? null : names.get(0);
  }

  private List<RankedName> lookupByIdOrName(TermRecord v, NeoTaxon t, DwcTerm idTerm, Issue invlidIdIssue, DwcTerm nameTerm, Origin createdNameOrigin) {
    return lookupByIdOrName(v, t, true, idTerm, invlidIdIssue, nameTerm, createdNameOrigin);
  }

  private List<RankedName> lookupByIdOrName(TermRecord v, NeoTaxon t, boolean allowMultiple, DwcTerm idTerm, Issue invlidIdIssue, DwcTerm nameTerm, Origin createdNameOrigin) {
    List<RankedName> names = lookupByTaxonID(idTerm, v, t, invlidIdIssue, allowMultiple);
    if (names.isEmpty()) {
      // try to setup rel via the name
      RankedName n = lookupByName(nameTerm, v, t, createdNameOrigin);
      if (n != null) {
        names.add(n);
      }
    }
    return names;
  }

  /**
   * Reads a verbatim given term that should represent a foreign key to another record via the taxonID.
   * If the value is not the same as the original records taxonID it tries to split the ids into multiple keys and lookup the matching nodes.
   *
   * @return list of potentially split ids with their matching neo node if found, otherwise null
   */
  private List<RankedName> lookupByTaxonID(DwcTerm term, TermRecord v, NeoTaxon t, Issue invalidIdIssue, boolean allowMultiple) {
    List<RankedName> ids = Lists.newArrayList();
    final String unsplitIds = v.get(term);
    if (unsplitIds != null && !unsplitIds.equals(t.getID())) {
      if (allowMultiple && meta.getMultiValueDelimiters().containsKey(term)) {
        ids.addAll(lookupRankedNames(
            meta.getMultiValueDelimiters().get(term).splitToList(unsplitIds), t)
        );
      } else {
        // match by taxonID to see if this is an existing identifier or if we should try to split it
        Node a = store.byID(unsplitIds);
        if (a != null) {
          ids.add(NeoProperties.getRankedName(a));

        } else if (allowMultiple){
          for (Splitter splitter : COMMON_SPLITTER) {
            List<String> vals = splitter.splitToList(unsplitIds);
            if (vals.size() > 1) {
              ids.addAll(lookupRankedNames(vals, t));
              break;
            }
          }
        }
      }
      // could not find anything?
      if (ids.isEmpty()) {
        v.addIssue(invalidIdIssue);
        t.addIssue(invalidIdIssue);
        LOG.warn("{} {} not existing", term.simpleName(), unsplitIds);
      }
    }
    return ids;
  }


  private List<RankedName> lookupRankedNames(Iterable<String> taxonIDs, NeoTaxon t) {
    List<RankedName> rankedNames = Lists.newArrayList();
    for (String id : taxonIDs) {
      if (!id.equals(t.getID())) {
        Node n = store.byID(id);
        if (n != null) {
          rankedNames.add(NeoProperties.getRankedName(n));
        }
      }
    }
    return rankedNames;
  }

  /**
   * Reads a verbatim given term that should represent a scientific name pointing to another record via the scientificName.
   * It first tries to lookup existing records by the canonical name with author, but falls back to authorless lookup if no matches.
   * If the name is the same as the original records scientificName it is ignored.
   *
   * If true names that cannot be found are created as explicit names
   *
   * @return the accepted node with its name. Null if no accepted name was mapped or equals the record itself
   */
  private RankedName lookupByName(DwcTerm term, TermRecord v, NeoTaxon t, Origin createdOrigin) {
    if (v.hasTerm(term)) {
      final Name name = NameParser.PARSER.parse(v.get(term)).get().getName();
      // force unranked name for non binomials or unparsed names, avoiding wrong parser decisions
      if (!name.isParsed() || !name.isBinomial()) {
        name.setRank(Rank.UNRANKED);
      }
      if (!name.getScientificName().equalsIgnoreCase(t.name.getScientificName())) {
        List<Node> matches = store.byScientificName(name.getScientificName());
        // remove other authors, but allow names without authors
        if (name.hasAuthorship()) {
          matches.removeIf(n -> !Strings.isNullOrEmpty(NeoProperties.getAuthorship(n)) && !NeoProperties.getAuthorship(n).equalsIgnoreCase(name.authorshipComplete()));
        }

        // if multiple matches remove synonyms
        if (matches.size() > 1) {
          matches.removeIf(n -> n.hasLabel(Labels.SYNONYM));
        }

        // if we got one match, use it!
        if (matches.isEmpty()) {
          // create name
          LOG.debug("{} {} not existing, materialize it", term.simpleName(), name);
          return store.createDoubtfulFromSource(createdOrigin, name, t, t.name.getRank());

        } else {
          if (matches.size() > 1) {
            // still multiple matches, pick first and log critical issue!
            t.addIssue(Issue.NAME_NOT_UNIQUE);
          }
          return NeoProperties.getRankedName(matches.get(0));
        }
      }
    }
    return null;
  }

  @Override
  public void commitBatch(int counter) {
    if (Thread.interrupted()) {
      LOG.warn("Normalizer interrupted, exit dataset {} early with incomplete parsing", store.getDataset().getKey());
      throw new NormalizationFailedException("Normalizer interrupted");
    }
    LOG.debug("Processed relations for {} nodes", counter);
  }
}