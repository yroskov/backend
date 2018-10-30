package org.col.admin.validator;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.concurrent.Callable;

import org.col.common.util.LoggingUtils;

public class ValidationJob implements Callable<ValidationReport> {
  
  private final ValidationReport report = new ValidationReport();
  private final ValidatorConfig cfg;
  private final Path archive;
  
  public ValidationJob(ValidatorConfig cfg, ValidationRequest req) {
    this.cfg = cfg;
    archive = cfg.storePath(req.getKey());
    report.setKey(req.getKey());
    report.setCreated(req.getCreated());
  }
  
  @Override
  public ValidationReport call() throws Exception {
    report.setStarted(LocalDateTime.now());
    LoggingUtils.setMDC(cfg.getDatasetKey(), getClass());
    try {
      validate();
    } catch (Exception e){
      report.setStatus(ValidationStatus.ABORTED);
      report.setError(e.getMessage());
      
    } finally {
      LoggingUtils.removeMDC();
    }
    return report;
  }
  
  private void validate() {
  
  }
}
