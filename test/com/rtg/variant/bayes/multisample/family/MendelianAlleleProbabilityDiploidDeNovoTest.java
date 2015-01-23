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

package com.rtg.variant.bayes.multisample.family;

import com.rtg.variant.bayes.Code;
import com.rtg.variant.bayes.CodeDiploid;

import junit.framework.TestCase;

/**
 */
public class MendelianAlleleProbabilityDiploidDeNovoTest extends TestCase {

  public void testUniHypothesisLookup() {
    // Non de novo should give no likelihood
    final Code code = new CodeDiploid(1);
    assertEquals(Double.NEGATIVE_INFINITY, MendelianAlleleProbabilityDiploidDeNovo.SINGLETON.probabilityLn(code, code.code(0), code.code(0), code.code(0)));
  }

  public void testProbabilityLnLookup() {
    final Code code = new CodeDiploid(4);

    // Non de novo should give no likelihood
    assertEquals(Double.NEGATIVE_INFINITY, MendelianAlleleProbabilityDiploidDeNovo.SINGLETON.probabilityLn(code, code.code(0), code.code(1), code.code(0, 1)));
    assertEquals(Double.NEGATIVE_INFINITY, MendelianAlleleProbabilityDiploidDeNovo.SINGLETON.probabilityLn(code, code.code(0, 1), code.code(1), code.code(0, 1)));
    assertEquals(Double.NEGATIVE_INFINITY, MendelianAlleleProbabilityDiploidDeNovo.SINGLETON.probabilityLn(code, code.code(0, 1), code.code(2, 3), code.code(0, 3)));

    // De novo table entries
    assertEquals(Math.log(1.0 / 8), MendelianAlleleProbabilityDiploidDeNovo.SINGLETON.probabilityLn(code, code.code(0, 1), code.code(0, 1), code.code(0, 2)), 0.0001);
    assertEquals(Math.log(1.0 / 6), MendelianAlleleProbabilityDiploidDeNovo.SINGLETON.probabilityLn(code, code.code(0, 1), code.code(2, 3), code.code(0)), 0.0001);
    assertEquals(Math.log(1.0 / 6), MendelianAlleleProbabilityDiploidDeNovo.SINGLETON.probabilityLn(code, code.code(0), code.code(1), code.code(2, 0)), 0.0001);
    assertEquals(Math.log(1.0 / 12), MendelianAlleleProbabilityDiploidDeNovo.SINGLETON.probabilityLn(code, code.code(0), code.code(1, 2), code.code(3, 1)), 0.0001);
  }

  //test symmetries and the two ways of accessing the table
  public void testLookupCode() {
    final Code code = new CodeDiploid(4);
    for (int i = 0; i < code.size(); i++)  {
      for (int j = 0; j < code.size(); j++)  {
        //System.err.println("i=" + i + " j=" + j);
        for (int k = 0; k < code.size(); k++)  {
          final double exp = MendelianAlleleProbabilityDiploidDeNovo.SINGLETON.probabilityLn(code, i, j, k);
          assertEquals("f=" + code.a(i) + "/" + code.bc(i) + " m=" + code.a(j) + "/" + code.bc(j) + " c=" + code.a(k) + "/" + code.bc(k), exp, MendelianAlleleProbabilityDiploidDeNovo.SINGLETON.probabilityLn(code, j, i, k));
        }
      }
    }
  }

  //test there is no overlap between non de novo and de novo tables
  public void testOverlap() {
    final Code code = new CodeDiploid(4);
    for (int i = 0; i < code.size(); i++) {
      for (int j = 0; j < code.size(); j++) {
        for (int k = 0; k < code.size(); k++) {
          if (MendelianAlleleProbabilityDiploid.SINGLETON.probabilityLn(code, j, k, i) > Double.NEGATIVE_INFINITY) {
            assertEquals(Double.NEGATIVE_INFINITY, MendelianAlleleProbabilityDiploidDeNovo.SINGLETON.probabilityLn(code, j, k, i));
          }
        }
      }
    }
  }
}
