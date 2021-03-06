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
package com.rtg.metagenomics;

import com.rtg.util.diagnostic.Diagnostic;

/**
 * Wraps around a line solver implementation adding developer logging.
 */
final class LoggingLineSolver implements LineSolver {

  static final double RELATIVE_THRESHOLD = 0.01;

  private final boolean mVeryVerbose;

  private final LineSolver mSolver;

  /**
   * Constructor setting verbose debugging output.
   * @param solver the delegate line solver
   * @param veryVerbose verbose output.
   */
  LoggingLineSolver(LineSolver solver, boolean veryVerbose) {
    if (solver == null) {
      throw new NullPointerException("null solver given.");
    }
    mSolver = solver;
    mVeryVerbose = veryVerbose;
  }

  @Override
  public double solveLine(final Line line, final double relThreshold) {
    final Line vline = new LineVerbose(line, mVeryVerbose);
    final double x = mSolver.solveLine(vline, relThreshold);

    assert x >= 0.0 && AbstractSolver.isFinite(x);
    //assert x == 0.0 || line.value(x) <= 0.0;
    return x;
  }

  private static class LineVerbose extends Line {
    private final Line mProxy;
    private final boolean mVeryVerbose;
    /**
     * @param proxy proxy line
     * @param veryVerbose print very verbose information
     */
    LineVerbose(Line proxy, boolean veryVerbose) {
      super();
      mProxy = proxy;
      mVeryVerbose = veryVerbose;
    }
    @Override
    public double value(double x) {
      final double v = mProxy.value(x);
      if (mVeryVerbose) {
        Diagnostic.developerLog("lineValue x: " + x + " v: " + v);
      }
      return v;
    }
  }
}
