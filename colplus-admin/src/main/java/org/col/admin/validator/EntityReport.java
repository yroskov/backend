package org.col.admin.validator;

import java.util.HashMap;
import java.util.Map;

import org.col.api.model.VerbatimRecord;
import org.col.api.vocab.Issue;
import org.gbif.dwc.terms.Term;

public class EntityReport {
  private Term type;
  private int records;
  private Map<Term, String> terms = new HashMap<>();
  private Map<Issue, Integer> issues = new HashMap<>();
  private Map<Issue, VerbatimRecord> example = new HashMap<>();
}
