package life.catalogue.es.nu.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import life.catalogue.api.model.Page;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.api.search.NameUsageSearchRequest.SortBy;
import life.catalogue.es.EsNameUsage;
import life.catalogue.es.EsReadTestBase;
import life.catalogue.es.nu.search.RequestTranslator;
import life.catalogue.es.query.EsSearchRequest;
import org.gbif.nameparser.api.Rank;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SortingTest extends EsReadTestBase {

  @Before
  public void before() {
    destroyAndCreateIndex();
  }

  @Test
  public void testSortByName_01() {
    EsNameUsage docA = new EsNameUsage();
    docA.setScientificName("A");
    EsNameUsage docB = new EsNameUsage();
    docB.setScientificName("B");
    EsNameUsage docC = new EsNameUsage();
    docC.setScientificName("C");
    EsNameUsage docD = new EsNameUsage();
    docD.setScientificName("D");
    EsNameUsage docE = new EsNameUsage();
    docE.setScientificName("E");

    indexRaw(docB, docA, docD, docE, docC);

    List<EsNameUsage> expected = Arrays.asList(docA, docB, docC, docD, docE);

    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.setSortBy(SortBy.NAME);
    EsSearchRequest esQuery = new RequestTranslator(query, new Page()).translateRequest();
    List<EsNameUsage> result = queryRaw(esQuery);
    assertEquals(expected, result);
  }

  @Test
  public void testSortByNameDescending_01() {
    EsNameUsage docA = new EsNameUsage();
    docA.setScientificName("A");
    EsNameUsage docB = new EsNameUsage();
    docB.setScientificName("B");
    EsNameUsage docC = new EsNameUsage();
    docC.setScientificName("C");
    EsNameUsage docD = new EsNameUsage();
    docD.setScientificName("D");
    EsNameUsage docE = new EsNameUsage();
    docE.setScientificName("E");

    indexRaw(docB, docA, docD, docE, docC);

    List<EsNameUsage> expected = Arrays.asList(docE, docD, docC, docB, docA);

    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.setSortBy(SortBy.NAME);
    query.setReverse(true);
    EsSearchRequest esQuery = new RequestTranslator(query, new Page()).translateRequest();
    List<EsNameUsage> result = queryRaw(esQuery);
    assertEquals(expected, result);
  }

  @Test
  public void testSortNative_01() {
    EsNameUsage docA = new EsNameUsage();
    docA.setScientificName("A");
    EsNameUsage docB = new EsNameUsage();
    docB.setScientificName("B");
    EsNameUsage docC = new EsNameUsage();
    docC.setScientificName("C");
    EsNameUsage docD = new EsNameUsage();
    docD.setScientificName("D");
    EsNameUsage docE = new EsNameUsage();
    docE.setScientificName("E");

    List<EsNameUsage> docs = Arrays.asList(docB, docA, docD, docE, docC);

    indexRaw(docs);

    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.setSortBy(null);
    EsSearchRequest esQuery = new RequestTranslator(query, new Page()).translateRequest();
    List<EsNameUsage> result = queryRaw(esQuery);
    assertEquals(docs, result);
  }

  @Test
  public void testSortNative_02() {
    EsNameUsage docA = new EsNameUsage();
    docA.setScientificName("A");
    EsNameUsage docB = new EsNameUsage();
    docB.setScientificName("B");
    EsNameUsage docC = new EsNameUsage();
    docC.setScientificName("C");
    EsNameUsage docD = new EsNameUsage();
    docD.setScientificName("D");
    EsNameUsage docE = new EsNameUsage();
    docE.setScientificName("E");

    List<EsNameUsage> docs = Arrays.asList(docB, docA, docD, docE, docC);

    indexRaw(docs);

    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.setSortBy(SortBy.NATIVE);
    EsSearchRequest esQuery = new RequestTranslator(query, new Page()).translateRequest();
    List<EsNameUsage> result = queryRaw(esQuery);
    assertEquals(docs, result);
  }

  @Test
  public void testSortTaxonomic_01() {

    EsNameUsage kingdomF = new EsNameUsage();
    kingdomF.setRank(Rank.KINGDOM);
    kingdomF.setScientificName("F");

    EsNameUsage kingdomP = new EsNameUsage();
    kingdomP.setRank(Rank.KINGDOM);
    kingdomP.setScientificName("P");

    EsNameUsage phylumC = new EsNameUsage();
    phylumC.setRank(Rank.PHYLUM);
    phylumC.setScientificName("C");

    EsNameUsage phylumD = new EsNameUsage();
    phylumD.setRank(Rank.PHYLUM);
    phylumD.setScientificName("D");

    EsNameUsage classZ = new EsNameUsage();
    classZ.setRank(Rank.CLASS);
    classZ.setScientificName("Z");

    EsNameUsage orderA = new EsNameUsage();
    orderA.setRank(Rank.ORDER);
    orderA.setScientificName("A");

    EsNameUsage familyT = new EsNameUsage();
    familyT.setRank(Rank.FAMILY);
    familyT.setScientificName("F");

    EsNameUsage genusA = new EsNameUsage();
    genusA.setRank(Rank.GENUS);
    genusA.setScientificName("A");

    EsNameUsage genusC = new EsNameUsage();
    genusC.setRank(Rank.GENUS);
    genusC.setScientificName("C");

    EsNameUsage speciesZ = new EsNameUsage();
    speciesZ.setRank(Rank.SPECIES);
    speciesZ.setScientificName("Z");

    List<EsNameUsage> expected = Arrays.asList(
        kingdomF,
        kingdomP,
        phylumC,
        phylumD,
        classZ,
        orderA,
        familyT,
        genusA,
        genusC,
        speciesZ);

    List<EsNameUsage> docs = new ArrayList<EsNameUsage>(expected);
    Collections.shuffle(docs);
    indexRaw(docs);

    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.setSortBy(SortBy.TAXONOMIC);
    EsSearchRequest esQuery = new RequestTranslator(query, new Page()).translateRequest();

    List<EsNameUsage> result = queryRaw(esQuery);
    assertEquals(expected, result);

    // Let's just do this one more time.
    truncate();
    docs = new ArrayList<EsNameUsage>(expected);
    Collections.shuffle(docs);
    indexRaw(docs);

    result = queryRaw(esQuery);
    assertEquals(expected, result);

  }

  @Test
  public void testSortTaxonomicDescending_01() {

    EsNameUsage kingdomF = new EsNameUsage();
    kingdomF.setRank(Rank.KINGDOM);
    kingdomF.setScientificName("F");

    EsNameUsage kingdomP = new EsNameUsage();
    kingdomP.setRank(Rank.KINGDOM);
    kingdomP.setScientificName("P");

    EsNameUsage phylumC = new EsNameUsage();
    phylumC.setRank(Rank.PHYLUM);
    phylumC.setScientificName("C");

    EsNameUsage phylumD = new EsNameUsage();
    phylumD.setRank(Rank.PHYLUM);
    phylumD.setScientificName("D");

    EsNameUsage classZ = new EsNameUsage();
    classZ.setRank(Rank.CLASS);
    classZ.setScientificName("Z");

    EsNameUsage orderA = new EsNameUsage();
    orderA.setRank(Rank.ORDER);
    orderA.setScientificName("A");

    EsNameUsage familyT = new EsNameUsage();
    familyT.setRank(Rank.FAMILY);
    familyT.setScientificName("F");

    EsNameUsage genusA = new EsNameUsage();
    genusA.setRank(Rank.GENUS);
    genusA.setScientificName("A");

    EsNameUsage genusC = new EsNameUsage();
    genusC.setRank(Rank.GENUS);
    genusC.setScientificName("C");

    EsNameUsage speciesZ = new EsNameUsage();
    speciesZ.setRank(Rank.SPECIES);
    speciesZ.setScientificName("Z");

    // Rank reversed, but name still alphabetical
    List<EsNameUsage> expected = Arrays.asList(
        speciesZ,
        genusA,
        genusC,
        familyT,
        orderA,
        classZ,
        phylumC,
        phylumD,
        kingdomF,
        kingdomP);

    List<EsNameUsage> docs = new ArrayList<EsNameUsage>(expected);
    Collections.shuffle(docs);
    indexRaw(docs);

    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.setSortBy(SortBy.TAXONOMIC);
    query.setReverse(true);
    EsSearchRequest esQuery = new RequestTranslator(query, new Page()).translateRequest();

    List<EsNameUsage> result = queryRaw(esQuery);
    assertEquals(expected, result);

    // Let's just do this one more time.
    truncate();
    docs = new ArrayList<EsNameUsage>(expected);
    Collections.shuffle(docs);
    indexRaw(docs);

    result = queryRaw(esQuery);
    assertEquals(expected, result);

  }

  public void testSortIndexingOrderReversed() {
    EsNameUsage a = new EsNameUsage();
    EsNameUsage b = new EsNameUsage();
    EsNameUsage c = new EsNameUsage();
    EsNameUsage d = new EsNameUsage();
    EsNameUsage e = new EsNameUsage();
    List<EsNameUsage> docs = Arrays.asList(a, b, c, d, e);
    indexRaw(docs);

    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.setSortBy(SortBy.NATIVE);
    query.setReverse(true);
    EsSearchRequest esQuery = new RequestTranslator(query, new Page()).translateRequest();

    List<EsNameUsage> result = queryRaw(esQuery);

    Collections.reverse(docs);
    assertEquals(docs, result);

    // Let's just do this one more time.
    truncate();   
    Collections.shuffle(docs);
    indexRaw(docs);
    queryRaw(esQuery);
    Collections.reverse(docs);
    assertEquals(docs, result);
    
  }


  @Test
  public void testSortByIndexNameId_01() {
    EsNameUsage docA = new EsNameUsage();
    docA.setNameIndexId("A");
    EsNameUsage docB = new EsNameUsage();
    docB.setNameIndexId("B");
    EsNameUsage docC = new EsNameUsage();
    docC.setNameIndexId("C");
    EsNameUsage docD = new EsNameUsage();
    docD.setNameIndexId("D");
    EsNameUsage docE = new EsNameUsage();
    docE.setNameIndexId("E");

    indexRaw(docB, docA, docD, docE, docC);

    List<EsNameUsage> expected = Arrays.asList(docA, docB, docC, docD, docE);

    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.setSortBy(SortBy.INDEX_NAME_ID);
    EsSearchRequest esQuery = new RequestTranslator(query, new Page()).translateRequest();
    List<EsNameUsage> result = queryRaw(esQuery);
    assertEquals(expected, result);
  }


  @Test
  public void testSortByIndexNameIdDescending_01() {
    EsNameUsage docA = new EsNameUsage();
    docA.setNameIndexId("A");
    EsNameUsage docB = new EsNameUsage();
    docB.setNameIndexId("B");
    EsNameUsage docC = new EsNameUsage();
    docC.setNameIndexId("C");
    EsNameUsage docD = new EsNameUsage();
    docD.setNameIndexId("D");
    EsNameUsage docE = new EsNameUsage();
    docE.setNameIndexId("E");

    indexRaw(docB, docA, docD, docE, docC);

    List<EsNameUsage> expected = Arrays.asList(docE, docD, docC, docB, docA);

    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.setSortBy(SortBy.INDEX_NAME_ID);
    query.setReverse(true);
    EsSearchRequest esQuery = new RequestTranslator(query, new Page()).translateRequest();
    List<EsNameUsage> result = queryRaw(esQuery);
    assertEquals(expected, result);
  }


}
