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
package com.rtg.index.hash.ngs;


import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.rtg.index.hash.ngs.instances.CG2Maska1;
import com.rtg.index.hash.ngs.instances.CG2Maska11;
import com.rtg.index.hash.ngs.instances.CG2Maska15;
import com.rtg.index.hash.ngs.instances.CG2Maskw18;
import com.rtg.index.hash.ngs.instances.CG2Maskw18a1;
import com.rtg.util.diagnostic.ErrorType;
import com.rtg.util.diagnostic.NoTalkbackSlimException;

/**
 * Provides access to the set of mask factory implementations.
 *
 */
public final class FactoryUtil {

  private FactoryUtil() { } //prevent instantiation.

  private static final Map<String, HashFunctionFactory> MASK_FACTORIES = new HashMap<>();

  static {
    putFactory("CGMaska0", com.rtg.index.hash.ngs.instances.CGMaska0.FACTORY);
    putFactory("CGMaska1b1", com.rtg.index.hash.ngs.instances.CGMaska1b1.FACTORY);
    putFactory("CGMaska15b1", com.rtg.index.hash.ngs.instances.CGMaska15b1.FACTORY);
    putFactory("CGMaska1b1alt", com.rtg.index.hash.ngs.instances.CGMaska1b1alt.FACTORY);
    putFactory("CGMaska15b1alt", com.rtg.index.hash.ngs.instances.CGMaska15b1alt.FACTORY);
    putFactory("CG2Maska1", CG2Maska1.FACTORY);
    putFactory("CG2Maska11", CG2Maska11.FACTORY);
    putFactory("CG2Maska15", CG2Maska15.FACTORY);
    putFactory("CG2Maskw18", CG2Maskw18.FACTORY);
    putFactory("CG2Maskw18a1", CG2Maskw18a1.FACTORY);
    //-------------- public options
    putFactory("cg1", com.rtg.index.hash.ngs.instances.CGMaska15b1.FACTORY);
    putFactory("cg1-fast", com.rtg.index.hash.ngs.instances.CGMaska1b1.FACTORY);
    putFactory("cg2", CG2Maskw18a1.FACTORY);
  }

  private static void putFactory(String name, HashFunctionFactory factory) {
    MASK_FACTORIES.put(name.toLowerCase(Locale.getDefault()), factory);
  }

  /**
   * @param mask Class name of the mask class.
   * @return check if the mask exists
   */
  public static boolean checkMaskExists(final String mask) {
    if (mask != null) {
      return MASK_FACTORIES.containsKey(mask.toLowerCase(Locale.getDefault()));
    }
    return false;
  }

  /**
   * @param mask Class name of the mask class.
   * @return a factory which will create a new a <code>HashFunction</code>.
   */
  public static HashFunctionFactory hashFunction(final String mask) {
    final String m = mask == null ? null : mask.toLowerCase(Locale.getDefault());
    final HashFunctionFactory factory = MASK_FACTORIES.get(m);
    if (factory == null) {
      throw new NoTalkbackSlimException(ErrorType.INVALID_MASK, mask);
    }
    return factory;
  }

}

