package org.col.es;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.DeflaterOutputStream;

import org.col.api.model.Name;
import org.col.api.model.SimpleName;
import org.col.api.model.VernacularName;
import org.col.api.search.NameUsageWrapper;
import org.col.api.vocab.NameField;
import org.col.common.tax.SciNameNormalizer;
import org.col.es.model.EsNameUsage;
import org.col.es.model.Monomial;

import static org.col.api.vocab.NameField.BASIONYM_AUTHORS;
import static org.col.api.vocab.NameField.BASIONYM_EX_AUTHORS;
import static org.col.api.vocab.NameField.BASIONYM_YEAR;
import static org.col.api.vocab.NameField.CANDIDATUS;
import static org.col.api.vocab.NameField.COMBINATION_AUTHORS;
import static org.col.api.vocab.NameField.COMBINATION_EX_AUTHORS;
import static org.col.api.vocab.NameField.COMBINATION_YEAR;
import static org.col.api.vocab.NameField.CULTIVAR_EPITHET;
import static org.col.api.vocab.NameField.GENUS;
import static org.col.api.vocab.NameField.INFRAGENERIC_EPITHET;
import static org.col.api.vocab.NameField.INFRASPECIFIC_EPITHET;
import static org.col.api.vocab.NameField.NOM_STATUS;
import static org.col.api.vocab.NameField.NOTHO;
import static org.col.api.vocab.NameField.PUBLISHED_IN_ID;
import static org.col.api.vocab.NameField.PUBLISHED_IN_PAGE;
import static org.col.api.vocab.NameField.REMARKS;
import static org.col.api.vocab.NameField.SANCTIONING_AUTHOR;
import static org.col.api.vocab.NameField.SOURCE_URL;
import static org.col.api.vocab.NameField.SPECIFIC_EPITHET;
import static org.col.api.vocab.NameField.STRAIN;
import static org.col.api.vocab.NameField.UNINOMIAL;
import static org.col.common.util.CollectionUtils.notEmpty;

/**
 * Converts NameUsageWrapper instances to EsNameUsage documents. Only a few fields are indexed. The remainder of the NameUsageWrapper is
 * converted to JSON and the resulting JSON string is stored in a payload field.
 */
public class NameUsageTransfer {

  /**
   * Whether or not to zip the stringified NameUsageWrapper.
   */
  public static final boolean ZIP_PAYLOAD = true;

  /**
   * Provides a weakly normalized version of the original scientific name. Whatever normalization method we choose, we must make sure it is
   * used both at index time (here) and at query time (QTranslator). Hence this public static method.
   */
  public static String normalizeWeakly(String sn) {
    return SciNameNormalizer.normalize(sn);
  }

  /**
   * Provides a strongly normalized version of the original scientific name.
   */
  public static String normalizeStrongly(String sn) {
    return SciNameNormalizer.normalizeAll(sn);
  }

  /**
   * Extracts the classification from the Elasticsearch document.
   * 
   * @param enu
   * @return
   */
  public static List<SimpleName> extractClassifiction(EsNameUsage enu) {
    if (enu.getHigherNameIds() == null) {
      return null;
    }
    List<String> ids = enu.getHigherNameIds();
    List<Monomial> names = enu.getHigherNames();
    List<SimpleName> classification = new ArrayList<>(ids.size());
    for (int i = 0; i < enu.getHigherNameIds().size(); i++) {
      SimpleName sn = new SimpleName(ids.get(i), names.get(i).getName(), names.get(i).getRank());
      classification.add(sn);
    }
    return classification;
  }

  /**
   * Nullifies fields in the NameUsageWrapper object that are already indexed separately so as to make the payload (and the entire document)
   * as small as possible and to cut down as much as possible on JSON processing. It's not necessary (and we don't) prune away everything
   * that can be pruned away, as long as this method mirrors enrichPayload().
   * 
   * @param nuw
   */
  static void prunePayload(NameUsageWrapper nuw) {
    nuw.getUsage().setId(null);
    nuw.getUsage().getName().setDatasetKey(null);
    nuw.getUsage().getName().setId(null);
    nuw.getUsage().getName().setIndexNameId(null);
    nuw.getUsage().getName().setNomStatus(null);
    nuw.getUsage().getName().setPublishedInId(null);
    nuw.getUsage().getName().setType(null);
    nuw.setIssues(null);
    nuw.setClassification(null);
  }

  /**
   * Puts the nullified fields back onto the NameUsageWrapper object.
   * 
   * @param nuw
   * @param enu
   */
  static void enrichPayload(NameUsageWrapper nuw, EsNameUsage enu) {
    nuw.getUsage().setId(enu.getUsageId());
    nuw.getUsage().getName().setDatasetKey(enu.getDatasetKey());
    nuw.getUsage().getName().setId(enu.getNameId());
    nuw.getUsage().getName().setIndexNameId(enu.getIndexNameId());
    nuw.getUsage().getName().setNomStatus(enu.getNomStatus());
    nuw.getUsage().getName().setPublishedInId(enu.getPublishedInId());
    nuw.getUsage().getName().setType(enu.getType());
    nuw.setIssues(enu.getIssues());
    loadClassification(enu, nuw);
  }

