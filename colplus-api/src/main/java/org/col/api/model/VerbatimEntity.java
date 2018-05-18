package org.col.api.model;

import java.util.Set;

import org.col.api.vocab.Issue;

/**
 *
 */
public interface VerbatimEntity {

  Integer getVerbatimKey();

  void setVerbatimKey(Integer verbatimKey);

  Set<Issue> getIssues();

  void addIssue(Issue issue);

}
