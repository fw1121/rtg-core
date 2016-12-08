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
package com.rtg.variant.bayes.multisample.lineage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.rtg.variant.bayes.MockModel;
import com.rtg.variant.bayes.ModelInterface;
import com.rtg.variant.bayes.snp.DescriptionCommon;
import com.rtg.variant.bayes.snp.HypothesesPrior;
import com.rtg.variant.bayes.snp.StatisticsSnp;
import com.rtg.variant.util.arithmetic.SimplePossibility;

import junit.framework.TestCase;

/**
 * Test class.
 */
public class ForwardBackwardLineageTest extends TestCase {

  public void testDeNovoUnlikely() {
    final Lineage.LineageBuilder lb = new Lineage.LineageBuilder();
    lb.add(0, 1);
    lb.deNovoPriorDefault(0.001);
    final Lineage lineage = lb.create();
    final List<ModelInterface<?>> models = new ArrayList<>();
    final HypothesesPrior<DescriptionCommon> h = new HypothesesPrior<>(new DescriptionCommon("A", "B", "C"), SimplePossibility.SINGLETON, new double[] {0.2, 0.5, 0.3}, true, 0);
    models.add(new MockModel<>(h.hypotheses(), new StatisticsSnp(h.description()), new double[] {0.3, 0.3, 0.4}));
    models.add(new MockModel<>(h.hypotheses(), new StatisticsSnp(h.description()), new double[] {0.3, 0.3, 0.4}));
    final Factor[] rootGenotypePrior = {new ModelFactor(new Variable("G" + 0, h.size()), h), null};
    final ForwardBackwardLineage fb = new ForwardBackwardLineage(SimplePossibility.SINGLETON, lineage, rootGenotypePrior, models);
    assertNotNull(fb.posterior(1));
  }

  public void testLikely() {
    final Lineage.LineageBuilder lb = new Lineage.LineageBuilder();
    lb.add(0, 1);
    final Lineage lineage = lb.create();
    final List<ModelInterface<?>> models = new ArrayList<>();
    final HypothesesPrior<DescriptionCommon> h = new HypothesesPrior<>(new DescriptionCommon("A", "B", "C"), SimplePossibility.SINGLETON, new double[] {0.2, 0.5, 0.3}, true, 0);
    models.add(new MockModel<>(h.hypotheses(), new StatisticsSnp(h.description()), new double[] {0.0005, 0.0005, 0.999}));
    models.add(new MockModel<>(h.hypotheses(), new StatisticsSnp(h.description()), new double[] {0.999, 0.0005, 0.0005}));
    final Factor[] rootGenotypePrior = {new ModelFactor(new Variable("G" + 0, h.size()), h), null};
    final ForwardBackwardLineage fb = new ForwardBackwardLineage(SimplePossibility.SINGLETON, lineage, rootGenotypePrior, models);
    assertNotNull(fb.posterior(1));
  }

  void checkFactor(Factor f, Variable v, double... values) {
    final Factor normal = DefaultFactor.asNormalized(f);
    assertEquals(values.length, v.size());
    for (int i = 0; i < v.size(); ++i) {
      assertEquals(values[i], normal.p(Collections.singletonMap(v, i)), 1e-5);
    }
  }

