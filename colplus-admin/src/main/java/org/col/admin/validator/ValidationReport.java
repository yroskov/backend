package org.col.admin.validator;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import org.col.api.vocab.DataFormat;
import org.gbif.dwc.terms.Term;

public class ValidationReport {
  private UUID key;
  private String md5;
  private DataFormat format;
  private Integer bytes;
  private LocalDateTime created;
  private ValidationStatus status;
  
  private String error;
  private LocalDateTime started;
  private LocalDateTime finished;
  
  private Integer names;
  private Integer taxa;
  private Integer synonyms;
  private Integer references;
  private Integer descriptions;
  private Integer distributions;
  private Integer media;
  private Integer vernaculars;

  private Map<Term, EntityReport> entities;
  
  public ValidationReport() {
  }
  
  public ValidationReport(ValidationRequest req) {
    this.key = req.getKey();
    this.format = req.getFormat();
    this.created = req.getCreated();
  }
  
  public UUID getKey() {
    return key;
  }
  
  public void setKey(UUID key) {
    this.key = key;
  }
  
  public String getMd5() {
    return md5;
  }
  
  public void setMd5(String md5) {
    this.md5 = md5;
  }
  
  public DataFormat getFormat() {
    return format;
  }
  
  public void setFormat(DataFormat format) {
    this.format = format;
  }
  
  public Integer getBytes() {
    return bytes;
  }
  
  public void setBytes(Integer bytes) {
    this.bytes = bytes;
  }
  
  public LocalDateTime getCreated() {
    return created;
  }
  
  public void setCreated(LocalDateTime created) {
    this.created = created;
  }
  
  public ValidationStatus getStatus() {
    return status;
  }
  
  public void setStatus(ValidationStatus status) {
    this.status = status;
  }
  
  public String getError() {
    return error;
  }
  
  public void setError(String error) {
    this.error = error;
  }
  
  public LocalDateTime getStarted() {
    return started;
  }
  
  public void setStarted(LocalDateTime started) {
    this.started = started;
  }
  
  public LocalDateTime getFinished() {
    return finished;
  }
  
  public void setFinished(LocalDateTime finished) {
    this.finished = finished;
  }
  
  public Integer getNames() {
    return names;
  }
  
  public void setNames(Integer names) {
    this.names = names;
  }
  
  public Integer getTaxa() {
    return taxa;
  }
  
  public void setTaxa(Integer taxa) {
    this.taxa = taxa;
  }
  
  public Integer getSynonyms() {
    return synonyms;
  }
  
  public void setSynonyms(Integer synonyms) {
    this.synonyms = synonyms;
  }
  
  public Integer getReferences() {
    return references;
  }
  
  public void setReferences(Integer references) {
    this.references = references;
  }
  
  public Integer getDescriptions() {
    return descriptions;
  }
  
  public void setDescriptions(Integer descriptions) {
    this.descriptions = descriptions;
  }
  
  public Integer getDistributions() {
    return distributions;
  }
  
  public void setDistributions(Integer distributions) {
    this.distributions = distributions;
  }
  
  public Integer getMedia() {
    return media;
  }
  
  public void setMedia(Integer media) {
    this.media = media;
  }
  
  public Integer getVernaculars() {
    return vernaculars;
  }
  
  public void setVernaculars(Integer vernaculars) {
    this.vernaculars = vernaculars;
  }
  
  public Map<Term, EntityReport> getEntities() {
    return entities;
  }
  
  public void setEntities(Map<Term, EntityReport> entities) {
    this.entities = entities;
  }
}
