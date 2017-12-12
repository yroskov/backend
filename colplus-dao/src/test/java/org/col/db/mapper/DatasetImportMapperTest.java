package org.col.db.mapper;

import com.google.common.collect.Maps;
import org.col.api.DatasetImport;
import org.col.api.vocab.*;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;

import static org.col.TestEntityGenerator.DATASET1;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;


/**
 *
 */
public class DatasetImportMapperTest extends MapperTestBase<DatasetImportMapper> {
  private static Random rnd = new Random();

  public DatasetImportMapperTest() {
    super(DatasetImportMapper.class);
  }

  private static DatasetImport create() throws Exception {
    DatasetImport d = new DatasetImport();
    d.setDatasetKey(DATASET1.getKey());
    d.setError("no error");
    d.setSuccess(true);
    d.setStarted(LocalDateTime.now());
    d.setFinished(LocalDateTime.now());
    d.setDownload(LocalDateTime.now());
    d.setVerbatimCount(5748923);
    d.setNameCount(65432);
    d.setTaxonCount(5748329);
    d.setDistributionCount(12345);
    d.setVernacularCount(432);
    d.setIssuesCount(mockCount(Issue.class));
    d.setNamesByOriginCount(mockCount(Origin.class));
    d.setNamesByRankCount(mockCount(Rank.class));
    d.setNamesByTypeCount(mockCount(NameType.class));
    d.setVernacularsByLanguageCount(mockCount(Language.class));
    d.setDistributionsByGazetteerCount(mockCount(Gazetteer.class));
    return d;
  }

  private static <T extends Enum> Map<T, Integer> mockCount(Class<T> clazz) {
    Map<T, Integer> cnt = Maps.newHashMap(); 
    for (T val : clazz.getEnumConstants()) {
      cnt.put(val, rnd.nextInt());
    }
    return cnt;
  }

  @Test
  public void roundtrip() throws Exception {
    DatasetImport d1 = create();
    mapper().create(d1);

    commit();

    DatasetImport d2 = mapper().last(d1.getDatasetKey());
    assertNotNull(d2.getAttempt());
    d1.setAttempt(d2.getAttempt());
    assertEquals(d1, d2);
  }

  @Test
  public void generate() throws Exception {
    DatasetImport d = mapper().metrics(DATASET1.getKey());
    assertEquals((Integer) 2, d.getNameCount());
    assertEquals((Integer) 2, d.getTaxonCount());
    assertEquals((Integer) 0, d.getVerbatimCount());
    assertEquals((Integer) 3, d.getVernacularCount());
    assertEquals((Integer) 3, d.getDistributionCount());

    assertEquals( 1, d.getNamesByRankCount().size());
    assertEquals((Integer) 2, d.getNamesByRankCount().get(Rank.SPECIES));

    assertEquals( 1, d.getNamesByOriginCount().size());
    assertEquals((Integer) 2, d.getNamesByOriginCount().get(Origin.SOURCE));

    assertEquals( 1, d.getNamesByTypeCount().size());
    assertEquals((Integer) 2, d.getNamesByTypeCount().get(NameType.SCIENTIFIC));

    assertEquals( 1, d.getDistributionsByGazetteerCount().size());
    assertEquals((Integer) 3, d.getDistributionsByGazetteerCount().get(Gazetteer.TEXT));

    assertEquals( 3, d.getVernacularsByLanguageCount().size());
    assertEquals((Integer) 1, d.getVernacularsByLanguageCount().get(Language.GERMAN));
    assertEquals((Integer) 1, d.getVernacularsByLanguageCount().get(Language.ENGLISH));
    assertEquals((Integer) 1, d.getVernacularsByLanguageCount().get(Language.DUTCH));
  }

}