package org.col.util.io;

import org.col.util.CharsetDetectionTest;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class CharsetDetectingStreamTest {

  @Test
  public void detectEncoding() throws Exception {
    for (Path p : CharsetDetectionTest.testFiles()) {
      System.out.println("\n***** " + PathUtils.getFilename(p) + " *****");
      Charset expected = CharsetDetectionTest.expectedCharset(p);

      CharsetDetectingStream stream = CharsetDetectingStream.create(Files.newInputStream(p));
      final Charset detected = stream.getCharset();

      assertEquals(PathUtils.getFilename(p), expected, detected);

      // try to read entire file
      BufferedReader br = new BufferedReader(new InputStreamReader(stream, detected));
      while ((br.readLine()) != null) {
      }
    }
  }

}