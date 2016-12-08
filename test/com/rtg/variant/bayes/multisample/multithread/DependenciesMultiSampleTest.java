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

package com.rtg.variant.bayes.multisample.multithread;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import com.rtg.scheduler.AbstractDependenciesTest;
import com.rtg.scheduler.Dependencies;
import com.rtg.scheduler.EventList;
import com.rtg.scheduler.Executor;
import com.rtg.scheduler.ExecutorSequential;
import com.rtg.scheduler.Job;
import com.rtg.scheduler.JobFactory;
import com.rtg.scheduler.Result;
import com.rtg.scheduler.Scheduler;
import com.rtg.scheduler.SchedulerSynchronized;
import com.rtg.util.integrity.Exam;
import com.rtg.util.io.MemoryPrintStream;
import com.rtg.variant.bayes.multisample.Complexities;

/**
 */
public class DependenciesMultiSampleTest extends AbstractDependenciesTest<JobIdMultisample> {


  @Override
  protected Dependencies<JobIdMultisample> getDependencies() {
    return new DependenciesMultiSample(5);
  }

  public void testGlobalIntegrity() {
    Exam.globalIntegrity(getDependencies());
  }

  public void testDetails() {
    final int numberChunks = 5;
    final Dependencies<JobIdMultisample> dependencies = getDependencies();
    assertEquals(1, dependencies.delta());
    final Set<JobIdMultisample> allIds = allIds(dependencies, 5);
    checkAllIds(numberChunks, allIds);
  }

  //check that when look ahead restrictions in place still get all ids
  public void testLookAheadAll() {
    final int numberChunks = 5;
    final Set<JobIdMultisample> allIds = allIdsLookahead((DependenciesMultiSample) getDependencies());
    //System.err.println(allIds);
    checkAllIds(numberChunks, allIds);
  }

  private void checkAllIds(final int numberChunks, final Set<JobIdMultisample> allIds) {
    final JobType[] values = JobType.values();
    assertTrue(allIds.contains(new JobIdMultisample(numberChunks, 0, JobType.INCR)));
    assertTrue(allIds.contains(new JobIdMultisample(numberChunks, 0, JobType.DANGLING)));
    assertTrue(allIds.contains(new JobIdMultisample(numberChunks, 0, JobType.COMPLEX)));
    assertTrue(allIds.contains(new JobIdMultisample(numberChunks, 0, JobType.BED)));
    assertFalse(allIds.contains(new JobIdMultisample(numberChunks, 0, JobType.FILTER)));
    assertFalse(allIds.contains(new JobIdMultisample(numberChunks, 0, JobType.OUT)));
    for (int i = 1; i < numberChunks; ++i) {
      for (final JobType ty : values) {
        assertTrue("i=" + i + " ty=" + ty, allIds.contains(new JobIdMultisample(numberChunks, i, ty)));
      }
    }
    assertFalse(allIds.contains(new JobIdMultisample(numberChunks, numberChunks, JobType.INCR)));
    assertTrue(allIds.contains(new JobIdMultisample(numberChunks, numberChunks, JobType.DANGLING)));
    assertTrue(allIds.contains(new JobIdMultisample(numberChunks, numberChunks, JobType.COMPLEX)));
    assertTrue(allIds.contains(new JobIdMultisample(numberChunks, numberChunks, JobType.BED)));
    assertTrue(allIds.contains(new JobIdMultisample(numberChunks, numberChunks, JobType.FILTER)));
    assertTrue(allIds.contains(new JobIdMultisample(numberChunks, numberChunks, JobType.OUT)));
  }

  //make sure look ahead restricts the generated jobs.
  public void testLookAhead() {
    final int numberChunks = 5;
    final int lookAhead = 3;
    final Set<JobIdMultisample> allIds = allIds(getDependencies(), 3);
    //System.err.println(allIds);
    final JobType[] values = JobType.values();
    assertTrue(allIds.contains(new JobIdMultisample(numberChunks, 0, JobType.INCR)));
    assertTrue(allIds.contains(new JobIdMultisample(numberChunks, 0, JobType.DANGLING)));
    assertTrue(allIds.contains(new JobIdMultisample(numberChunks, 0, JobType.COMPLEX)));
    assertTrue(allIds.contains(new JobIdMultisample(numberChunks, 0, JobType.BED)));
    assertFalse(allIds.contains(new JobIdMultisample(numberChunks, 0, JobType.FILTER)));
    assertFalse(allIds.contains(new JobIdMultisample(numberChunks, 0, JobType.OUT)));
    for (int i = 1; i <= lookAhead; ++i) {
      for (final JobType ty : values) {
        assertTrue("i=" + i + " ty=" + ty, allIds.contains(new JobIdMultisample(numberChunks, i, ty)));
      }
    }
    final int lim = lookAhead + 1;
    assertFalse(allIds.contains(new JobIdMultisample(numberChunks, lim, JobType.INCR)));
    assertTrue(allIds.contains(new JobIdMultisample(numberChunks, lim, JobType.DANGLING)));
    assertTrue(allIds.contains(new JobIdMultisample(numberChunks, lim, JobType.COMPLEX)));
    assertTrue(allIds.contains(new JobIdMultisample(numberChunks, lim, JobType.BED)));
    assertTrue(allIds.contains(new JobIdMultisample(numberChunks, lim, JobType.FILTER)));
    assertTrue(allIds.contains(new JobIdMultisample(numberChunks, lim, JobType.OUT)));
  }

