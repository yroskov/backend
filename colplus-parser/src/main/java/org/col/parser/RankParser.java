package org.col.parser;

import com.google.common.base.Strings;
import org.col.api.vocab.Rank;

import java.util.Optional;

/**
 *
 */
public class RankParser implements Parser<Rank> {

  @Override
  public Optional<Rank> parse(String value) {
    //TODO: implement more mappings, likely resource file based
    if (!Strings.isNullOrEmpty(value)) {
      try {
        Rank.valueOf(value.trim().replaceAll(" +", "_").toUpperCase());
      } catch (IllegalArgumentException e) {
        throw new UnparsableException(e);
      }
    }
    return Optional.empty();
  }
}
