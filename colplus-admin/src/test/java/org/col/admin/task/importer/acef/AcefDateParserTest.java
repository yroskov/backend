package org.col.admin.task.importer.acef;

import static org.junit.Assert.assertEquals;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.Test;

@SuppressWarnings("static-method")
public class AcefDateParserTest {

  @Test(expected = IllegalArgumentException.class)
  public void parseYear1() {
    String s = "1972a";
    AcefDateParser parser = AcefDateParser.PARSER;
    parser.parse(s);
  }

  @Test(expected = IllegalArgumentException.class)
  public void parseYear2() {
    String s = "19727";
    AcefDateParser parser = AcefDateParser.PARSER;
    parser.parse(s);
  }

  @Test(expected = IllegalArgumentException.class)
  public void parseYear3() {
    String s = "27";
    AcefDateParser parser = AcefDateParser.PARSER;
    parser.parse(s);
  }

  @Test
  public void parseYear4() {
    String s = "1927";
    AcefDateParser parser = AcefDateParser.PARSER;
    Optional<LocalDate> date = parser.parse(s);
    assertEquals("01", 1927, date.get().getYear());
  }

}