  EsNameUsage toDocument(NameUsageWrapper nuw) throws IOException {
    EsNameUsage enu = new EsNameUsage();
    if (notEmpty(nuw.getVernacularNames())) {
      List<String> names = nuw.getVernacularNames()
          .stream()
          .map(VernacularName::getName)
          .collect(Collectors.toList());
      enu.setVernacularNames(names);
    }
    saveClassification(nuw, enu);
    enu.setIssues(nuw.getIssues());
    Name name = nuw.getUsage().getName();
    enu.setAuthorship(name.authorshipComplete());
    enu.setDatasetKey(name.getDatasetKey());
    enu.setNameId(name.getId());
    enu.setIndexNameId(name.getIndexNameId());
    enu.setNomStatus(name.getNomStatus());
    enu.setPublishedInId(name.getPublishedInId());
    enu.setRank(name.getRank());
    String w = normalizeWeakly(name.getScientificName());
    String s = normalizeStrongly(name.getScientificName());
    enu.setScientificNameWN(w);
    /*
     * Don't waste time indexing the same ngram tokens twice for every document. Only index the strongly normalized variant if it differs
     * from the weakly normalized variant. This if-logic is replicated at query time (see QTranslator).
     */
    if (!w.equals(s)) {
      enu.setScientificNameSN(s);
    }
    enu.setStatus(nuw.getUsage().getStatus());
    enu.setUsageId(nuw.getUsage().getId());
    enu.setType(name.getType());
    enu.setNameFields(getNonNullNameFields(name));
    prunePayload(nuw);
    if (ZIP_PAYLOAD) {
      ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
      DeflaterOutputStream dos = new DeflaterOutputStream(baos);
      EsModule.NAME_USAGE_WRITER.writeValue(dos, nuw);
      dos.close();
      enu.setPayload(Base64.getEncoder().encodeToString(baos.toByteArray()));
    } else {
      enu.setPayload(EsModule.NAME_USAGE_WRITER.writeValueAsString(nuw));
    }
    return enu;
  }

  private static Set<NameField> getNonNullNameFields(Name name) {
    Set<NameField> fields = EnumSet.noneOf(NameField.class);
    if (name.getBasionymAuthorship() != null && notEmpty(name.getBasionymAuthorship().getAuthors())) {
      fields.add(BASIONYM_AUTHORS);
    }
    if (name.getBasionymAuthorship() != null && notEmpty(name.getBasionymAuthorship().getExAuthors())) {
      fields.add(BASIONYM_EX_AUTHORS);
    }
    if (name.getBasionymAuthorship() != null && name.getBasionymAuthorship().getYear() != null) {
      fields.add(BASIONYM_YEAR);
    }
    if (name.isCandidatus()) {
      fields.add(CANDIDATUS);
    }
    if (name.getCombinationAuthorship() != null && notEmpty(name.getCombinationAuthorship().getAuthors())) {
      fields.add(COMBINATION_AUTHORS);
    }
    if (name.getCombinationAuthorship() != null && notEmpty(name.getCombinationAuthorship().getExAuthors())) {
      fields.add(COMBINATION_EX_AUTHORS);
    }
    if (name.getCombinationAuthorship() != null && name.getCombinationAuthorship().getYear() != null) {
      fields.add(COMBINATION_YEAR);
    }
    addIfSet(fields, CULTIVAR_EPITHET, name.getCultivarEpithet());
    addIfSet(fields, GENUS, name.getGenus());
    addIfSet(fields, INFRAGENERIC_EPITHET, name.getInfragenericEpithet());
    addIfSet(fields, INFRASPECIFIC_EPITHET, name.getInfraspecificEpithet());
    addIfSet(fields, NOM_STATUS, name.getNomStatus());
    addIfSet(fields, NOTHO, name.getNotho());
    addIfSet(fields, PUBLISHED_IN_ID, name.getPublishedInId());
    addIfSet(fields, PUBLISHED_IN_PAGE, name.getPublishedInPage());
    addIfSet(fields, REMARKS, name.getRemarks());
    addIfSet(fields, SANCTIONING_AUTHOR, name.getSanctioningAuthor());
    addIfSet(fields, SOURCE_URL, name.getSourceUrl());
    addIfSet(fields, SPECIFIC_EPITHET, name.getSpecificEpithet());
    addIfSet(fields, STRAIN, name.getStrain());
    addIfSet(fields, UNINOMIAL, name.getUninomial());
    return fields;
  }

  private static void addIfSet(Set<NameField> fields, NameField nf, Object val) {
    if (val != null) {
      fields.add(nf);
    }
  }

  private static void loadClassification(EsNameUsage from, NameUsageWrapper into) {
    into.setClassification(extractClassifiction(from));
  }

  private static void saveClassification(NameUsageWrapper from, EsNameUsage to) {
    if (notEmpty(from.getClassification())) {
      // NB last element is the taxon itsef; couldn't figure out the SQL to exclude it
      List<String> higherTaxonIds = new ArrayList<>(from.getClassification().size() - 1);
      List<Monomial> monomials = new ArrayList<>(from.getClassification().size() - 1);
      for (int i = 0; i < from.getClassification().size() - 1; i++) {
        SimpleName sn = from.getClassification().get(i);
        higherTaxonIds.add(sn.getId());
        monomials.add(new Monomial(sn.getRank(), sn.getName()));
      }
      to.setHigherNames(monomials);
      to.setHigherNameIds(higherTaxonIds);
    }
  }

}
