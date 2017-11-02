package org.col.parser.gna;

import org.col.parser.UnparsableException;
import org.globalnames.parser.ScientificNameParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Option;
import scala.collection.Map;

/**
 *
 */
public class ScinameMap {
  private static final Logger LOG = LoggerFactory.getLogger(ScinameMap.class);
  private final Map<String, Object> map;


  public static ScinameMap create(ScientificNameParser.Result result) throws UnparsableException {
    Object details = result.detailed();
    if (details instanceof org.json4s.JsonAST.JArray) {
      org.json4s.JsonAST.JArray array = (org.json4s.JsonAST.JArray) details;
      return new ScinameMap((scala.collection.immutable.Map) array.values().iterator().next());

    } else if (details instanceof org.json4s.JsonAST.JNothing$) {
      // what now?
      throw new UnparsableException("GNA Parser details of type Nothing for name: " + result.render(false, false));

    } else {
      LOG.info(result.render(false, false));
      throw new UnparsableException("GNA Parser details of unkown type " + details.getClass().getCanonicalName());
    }
  }

  private ScinameMap(Map map) {
    this.map = map;
  }

  public Option<Epithet> uninomial() {
    return epithet("uninomial");
  }

  public Option<Epithet> genus() {
    return epithet("genus");
  }

  public Option<Epithet> infraGeneric() {
    return epithet("infrageneric_epithet");
  }

  public Option<Epithet> specificEpithet() {
    return epithet("specific_epithet");
  }

  public Option<Epithet> infraSpecificEpithet() {
    Option opt = ScalaUtils.unwrap(map.get("infraspecific_epithets"));
    if (opt.isDefined()) {
      scala.collection.immutable.List list = (scala.collection.immutable.List) opt.get();
      if (list.isEmpty()) {
        return Option.empty();
      }
      return Option.apply(new Epithet((Map) list.last()));
    }
    return Option.empty();
  }

  Option<Object> annotation() {
    return map.get("annotation_identification");
  }


  private Option<Epithet> epithet(String key) {
    Option val = ScalaUtils.unwrap(map.get(key));
    if (val.isDefined()) {
      return Option.apply(new Epithet((Map) val.get()));
    }
    return Option.empty();
  }

}