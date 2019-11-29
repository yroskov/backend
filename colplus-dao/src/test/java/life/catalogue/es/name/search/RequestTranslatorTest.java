package life.catalogue.es.name.search;

import com.fasterxml.jackson.core.JsonProcessingException;

import life.catalogue.api.model.Page;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.es.EsModule;
import org.gbif.nameparser.api.Rank;
import org.junit.Test;

import static life.catalogue.api.search.NameUsageSearchParameter.DATASET_KEY;
import static life.catalogue.api.search.NameUsageSearchParameter.ISSUE;
import static life.catalogue.api.search.NameUsageSearchParameter.RANK;
import static life.catalogue.api.search.NameUsageSearchParameter.STATUS;

// No real tests here, but generates queries that can be tried out in Kibana.
public class RequestTranslatorTest {

  /*
   * Case: 4 facets, two filters, both corresponding to a facet.
   */
  @Test
  public void test1() throws JsonProcessingException {

    NameUsageSearchRequest nsr = new NameUsageSearchRequest();

    nsr.addFacet(ISSUE);
    nsr.addFacet(DATASET_KEY);
    nsr.addFacet(RANK);
    nsr.addFacet(STATUS);

    nsr.addFilter(DATASET_KEY, 1000);
    nsr.addFilter(RANK, Rank.GENUS);

    RequestTranslator t = new RequestTranslator(nsr, new Page());

    System.out.println(EsModule.write(t.translate()));

  }

  /*
   * Case: 4 facets, three filters, one corresponding to a facet, two non-facet filters.
   */
  @Test
  public void test2() throws JsonProcessingException {

    NameUsageSearchRequest nsr = new NameUsageSearchRequest();

    nsr.addFacet(ISSUE);
    nsr.addFacet(DATASET_KEY);
    nsr.addFacet(RANK);
    nsr.addFacet(STATUS);

    nsr.addFilter(DATASET_KEY, 1000);
    // nsr.addFilter(PUBLISHED_IN_ID, "ABCD");
    nsr.setQ("Car");

    RequestTranslator t = new RequestTranslator(nsr, new Page());

    System.out.println(EsModule.write(t.translate()));

  }

  /*
   * Case: 3 facets, two non-facet filters.
   */
  @Test
  public void test3() throws JsonProcessingException {

    NameUsageSearchRequest nsr = new NameUsageSearchRequest();

    nsr.addFacet(ISSUE);
    nsr.addFacet(DATASET_KEY);
    nsr.addFacet(RANK);

    nsr.addFilter(STATUS, TaxonomicStatus.ACCEPTED);
    nsr.setQ("c");

    RequestTranslator t = new RequestTranslator(nsr, new Page());

    System.out.println(EsModule.write(t.translate()));

  }

  /*
   * Case: 4 facets, no filters.
   */
  @Test
  public void test4() throws JsonProcessingException {

    NameUsageSearchRequest nsr = new NameUsageSearchRequest();

    nsr.addFacet(ISSUE);
    nsr.addFacet(DATASET_KEY);
    nsr.addFacet(RANK);
    nsr.addFacet(STATUS);

    RequestTranslator t = new RequestTranslator(nsr, new Page());

    System.out.println(EsModule.write(t.translate()));

  }

  /*
   * Case: 1 facet, two non-facet filters
   */
  @Test
  public void test5() throws JsonProcessingException {

    NameUsageSearchRequest nsr = new NameUsageSearchRequest();

    nsr.addFacet(RANK);

    nsr.addFilter(DATASET_KEY, 1000);
    nsr.setQ("Car");

    RequestTranslator t = new RequestTranslator(nsr, new Page());

    System.out.println(EsModule.write(t.translate()));

  }

}