  public void testLikelyLineage() {
    final Lineage.LineageBuilder lb = new Lineage.LineageBuilder();
    lb.add(0, 1);
    lb.add(1, 2);
    lb.deNovoPriorDefault(0.001);
    final Lineage lineage = lb.create();
    final List<ModelInterface<?>> models = new ArrayList<>();
    final HypothesesPrior<DescriptionCommon> h = new HypothesesPrior<>(new DescriptionCommon("A", "B", "C"), SimplePossibility.SINGLETON, new double[] {0.2, 0.5, 0.3}, true, 0);
    models.add(new MockModel<>(h.hypotheses(), new StatisticsSnp(h.description()), new double[] {0.3, 0.3, 0.4}));
    models.add(new MockModel<>(h.hypotheses(), new StatisticsSnp(h.description()), new double[] {0.4, 0.3, 0.3}));
    models.add(new MockModel<>(h.hypotheses(), new StatisticsSnp(h.description()), new double[] {0.4, 0.3, 0.3}));
    final Factor[] rootGenotypePrior = {new ModelFactor(new Variable("G" + 0, h.size()), h), null, null};
    final ForwardBackwardLineage fb = new ForwardBackwardLineage(SimplePossibility.SINGLETON, lineage, rootGenotypePrior, models);
    assertNotNull(fb.posterior(1));

//    System.err.println(fb.posterior(0));
//    for (int k = 1; k < models.size(); ++k) {
//      System.err.println(DefaultFactor.asNormalized(fb.posteriorDeNovo(k)));
//    }
    final Variable v0 = new Variable("G0", h.size());
    checkFactor(fb.posterior(0), v0, 0.28293, 0.39837, 0.31869);

    final Variable v2 = new Variable("G2", h.size());
    // Externally calculated
    checkFactor(fb.posterior(2), v2, 0.28368, 0.39786, 0.31847);
  }

  public void testLikelyLongerLineage() {
    final Lineage.LineageBuilder lb = new Lineage.LineageBuilder();
    lb.add(0, 1);
    lb.add(1, 2);
    lb.add(2, 3);
    lb.add(3, 4);
    final Lineage lineage = lb.create();
    final List<ModelInterface<?>> models = new ArrayList<>();
    final HypothesesPrior<DescriptionCommon> h = new HypothesesPrior<>(new DescriptionCommon("A", "B", "C"), SimplePossibility.SINGLETON, new double[] {0.2, 0.5, 0.3}, true, 0);
    models.add(new MockModel<>(h.hypotheses(), new StatisticsSnp(h.description()), new double[] {0.0005, 0.0005, 0.999}));
    models.add(new MockModel<>(h.hypotheses(), new StatisticsSnp(h.description()), new double[] {0.0005, 0.0005, 0.999}));
    models.add(new MockModel<>(h.hypotheses(), new StatisticsSnp(h.description()), new double[] {0.999, 0.0005, 0.0005}));
    models.add(new MockModel<>(h.hypotheses(), new StatisticsSnp(h.description()), new double[] {0.999, 0.0009, 0.0001}));
    models.add(new MockModel<>(h.hypotheses(), new StatisticsSnp(h.description()), new double[] {0.999, 0.0009, 0.0001}));
    final Factor[] rootGenotypePrior = {new ModelFactor(new Variable("G" + 0, h.size()), h), null, null, null, null};
    final ForwardBackwardLineage fb = new ForwardBackwardLineage(SimplePossibility.SINGLETON, lineage, rootGenotypePrior, models);
    assertNotNull(fb.posterior(1));

//    System.err.println(fb.posterior(0));
//    for (int k = 1; k < 5; ++k) {
//      System.err.println(DefaultFactor.asNormalized(fb.posteriorDeNovo(k)));
//    }
  }

  public void testSiblings() {
    final Lineage.LineageBuilder lb = new Lineage.LineageBuilder();
    lb.add(0, 1);
    lb.add(1, 2);
    lb.add(2, 3);
    lb.add(1, 4);
    lb.deNovoPriorDefault(0.001);
    final Lineage lineage = lb.create();
    final List<ModelInterface<?>> models = new ArrayList<>();
    final HypothesesPrior<DescriptionCommon> h = new HypothesesPrior<>(new DescriptionCommon("A", "B", "C"), SimplePossibility.SINGLETON, new double[] {0.2, 0.5, 0.3}, true, 0);
    models.add(new MockModel<>(h.hypotheses(), new StatisticsSnp(h.description()), new double[] {0.005, 0.005, 0.99}));
    models.add(new MockModel<>(h.hypotheses(), new StatisticsSnp(h.description()), new double[] {0.005, 0.005, 0.99}));
    models.add(new MockModel<>(h.hypotheses(), new StatisticsSnp(h.description()), new double[] {0.99, 0.005, 0.005}));
    models.add(new MockModel<>(h.hypotheses(), new StatisticsSnp(h.description()), new double[] {0.99, 0.009, 0.001}));
    models.add(new MockModel<>(h.hypotheses(), new StatisticsSnp(h.description()), new double[] {0.005, 0.005, 0.99}));
    final Factor[] rootGenotypePrior = {new ModelFactor(new Variable("G" + 0, h.size()), h), null, null, null, null};
    final ForwardBackwardLineage fb = new ForwardBackwardLineage(SimplePossibility.SINGLETON, lineage, rootGenotypePrior, models);

    final Variable v3 = new Variable("G" + 3, h.size());
    final Variable v4 = new Variable("G" + 4, h.size());

    // Externally calculated
    checkFactor(fb.posterior(3), v3, 0.98986, 9.4924e-5, 0.01003);
    checkFactor(fb.posterior(4), v4, 1.9682e-4, 2.8701e-6, 0.99980);
  }

