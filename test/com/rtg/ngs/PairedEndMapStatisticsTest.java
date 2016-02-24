/*
 * Copyright (c) 2014. Real Time Genomics Limited.
 *
 * Use of this source code is bound by the Real Time Genomics Limited Software Licence Agreement
 * for Academic Non-commercial Research Purposes only.
 *
 * If you did not receive a license accompanying this file, a copy must first be obtained by email
 * from support@realtimegenomics.com.  On downloading, using and/or continuing to use this source
 * code you accept the terms of that license agreement and any amendments to those terms that may
 * be made from time to time by Real Time Genomics Limited.
 */
package com.rtg.ngs;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import com.rtg.util.TestUtils;
import com.rtg.util.diagnostic.Diagnostic;

import junit.framework.TestCase;

/**
 */
public class PairedEndMapStatisticsTest extends TestCase {

  public void testPrintStatistics() throws Exception {
    final PairedEndMapStatistics testStats = new PairedEndMapStatistics(true, null);
    final ByteArrayOutputStream log = new ByteArrayOutputStream();
    final PrintStream ps = new PrintStream(log);
    Diagnostic.setLogStream(ps);
    try {
      testStats.printStatistics(null);
    } finally {
      //closes log
      Diagnostic.setLogStream();
    }
    assertEquals("", log.toString());
    testStats.set(MapStatisticsField.TOTAL_READS, Arm.LEFT, 1000L);
    testStats.set(MapStatisticsField.TOTAL_READS, Arm.RIGHT, 234L);
    testStats.set(MapStatisticsField.UNMATED_UNIQUE_READS, Arm.RIGHT, 567L);
    testStats.set(MapStatisticsField.UNMATED_AMBIG_READS, Arm.LEFT, 5L);
    testStats.set(MapStatisticsField.UNMATED_UNIQUE_READS, Arm.RIGHT, 932L);
    testStats.set(MapStatisticsField.UNMAPPED_NO_HITS, Arm.LEFT, 152L);
    testStats.set(MapStatisticsField.MISSING, Arm.LEFT, 12L);
    testStats.set(MapStatisticsField.MATED_AMBIG_READS, Arm.LEFT, 33L);
    testStats.set(MapStatisticsField.MATED_AMBIG_READS, Arm.RIGHT, 66L);

    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    testStats.printStatistics(out);
    final String outString = out.toString();
    TestUtils.containsAll(outString,
        "ARM MAPPINGS",
        " left right  both",
        " 1000   234  1234 100.0% total",
        "    0     0     0   0.0% mated uniquely",
        "   33    66    99   8.0% mated ambiguously (NH > 1)",
        "    0   932   932  75.5% unmated uniquely",
        "    5     0     5   0.4% unmated ambiguously",
        "    0     0     0   0.0% unmapped with no matings but too many hits (XC = C)",
        "  152     0   152  12.3% unmapped with no hits",
        "    0     0     0   0.0% unmapped due to read frequency",
        "    0     0     0   0.0% unmapped with no matings",
        "    0     0     0   0.0% unmapped with poor matings",
        "    0     0     0   0.0% unmapped with too many matings",
        "    0     0     0   0.0% unmapped with no matings and poor hits",
        "    0     0     0   0.0% unmapped with no matings and too many good hits"
        );
  }

  public void testMerge() {
    Diagnostic.setLogStream();
    final PairedEndMapStatistics testStats = new PairedEndMapStatistics(false, null);
    testStats.set(MapStatisticsField.TOTAL_READS, Arm.LEFT, 220L);
    testStats.set(MapStatisticsField.MATED_UNIQUE_READS, Arm.LEFT, 25L);
    testStats.set(MapStatisticsField.UNMATED_UNIQUE_READS, Arm.LEFT, 75L);
    testStats.set(MapStatisticsField.UNMAPPED_NO_HITS, Arm.LEFT, 80L);
    testStats.set(MapStatisticsField.MISSING, Arm.LEFT, 0L);

    final PairedEndMapStatistics testStats2 = new PairedEndMapStatistics(false, null);
    testStats2.set(MapStatisticsField.TOTAL_READS, Arm.LEFT, 220L);
    testStats2.set(MapStatisticsField.MATED_UNIQUE_READS, Arm.LEFT, 80L);
    testStats2.set(MapStatisticsField.UNMATED_UNIQUE_READS, Arm.LEFT, 20L);
    testStats2.set(MapStatisticsField.UNMAPPED_NO_HITS, Arm.LEFT, 7L);
    testStats2.set(MapStatisticsField.MISSING, Arm.LEFT, 2L);

    testStats.merge(testStats2);

    assertEquals(220L, testStats.value(MapStatisticsField.TOTAL_READS, Arm.LEFT));
    assertEquals(105L, testStats.value(MapStatisticsField.MATED_UNIQUE_READS, Arm.LEFT));
    assertEquals(95L, testStats.value(MapStatisticsField.UNMATED_UNIQUE_READS, Arm.LEFT));
    assertEquals(20L, testStats.value(MapStatisticsField.UNMAPPED_NO_HITS, Arm.LEFT));
    assertEquals(2L, testStats.value(MapStatisticsField.MISSING, Arm.LEFT));
  }

}
