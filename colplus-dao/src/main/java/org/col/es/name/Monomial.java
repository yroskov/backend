package org.col.es.name;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.col.es.ddl.ESDataType;
import org.col.es.ddl.MapToType;
import org.gbif.nameparser.api.Rank;

/**
 * A simple rank-name tuple. When indexing NameUsages the SimpleName instances that constitute a taxon's/synonym's classification are split
 * into a list of taxon/synonym ids on the one hand and a list of monomials on the other. This allows for fast retrieval by id, because no
 * nested query on subdocuments is necessary this way. In addition, if you have a query condition on the "primary key" field (the taxon ID),
 * it hardly ever makes sense to have any other query condition. We do, however, wrap rank and name into a separate object, because here
 * combining the two fields in an AND query could make sense, thus necessitating a nested query.
 */
public class Monomial {

  private Rank rank;
  private String name;

  @JsonCreator
  public Monomial(@JsonProperty("rank") Rank rank, @JsonProperty("name") String name) {
    this.rank = rank;
    this.name = name;
  }

  @MapToType(ESDataType.KEYWORD)
  public Rank getRank() {
    return rank;
  }

  public String getName() {
    return name;
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, rank);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    Monomial other = (Monomial) obj;
    return Objects.equals(name, other.name) && rank == other.rank;
  }

}