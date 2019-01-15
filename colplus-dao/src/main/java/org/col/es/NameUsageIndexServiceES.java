package org.col.es;

import java.io.IOException;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.search.NameUsageWrapper;
import org.col.db.mapper.BatchResultHandler;
import org.col.db.mapper.NameUsageMapper;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.col.es.EsConfig.ES_INDEX_NAME_USAGE;

public class NameUsageIndexServiceES implements NameUsageIndexService {

  private static final Logger LOG = LoggerFactory.getLogger(NameUsageIndexServiceES.class);

  private final RestClient client;
  private final EsConfig esConfig;
  private final String index;
  private final SqlSessionFactory factory;

  public NameUsageIndexServiceES(RestClient client, EsConfig esConfig, SqlSessionFactory factory) {
    this.client = client;
    this.index = esConfig.indexName(ES_INDEX_NAME_USAGE);
    this.esConfig = esConfig;
    this.factory = factory;
  }

  /**
   * Main method to index an entire dataset from postgres into ElasticSearch using the bulk API.
   */
  @Override
  public void indexDataset(int datasetKey) {
    NameUsageIndexer indexer = new NameUsageIndexer(client, index);
    int tCount, sCount, bCount;
    try (SqlSession session = factory.openSession()) {
      createOrEmptyIndex(index, datasetKey);
      NameUsageMapper mapper = session.getMapper(NameUsageMapper.class);
      try (BatchResultHandler<NameUsageWrapper> handler = new BatchResultHandler<>(indexer, 4096)) {
        LOG.debug("Indexing taxa for dataset {}", datasetKey);
        mapper.processDatasetTaxa(datasetKey, handler);
      }
      tCount = indexer.documentsIndexed();
      EsUtil.refreshIndex(client, index);
      try (SynonymResultHandler handler = new SynonymResultHandler(indexer, datasetKey)) {
        LOG.debug("Indexing synonyms for dataset {}", datasetKey);
        mapper.processDatasetSynonyms(datasetKey, handler);
      }
      sCount = indexer.documentsIndexed() - tCount;
      try (BatchResultHandler<NameUsageWrapper> handler = new BatchResultHandler<>(indexer, 4096)) {
        LOG.debug("Indexing bare names for dataset {}", datasetKey);
        mapper.processDatasetBareNames(datasetKey, handler);
      }
      bCount = indexer.documentsIndexed() - tCount - sCount;
      EsUtil.refreshIndex(client, index);
    } catch (IOException e) {
      throw new EsException(e);
    }
    LOG.info("Successfully indexed {} taxa, {} synonyms and {} bare names (total: {}; dataset: {}; index: {})",
        tCount,
        sCount,
        bCount,
        indexer.documentsIndexed(),
        datasetKey,
        index);
  }

  private void createOrEmptyIndex(String index, int datasetKey) throws IOException {
    if (EsUtil.indexExists(client, index)) {
      EsUtil.deleteDataset(client, index, datasetKey);
      EsUtil.refreshIndex(client, index);
    } else {
      EsUtil.createIndex(client, index, esConfig.nameUsage);
    }
  }

}