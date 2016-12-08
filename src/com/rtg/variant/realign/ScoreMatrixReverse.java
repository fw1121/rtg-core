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

package com.rtg.variant.realign;

import com.reeltwo.jumble.annotations.TestClass;
import com.rtg.variant.util.arithmetic.PossibilityArithmetic;

/**
 * Calculates the score matrix in reverse (from end backwards).
 */
@TestClass("com.rtg.variant.realign.ScoreMatrixReverseTestSuite")
public class ScoreMatrixReverse extends AbstractAllPaths {

  /**
   * A score matrix with the given maximum band width.
   * @param arith helper object that does the arithmetic so that this code can be independent of the representation.
   * @param params the gap probabilities to use.
   */
  protected ScoreMatrixReverse(PossibilityArithmetic arith, final RealignParams params) {
    super(arith, params);
  }

  protected double calculateDelete(final int i, final int j, final double match) {
    assert 0 <= i && i < mLength : "i=" + i + " mLength=" + mLength;
    assert 0 <= j && j < mWidth;
    final double m1 = mArith.multiply(match, mOneMinusDeleteExtendPoss);
    final double m2 = mArith.multiply(m1, match(i + 1, j));
    if (j == 0) {
      return m2;
    }
    final double m3 = mArith.multiply(mDeleteExtendPoss, mOneInFourPoss);
    final double m4 = mArith.multiply(delete(i + 1, j - 1), m3);
    return mArith.add(m4, m2);
  }

  protected double calculateInsert(final int i, final int j, final double match) {
    assert 0 <= i && i < mLength;
    assert 0 <= j && j < mWidth;
    if (i == 0) {
      return mArith.zero();
    }
    final double m1 = mArith.multiply(match, mOneMinusInsertExtendPoss);
    final double m2 = mArith.multiply(m1, match(i + 1, j));
    if (j == mWidth - 1) {
      return m2;
    }
    final double s2 = mArith.multiply(mInsertExtendPoss, insert(i, j + 1));
    return mArith.add(s2, m2);
  }

  protected double calculateMatch(final int i, final int j, final double match) {
    assert 0 <= i && i <= mLength;
    assert 0 <= j && j < mWidth;
    final double m1 = mArith.multiply(match, mOneMinusDeleteInsertOpenPoss);
    final double m2 = mArith.multiply(m1, match(i + 1, j));
    final double m3 = j == mWidth - 1 ? mArith.zero() : mArith.multiply(mInsertOpenPoss, insert(i, j + 1));
    final double m4 = j == 0 ? mArith.zero() : mArith.multiply(mDeleteOpenInFourPoss, delete(i + 1, j - 1));
    return mArith.add(m4, mArith.add(m3, m2));
  }

  protected void calculateRow(final int i) {
    for (int j = mWidth - 1; j >= 0; --j) {
      final double ma = matchEq(i, j);
      setDelete(i, j,  calculateDelete(i, j, ma));
      setMatch(i, j,  calculateMatch(i, j, ma));
      setInsert(i, j,  calculateInsert(i, j, ma));
    }
  }

  /**
   * Calculates <code>mEndScores</code>
   */
  protected void calculateEnd() {
    double sumLn = mArith.zero();
    for (int j = 0; j < mWidth; ++j) {
      // final insert row is all zero probabilities for most matrices, but is not initialised in the delta matrices
      // assert mInsert[0][j] == Double.NEGATIVE_INFINITY;
      final double m1 = mArith.multiply(delete(0, j), mDeleteStartPoss);
      final double m2 = mArith.multiply(match(0, j), mMatchStartPoss);
      sumLn = mArith.add(sumLn, mArith.add(m1, m2));
      mEndScores[j] = sumLn;
    }
  }

  @Override
  protected void calculateProbabilities() {
    final double one = mArith.one();
    calculateInitialRow(mLength, one, one);
    for (int i = mLength - 1; i >= 0; --i) {
      calculateRow(i);
    }
    calculateEnd();
  }

  @Override
  public double totalScoreLn() {
    return mArith.poss2Ln(mEndScores[mWidth - 1]);
  }

  @Override
  public double totalScore() {
    return mArith.poss2Prob(mEndScores[mWidth - 1]);
  }

  @Override
  public double total() {
    return mEndScores[mWidth - 1];
  }

  @Override
  public boolean underflow() {
    return mArith.underflow(mEndScores[mWidth - 1]);
  }

  /**
   * Calculates the likelihood that the start of the read is at or before a given template position.
   *
   * @param templatePos an absolute template position.
   * @return the natural log of the probability.
   */
  public double readStartsBeforeLn(int templatePos) {
    final int col = templatePos - mEnv.absoluteTemplatePosition(0) - rowOffset(1);
    if (col < 0) {
      return Double.NEGATIVE_INFINITY;  // read definitely starts after templatePos
    } else if (col >= mWidth) {
      return 0.0;  // read definitely starts before templatePos
    } else {
      return mArith.poss2Ln(mEndScores[col]) - totalScoreLn();
    }
  }
}