  private JobIdMultisample id(final int ch, final JobType ty) {
    return new JobIdMultisample(5, ch, ty);
  }

  public void testTo0() {
    final Dependencies<JobIdMultisample> dep = getDependencies();
    checkTo(dep, JobType.DANGLING, 0, id(0, JobType.BED), id(1, JobType.DANGLING), id(0, JobType.COMPLEX));
    checkTo(dep, JobType.INCR, 0, id(0, JobType.DANGLING), id(1, JobType.DANGLING), id(1, JobType.FILTER), id(0, JobType.FLUSH), id(1, JobType.FLUSH));
  }

  public void testTo1() {
    final Dependencies<JobIdMultisample> dep = getDependencies();
    checkTo(dep, JobType.INCR, 1, id(1, JobType.DANGLING), id(2, JobType.DANGLING), id(2, JobType.FILTER), id(1, JobType.FLUSH), id(2, JobType.FLUSH));
    checkTo(dep, JobType.DANGLING, 1, id(2, JobType.DANGLING), id(1, JobType.BED), id(1, JobType.COMPLEX));
    checkTo(dep, JobType.BED, 1, id(2, JobType.BED));
    checkTo(dep, JobType.COMPLEX, 1, id(1, JobType.FILTER), id(1, JobType.BED), id(1, JobType.FLUSH));
    checkTo(dep, JobType.FILTER, 1, id(1, JobType.OUT), id(2, JobType.FILTER));
    checkTo(dep, JobType.OUT, 1, id(2, JobType.OUT));
  }

  public void testToN() {
    final Dependencies<JobIdMultisample> dep = getDependencies();
    checkTo(dep, JobType.DANGLING, 5, id(5, JobType.BED), id(5, JobType.COMPLEX));
    checkTo(dep, JobType.BED, 5);
    checkTo(dep, JobType.COMPLEX, 5, id(5, JobType.FILTER), id(5, JobType.BED), id(5, JobType.FLUSH));
    checkTo(dep, JobType.FILTER, 5, id(5, JobType.OUT), id(6, JobType.FILTER));
    checkTo(dep, JobType.OUT, 5, id(6, JobType.OUT));
  }

  public void testToN1() {
    final Dependencies<JobIdMultisample> dep = getDependencies();
    checkTo(dep, JobType.FILTER, 6, id(6, JobType.OUT));
    checkTo(dep, JobType.OUT, 6);
  }

  private void checkTo(final Dependencies<JobIdMultisample> dep, final JobType ty, final int time, final JobIdMultisample... exp) {
    final JobIdMultisample id = id(time, ty);
    final Collection<JobIdMultisample> to = dep.to(id);
    assertEquals(exp.length, to.size());
    for (final JobIdMultisample exid : exp) {
      assertTrue(id + ">" + to.toString(), to.contains(exid));
    }
  }

  public void testFrom0() {
    final Dependencies<JobIdMultisample> dep = getDependencies();
    checkFrom(dep, JobType.INCR, 0);
    checkFrom(dep, JobType.DANGLING, 0, null, id(0, JobType.INCR), null);
    checkFrom(dep, JobType.BED, 0, null, id(0, JobType.DANGLING), id(0, JobType.COMPLEX));
  }

  public void testFrom1() {
    final Dependencies<JobIdMultisample> dep = getDependencies();
    checkFrom(dep, JobType.FILTER, 1, id(0, JobType.INCR), id(1, JobType.COMPLEX), null, id(1, JobType.FLUSH));
    checkFrom(dep, JobType.INCR, 1);
    checkFrom(dep, JobType.DANGLING, 1, id(0, JobType.INCR), id(1, JobType.INCR), id(0, JobType.DANGLING));
    checkFrom(dep, JobType.BED, 1, id(0, JobType.BED), id(1, JobType.DANGLING), id(1, JobType.COMPLEX));
    checkFrom(dep, JobType.COMPLEX, 1, id(1, JobType.DANGLING));
    checkFrom(dep, JobType.OUT, 1, null, id(1, JobType.FILTER));
  }

