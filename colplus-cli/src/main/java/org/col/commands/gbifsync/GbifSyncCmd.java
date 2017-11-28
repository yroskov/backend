package org.col.commands.gbifsync;

import com.zaxxer.hikari.HikariDataSource;
import io.dropwizard.cli.EnvironmentCommand;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.setup.Environment;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.Dataset;
import org.col.commands.CliApp;
import org.col.commands.config.CliConfig;
import org.col.db.MybatisFactory;
import org.col.db.mapper.DatasetMapper;
import org.glassfish.jersey.client.rx.RxClient;
import org.glassfish.jersey.client.rx.java8.RxCompletionStageInvoker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Basic task to showcase hello world
 */
public class GbifSyncCmd extends EnvironmentCommand<CliConfig> {
  private static final Logger LOG = LoggerFactory.getLogger(GbifSyncCmd.class);

  public GbifSyncCmd() {
    super(new CliApp(),"gbifsync", "Syncs datasets with the GBIF registry");
  }

  protected void sync(DatasetPager pager, DatasetMapper mapper) throws Exception {
    int created = 0;
    int updated = 0;
    int deleted = 0;

    while (pager.hasNext()) {
      List<Dataset> page = pager.next();
      for (Dataset gbif : page) {
        Dataset existing = mapper.getByGBIF(gbif.getGbifKey());
        if (existing == null) {
          // create new dataset
          mapper.create(gbif);
          created++;

        } else {
          // TODO: check when to update existing dataset and do it properly not just the title
          existing.setTitle(gbif.getTitle());
          mapper.update(existing);
          updated++;
        }
      }
    }
    //TODO: delete datasets no longer in GBIF
    LOG.info("{} datasets added, {} updated, {} deleted", created, updated, deleted);
  }

  @Override
  protected void run(Environment environment, Namespace namespace, CliConfig cfg) throws Exception {
    final HikariDataSource ds = cfg.db.pool();
    final SqlSessionFactory sessionFactory = MybatisFactory.configure(ds, "gbif-sync");

    final RxClient<RxCompletionStageInvoker> rxClient = new JerseyClientBuilder(environment)
        .using(cfg.client)
        .buildRx(getName(), RxCompletionStageInvoker.class);

    try (SqlSession session = sessionFactory.openSession(false)) {
      DatasetPager pager = new DatasetPager(rxClient, cfg.gbif);
      DatasetMapper mapper = session.getMapper(DatasetMapper.class);
      LOG.info("Syncing datasets from GBIF registry {}", cfg.gbif.api);
      sync(pager, mapper);
      session.commit();
      rxClient.close();

    } catch (RuntimeException e) {
      LOG.error("Failed to sync with GBIF", e);

    } finally {
      ds.close();
      rxClient.close();
    }
  }
}
