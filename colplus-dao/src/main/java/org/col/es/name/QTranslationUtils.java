package org.col.es.name;

import org.col.es.dsl.AutoCompleteQuery;
import org.col.es.dsl.BoolQuery;
import org.col.es.dsl.PrefixQuery;
import org.col.es.dsl.Query;
import org.col.es.dsl.TermQuery;
import org.col.es.model.NameStrings;

import static org.col.es.model.NameStrings.tokenize;

public class QTranslationUtils {

  public static final float BASE_BOOST = 100;

  private static final int MAX_NGRAM_SIZE = 10; // see es-settings.json

  private QTranslationUtils() {}

  public static Query getVernacularNameQuery(String q) {
    return new AutoCompleteQuery("vernacularNames", q).withBoost(BASE_BOOST);
  }

  public static Query getAuthorshipQuery(String q) {
    return new AutoCompleteQuery("authorship", q).withBoost(BASE_BOOST);
  }

  public static Query getScientificNameQuery(String q, NameStrings strings) {
    switch (tokenize(q).length) {
      case 1:
        /*
         * Compare the search phrase with genus, specific epither and infraspecific epithet. We slightly bump matches on
         * specific and infraspecific epithets. We also slight bump the query as a whole because all else being equals we'd
         * rather suggest scientific names than vernacular names or authors.
         */
        return new BoolQuery()
            .should(getGenusQuery(strings))
            .should(getSpecificEpithetQuery(strings).withBoost(BASE_BOOST * 1.01F))
            .should(getInfraspecificEpithetQuery(strings).withBoost(BASE_BOOST * 1.02F))
            .withBoost(BASE_BOOST * 1.01F);
      case 2:
        /*
         * match 1st term against genus and 2nd against either specific or infraspecific epithet. Slight bump matches on
         * infraspecific epithets.
         */
        return new BoolQuery()
            .must(getGenusQuery(strings))
            .must(new BoolQuery()
                .should(getSpecificEpithetQuery(strings))
                .should(getInfraspecificEpithetQuery(strings).withBoost(BASE_BOOST * 1.01F))
                .minimumShouldMatch(1))
            .withBoost(BASE_BOOST * 1.2F);
      case 3:
        return new BoolQuery()
            .must(getGenusQuery(strings))
            .must(getSpecificEpithetQuery(strings))
            .must(getInfraspecificEpithetQuery(strings))
            .withBoost(BASE_BOOST * 1.5F); // that's almost guaranteed to be bingo
      default:
        return new AutoCompleteQuery("nameStrings.scientificNameWN", strings.getScientificNameWN()).withBoost(BASE_BOOST * 1.01F);
    }
  }

  private static Query getGenusQuery(NameStrings strings) {
    if (strings.getGenus().length() == 1) {
      return new TermQuery("nameStrings.genusLetter", strings.getGenusLetter());
    }
    if (strings.getGenusWN() == null) { // normalized version does not differ from the original string
      return compare("nameStrings.genus", strings.getGenus());
    }
    return compare("nameStrings.genusWN", strings.getGenusWN());
  }

  private static Query getSpecificEpithetQuery(NameStrings strings) {
    if (strings.getSpecificEpithetSN() == null) {
      return compare("nameStrings.specificEpithet", strings.getSpecificEpithet());
    }
    return compare("nameStrings.specificEpithetSN", strings.getSpecificEpithetSN());
  }

  private static Query getInfraspecificEpithetQuery(NameStrings strings) {
    if (strings.getInfraspecificEpithetSN() == null) {
      return compare("nameStrings.infraspecificEpithet", strings.getInfraspecificEpithet());
    }
    return compare("nameStrings.infraspecificEpithetSN", strings.getInfraspecificEpithetSN());
  }

  // Only use for single-word fields!
  private static Query compare(String field, String value) {
    if (value.length() > MAX_NGRAM_SIZE) {
      return new PrefixQuery(field, value);
    }
    return new AutoCompleteQuery(field, value);
  }

}