  public void testFrom2() {
    final Dependencies<JobIdMultisample> dep = getDependencies();
    checkFrom(dep, JobType.INCR, 2);
    checkFrom(dep, JobType.DANGLING, 2, id(1, JobType.INCR), id(2, JobType.INCR), id(1, JobType.DANGLING));
    checkFrom(dep, JobType.BED, 2, id(1, JobType.BED), id(2, JobType.DANGLING), id(2, JobType.COMPLEX));
    checkFrom(dep, JobType.COMPLEX, 2, id(2, JobType.DANGLING));
    checkFrom(dep, JobType.FILTER, 2, id(1, JobType.INCR), id(2, JobType.COMPLEX), id(1, JobType.FILTER), id(2, JobType.FLUSH));
    checkFrom(dep, JobType.OUT, 2, id(1, JobType.OUT), id(2, JobType.FILTER));
  }

  public void testFromN() {
    final Dependencies<JobIdMultisample> dep = getDependencies();
    checkFrom(dep, JobType.DANGLING, 5, id(4, JobType.INCR), null, id(4, JobType.DANGLING));
    checkFrom(dep, JobType.BED, 5, id(4, JobType.BED), id(5, JobType.DANGLING), id(5, JobType.COMPLEX));
    checkFrom(dep, JobType.COMPLEX, 5, id(5, JobType.DANGLING));
    checkFrom(dep, JobType.FILTER, 5, id(4, JobType.INCR), id(5, JobType.COMPLEX), id(4, JobType.FILTER), id(5, JobType.FLUSH));
    checkFrom(dep, JobType.OUT, 5, id(4, JobType.OUT), id(5, JobType.FILTER));
  }

  public void testFromN1() {
    final Dependencies<JobIdMultisample> dep = getDependencies();
    checkFrom(dep, JobType.FILTER, 6, null, null, id(5, JobType.FILTER), null);
    checkFrom(dep, JobType.OUT, 6, id(5, JobType.OUT), id(6, JobType.FILTER));
  }

  private void checkFrom(final Dependencies<JobIdMultisample> dep, final JobType ty, int time, final JobIdMultisample... exp) {
    final JobIdMultisample id = id(time, ty);
    final Collection<JobIdMultisample> from = dep.from(id);
    //System.err.println(from);
    assertEquals(exp.length, from.size());
    int i = 0;
    for (final Object frid : from) {
      assertTrue(id + ">" + from.toString(), eq(exp[i], frid));
      ++i;
    }
  }

  private boolean eq(final JobIdMultisample a, final Object b) {
    if (a == null) {
      return b == null;
    }
    return a.equals(b);
  }

  //check that all the ids are found when look ahead bounds being applied.
  protected Set<JobIdMultisample> allIdsLookahead(final DependenciesMultiSample dep) {
    final Set<JobIdMultisample> allIds = new HashSet<>();
    while (true) {
      JobIdMultisample nx = dep.next(getLookAhead(3, dep.delta()));
      //System.err.println("init:" + id);
      if (nx == null) {
        break;
      }
      final Set<JobIdMultisample> frontier0 = new HashSet<>();
      do {
        frontier0.add(nx);
        nx = dep.next(getLookAhead(3, dep.delta()));
      } while (nx != null);
      Set<JobIdMultisample> frontier = new HashSet<>(frontier0);

      do {
        allIds.addAll(frontier);
        final Set<JobIdMultisample> delta = new HashSet<>();
        for (final JobIdMultisample id : frontier) {
          final Collection<JobIdMultisample> to = dep.to(id);
          for (JobIdMultisample idTo : to) {
            if (idTo != null && !allIds.contains(idTo)) {
              //System.err.println(next + ">" + idTo);
              delta.add(idTo);
            }
          }
        }
        frontier = delta;
      } while (!frontier.isEmpty());
    }
    return allIds;
  }

  public void testFoo() throws IOException {
    //this test doesn't assert anything, but assembles a basic task which can be used to debug dependencies in DependenciesMultiSample
    final DependenciesMultiSample dep = new DependenciesMultiSample(3);
    final JobFactory<JobIdMultisample> factory = (id, arguments) -> new Job<JobIdMultisample>(id) {
      @Override
      protected Result run() throws IOException {
//            System.err.println("id = " + id);
        switch (id.type()) {
          case INCR:
            return new Result(new Complexities(new ArrayList<>(), "foo", 0, 100, 5, 5, new byte[0], true, null), 5);
          case DANGLING:
            return new Result(new Complexities(new ArrayList<>(), "foo", 0, 100, 5, 5, new byte[0], true, null));
          case COMPLEX:
            return new Result((Object) null);
          case FILTER:
            return new Result(null, null);
          case FLUSH:
          case BED:
            return new Result();
          case OUT:
            return null;
          default:
            throw new RuntimeException();
        }
      }
    };

    final MemoryPrintStream mps = new MemoryPrintStream();
    final EventList<JobIdMultisample> eventList = new EventListMultiSample<>();
    final Scheduler<JobIdMultisample> schRand = new SchedulerSynchronized<>(dep, factory, eventList, mps.printStream(), null, 5);
    final Executor<JobIdMultisample> exec = new ExecutorSequential<>(schRand);
    exec.run();
  }

}
