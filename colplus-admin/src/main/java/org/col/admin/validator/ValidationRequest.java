package org.col.admin.validator;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.UUID;

import org.col.api.vocab.DataFormat;

public class ValidationRequest {
  private UUID key = UUID.randomUUID();
  private URI source;
  private DataFormat format;
  private LocalDateTime created = LocalDateTime.now();
  
  public ValidationRequest() {
  }
  
  public ValidationRequest(URI source, DataFormat format) {
    this.source = source;
    this.format = format;
  }
  
  public URI getSource() {
    return source;
  }
  
  public void setSource(URI source) {
    this.source = source;
  }
  
  public DataFormat getFormat() {
    return format;
  }
  
  public void setFormat(DataFormat format) {
    this.format = format;
  }
  
  public UUID getKey() {
    return key;
  }
  
  public LocalDateTime getCreated() {
    return created;
  }
}
