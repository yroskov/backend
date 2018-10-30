package org.col.admin.validator;

import java.nio.file.Path;
import java.util.UUID;
import javax.validation.constraints.NotNull;

public class ValidatorConfig {
  private int datasetKey = -100;
  
  @NotNull
  private Path archiveRepo;
  
  public int getDatasetKey() {
    return datasetKey;
  }
  
  public Path storePath(UUID key) {
    return archiveRepo.resolve(key.toString());
  }
  
}
