package org.col.admin.validator;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Supplier;

import io.dropwizard.lifecycle.Managed;
import org.col.api.vocab.DataFormat;
import org.col.common.concurrent.ExecutorUtils;
import org.col.common.concurrent.NamedThreadFactory;
import org.col.common.io.DownloadUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ValidatorService implements Managed {
  private static final Logger LOG = LoggerFactory.getLogger(ValidatorService.class);
  private static final String VALIDATOR_THREAD_NAME = "validator";
  private ExecutorService exec;
  private final ValidatorConfig cfg;
  private final DownloadUtil http;
  private final Map<UUID, ValidationRequest> requests = new HashMap<>();
  private final Map<UUID, Future<ValidationReport>> jobs = new HashMap<>();
  
  public ValidatorService(ValidatorConfig cfg) {
    this.cfg = cfg;
  }
  
  public ValidationRequest submit(URI dataAccess, DataFormat format) {
    ValidationRequest req = new ValidationRequest(dataAccess, format);
    return submit(req, download(req));
  }
  
  public ValidationRequest submit(InputStream data, DataFormat format) {
    ValidationRequest req = new ValidationRequest(null, format);
    return submit(req, download(req, data));
  }
  
  
  
  private ValidationRequest submit(ValidationRequest req, Supplier<ValidationRequest> reqSupplier) {
    requests.put(req.getKey(), req);
    CompletableFuture
        .supplyAsync(reqSupplier)
        .thenAccept(this::submit);
    return req;
  }

  private void submit(ValidationRequest req) {
    ValidationJob job = new ValidationJob(cfg, req);
    jobs.put(req.getKey(), exec.submit(job));
  }
  
  private Supplier<ValidationRequest> download(ValidationRequest req) {
  
  }
  
  private Supplier<ValidationRequest> download(ValidationRequest req, InputStream data) {
  
  }

  public ValidationReport get(UUID key) {
    if (requests.containsKey(key)) {
      ValidationRequest req = requests.get(key);
      ValidationReport rep = new ValidationReport(req);
      if (jobs.containsKey(key)) {
        rep.setStatus(ValidationStatus.RUNNING);
      } else if(req.getSource() != null) {
      } else {
        rep.setStatus(ValidationStatus.QUEUED);
      }
    } else {
      return null;
    }
  }

  @Override
  public void start() throws Exception {
    LOG.info("Starting validator service");
    exec = Executors.newSingleThreadExecutor(new NamedThreadFactory(VALIDATOR_THREAD_NAME));
  }
  
  @Override
  public void stop() throws Exception {
    LOG.info("Shutting down validator service");
    ExecutorUtils.shutdown(exec);
  }
}
