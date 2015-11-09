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
package com.rtg.index.hash.ngs.general;

import java.io.IOException;

import com.rtg.index.hash.ngs.HashFunctionFactory;
import com.rtg.index.hash.ngs.NgsHashFunction;
import com.rtg.index.hash.ngs.ReadCallImplementation;
import com.rtg.index.hash.ngs.TemplateCall;
import com.rtg.launcher.HashingRegion;
import com.rtg.util.integrity.IntegralAbstract;

/**
 * Compute number of masks allowing for indels.
 */
public final class MaskIndelCount {

  private static class TemplateCallCount extends IntegralAbstract implements TemplateCall {

    int mCount = 0;

    @Override
    public void done() {
      // do nothing
    }

    @Override
    public void endSequence() {
      // do nothing
    }

    @Override
    public void set(final long name, final int length) {
      // do nothing
    }

    @Override
    public void setReverse(final boolean reverse) {
      // do nothing
    }

    @Override
    public boolean isReverse() {
      return false;
    }

    @Override
    public void setHashFunction(final NgsHashFunction hashFunction) {
      // do nothing
    }

    @Override
    public void templateCall(final int endPosition, final long hash, final int index) {
      mCount++;
    }
    @Override
    public TemplateCallCount clone() throws CloneNotSupportedException {
      return (TemplateCallCount) super.clone();
    }
    @Override
    public TemplateCall threadClone(final HashingRegion region) {
      if (region != HashingRegion.NONE) {
        throw new UnsupportedOperationException();
      }
      try {
        return clone();
      } catch (final CloneNotSupportedException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void threadFinish() {
    }

    @Override
    public void toString(final StringBuilder sb) {
    }

    @Override
    public boolean integrity() {
      return true;
    }
    @Override
    public void logStatistics() {
      // do nothing
    }
  }

  private MaskIndelCount() { }

  /**
   * Compute the total number of masks including the tweaks for indels
   * when using the mask generated by the skeleton <code>sk</code>.
   * @param sk skeleton used to generate mask which is being counted.
   * @return the total number of masks including the tweaks for indels.
   * @throws IOException If an I/O error occurs
   */
  public static int indelCount(final Skeleton sk) throws IOException {
    final HashFunctionFactory factory = Mask.factory(sk, false);
    final TemplateCallCount template = new TemplateCallCount();
    final NgsHashFunction mask = factory.create(new ReadCallImplementation(null), template);
    for (int j = 0; j < sk.readLength(); j++) {
      mask.hashStep((byte) 0);
    }
    mask.templateForward(0);
    return template.mCount;
  }
}
