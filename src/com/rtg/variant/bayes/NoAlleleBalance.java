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

package com.rtg.variant.bayes;

/**
 * Represents not adjusting for allele balance
 * Will return the multiplicative identity in log space(0), so probability will not change
 */
public class NoAlleleBalance implements AlleleBalanceProbability {

  @Override
  public double alleleBalanceLn(int i, Hypotheses<?> hypotheses, Statistics<?> statistics) {
    return 0.0; // 1 in log space.
  }

  @Override
  public String toString() {
    return "none";
  }
}