  public void testNoEvidenceMiddleGuy() {
    final Lineage.LineageBuilder lb = new Lineage.LineageBuilder();
    lb.add(0, 1);
    lb.add(1, 2);
    lb.add(2, 3);
    lb.add(1, 4);
    final Lineage lineage = lb.create();
    final List<ModelInterface<?>> models = new ArrayList<>();
    final HypothesesPrior<DescriptionCommon> h = new HypothesesPrior<>(new DescriptionCommon("A", "B", "C"), SimplePossibility.SINGLETON, new double[] {0.2, 0.5, 0.3}, true, 0);
    models.add(new MockModel<>(h.hypotheses(), new StatisticsSnp(h.description()), new double[] {0.0005, 0.0005, 0.999}));
    models.add(new MockModel<>(h.hypotheses(), new StatisticsSnp(h.description()), new double[] {1.0 / 3, 1.0 / 3, 1.0 / 3}));
    models.add(new MockModel<>(h.hypotheses(), new StatisticsSnp(h.description()), new double[] {0.999, 0.0005, 0.0005}));
    models.add(new MockModel<>(h.hypotheses(), new StatisticsSnp(h.description()), new double[] {0.999, 0.0009, 0.0001}));
    models.add(new MockModel<>(h.hypotheses(), new StatisticsSnp(h.description()), new double[] {0.0005, 0.0005, 0.999}));
    final Factor[] rootGenotypePrior = {new ModelFactor(new Variable("G" + 0, h.size()), h), null, null, null, null};
    final ForwardBackwardLineage fb = new ForwardBackwardLineage(SimplePossibility.SINGLETON, lineage, rootGenotypePrior, models);
    assertNotNull(fb.posterior(1));

//    System.err.println(fb.posterior(0));
//    for (int k = 1; k < 5; ++k) {
//      System.err.println(DefaultFactor.asNormalized(fb.posteriorDeNovo(k)));
//    }
  }

  public void testDeNovoUnlikelyDiploid() {
    final Lineage.LineageBuilder lb = new Lineage.LineageBuilder();
    lb.add(0, 1);
    lb.deNovoPriorDefault(0.001);
    final Lineage lineage = lb.create();
    final List<ModelInterface<?>> models = new ArrayList<>();
    final HypothesesPrior<DescriptionCommon> h = new HypothesesPrior<>(new DescriptionCommon("A", "B", "C"), SimplePossibility.SINGLETON, new double[] {0.1, 0.1, 0.2, 0.1, 0.4, 0.1}, false, 0);
    models.add(new MockModel<>(h.hypotheses(), new StatisticsSnp(h.description()), new double[] {0.2, 0.2, 0.1, 0.1, 0.3, 0.1}));
    models.add(new MockModel<>(h.hypotheses(), new StatisticsSnp(h.description()), new double[] {0.2, 0.2, 0.1, 0.1, 0.3, 0.1}));
    final Factor[] rootGenotypePrior = {new ModelFactor(new Variable("G" + 0, h.size()), h), null};
    final ForwardBackwardLineage fb = new ForwardBackwardLineage(SimplePossibility.SINGLETON, lineage, rootGenotypePrior, models);
    assertNotNull(fb.posterior(1));
  }

}
