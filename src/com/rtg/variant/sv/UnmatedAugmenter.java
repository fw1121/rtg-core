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

package com.rtg.variant.sv;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import com.rtg.mode.DnaUtils;
import com.rtg.pairedend.InsertHelper;
import com.rtg.reference.ReferenceGenome;
import com.rtg.reference.ReferenceSequence;
import com.rtg.sam.ReadGroupUtils;
import com.rtg.sam.RecordIterator;
import com.rtg.sam.SamUtils;
import com.rtg.sam.SkipInvalidRecordsIterator;
import com.rtg.util.Constants;
import com.rtg.util.Environment;
import com.rtg.util.Pair;
import com.rtg.util.cli.CommandLine;
import com.rtg.util.diagnostic.NoTalkbackSlimException;
import com.rtg.util.intervals.RegionRestriction;
import com.rtg.util.io.FileUtils;
import com.rtg.util.machine.MachineType;
import com.rtg.util.machine.PairOrientation;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMFileWriter;
import htsjdk.samtools.SAMFileWriterFactory;
import htsjdk.samtools.SAMProgramRecord;
import htsjdk.samtools.SAMReadGroupRecord;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SamReader;

/**
 * Adds mating info to records of unique unmated hits with unique unmated hit for pair.
 * Ignores reads where one side does not have unique hit.
 */
public final class UnmatedAugmenter {

  /** Default filename for read group statistics */
  public static final String DEFAULT_RGSTATS_FILENAME = "rgstats.tsv";

  /**
   * Handles merging of multiple unmated augmenter runs.
   */
  public static final class Merger {

    private final List<UnmatedAugmenter> mAugmenters = Collections.synchronizedList(new ArrayList<UnmatedAugmenter>());
    private UnmatedAugmenter mBlended = null;

    /**
     * Creates an new UnmatedAugmenter and adds it to the collective.
     * @return a UnmatedAugmenter
     */
    public UnmatedAugmenter createUnmatedAugmenter() {
      final UnmatedAugmenter ua = new UnmatedAugmenter();
      mAugmenters.add(ua);
      return ua;
    }

    /**
     * Creates an UnmatedAugmenter that has the merged contents of the collective hash maps.
     * NOTE: This clears the collective as it merges them to allow garbage collection if no other references remain.
     * NOTE: Once this has been called, all future calls to it will return the same blended augmenter.
     * @return an UnmatedAugmenter
     */
    public synchronized UnmatedAugmenter blend() {
      if (mBlended == null) {
        mBlended = new UnmatedAugmenter();
        final Iterator<UnmatedAugmenter> iter = mAugmenters.iterator();
        while (iter.hasNext()) {
          final UnmatedAugmenter au = iter.next();
          mBlended.merge(au);
          au.mLeftSide.clear();
          au.mRightSide.clear();
          au.mRgMachineTypes.clear();
          iter.remove();
        }
      }
      return mBlended;
    }
  }

  // Contains minimal mate alignment information
  private static class CutRecord {
    final int mRefIndex;
    final int mAlignStart;
    final int mAlignEnd;
    final boolean mReverse;
    final int mAlignmentScore;

    CutRecord(SAMRecord r) {
      mRefIndex = r.getReferenceIndex();
      mAlignStart = r.getAlignmentStart();
      mAlignEnd = r.getAlignmentEnd();
      mReverse = r.getReadNegativeStrandFlag();
      mAlignmentScore = as(r);
    }

    private static int as(SAMRecord rec) {
      final Integer ii = rec.getIntegerAttribute(SamUtils.ATTRIBUTE_ALIGNMENT_SCORE);
      if (ii == null) {
        return Integer.MIN_VALUE;
      }
      return ii;
    }
  }

  private final HashMap<String, CutRecord> mLeftSide;
  private final HashMap<String, CutRecord> mRightSide;
  private final HashMap<String, MachineType> mRgMachineTypes;

  int mAugmentedUnmated = 0;
  int mAugmentedUnmapped = 0;

  /**
   * Constructor
   */
  public UnmatedAugmenter() {
    mLeftSide = new HashMap<>();
    mRightSide = new HashMap<>();
    mRgMachineTypes = new HashMap<>();
  }

  /**
   * Merge contents of another UnmatedAugmenter into this one.
   * @param other the other UnmatedAugmenter
   * @return a reference to this UnmatedAugmenter
   */
  private UnmatedAugmenter merge(UnmatedAugmenter other) {
    mLeftSide.putAll(other.mLeftSide);
    mRightSide.putAll(other.mRightSide);
    mRgMachineTypes.putAll(other.mRgMachineTypes);
    mAugmentedUnmapped += other.mAugmentedUnmapped;
    mAugmentedUnmated += other.mAugmentedUnmated;
    return this;
  }

  /**
   * Add record to internal hash maps used for augmenting if it is a unique unmated mapping without existing mate position information.
   * @param record SAM record to process.
   */
  public void addRecord(SAMRecord record) {
    if (isAugmentableUnmated(record)) {
      if (record.getFirstOfPairFlag()) {
        mLeftSide.put(record.getReadName(), new CutRecord(record));
      } else {
        mRightSide.put(record.getReadName(), new CutRecord(record));
      }
    }
  }

  private boolean isAugmentableUnmated(SAMRecord record) {
    if (!record.getReadPairedFlag() || record.getReadUnmappedFlag() || record.getProperPairFlag() || record.getMateReferenceIndex() != SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) {
      return false;
    }
    final Integer nh = SamUtils.getNHOrIH(record);
    return nh != null && nh == 1;
  }

  /**
   * Adds machine type from a read group header line.
   * @param srgr read group record to get machine type from.
   */
  public void addMachineType(SAMReadGroupRecord srgr) {
    final MachineType mt = ReadGroupUtils.platformToMachineType(srgr, true);
    if (mt == null) {
      throw new NoTalkbackSlimException("Read group " + srgr.getId() + " does not contain a recognized platform");
    } else if (mt.orientation() == null) {
      throw new NoTalkbackSlimException("Platform " + srgr.getPlatform() + " is not supported (unknown expected read orientation)");
    }
    final String rgId = srgr.getReadGroupId();
    mRgMachineTypes.put(rgId, mt);
  }

  SAMFileWriter makeWriter(SamReader reader, OutputStream outStream, boolean presorted) {
    final SAMFileHeader header = constructHeader(reader);
    final SAMFileWriter writer;
    if (reader.type() == SamReader.Type.BAM_TYPE) {
      writer = new SAMFileWriterFactory().makeBAMWriter(header, presorted, outStream, true);
    } else {
      writer = new SAMFileWriterFactory().makeSAMWriter(header, presorted, outStream);
    }
    return writer;
  }

  private SAMFileHeader constructHeader(SamReader reader) {
    final SAMFileHeader header = reader.getFileHeader();
    final SAMProgramRecord pg = new SAMProgramRecord(Constants.APPLICATION_NAME);
    pg.setProgramVersion(Environment.getVersion());
    if (CommandLine.getCommandLine() != null) {
      pg.setCommandLine(CommandLine.getCommandLine());
    } else {
      pg.setCommandLine("Internal");
    }
    SamUtils.addProgramRecord(header, pg);

    return header;
  }

  /**
   * Augments a file containing only unmated alignment. Not reentrant.
   * @param file SAM/BAM file to augment
   * @param output SAM/BAM file to output.
   * @param calc statistics calculator to feed records through.
   * @throws IOException when an IO error occurs
   */
  void augmentMixed(File file, File output, ReadGroupStatsCalculator calc) throws IOException {
    // First pass, collect mated stats and augmentable unmated info
    try (RecordIterator<SAMRecord> it = new SkipInvalidRecordsIterator(file.getPath(), SamUtils.makeSamReader(file), true)) {
      calc.setupReadGroups(it.header());
      while (it.hasNext()) {
        final SAMRecord record = it.next();
        if (record.getProperPairFlag()) {
          calc.addRecord(record);
        }
        addRecord(record);
      }
    }

    calc.calculate(); // Ensure insert size stats have been computed

    // Second pass, augment unmated records (and collect their stats) and unmapped records
    try (final SamReader reader = SamUtils.makeSamReader(file)) {
      try (RecordIterator<SAMRecord> it = new SkipInvalidRecordsIterator(file.getPath(), reader, true)) {
        try (SAMFileWriter writer = makeWriter(reader, FileUtils.createOutputStream(output, FileUtils.isGzipFilename(file) || reader.type() == SamReader.Type.BAM_TYPE), true)) {
          while (it.hasNext()) {
            final SAMRecord record = it.next();
            if (!record.getProperPairFlag()) {
              if (record.getReadUnmappedFlag()) {
                updateUnmappedRecord(record, calc, null);
              } else {
                updateUnmatedRecord(record);
                calc.addRecord(record);
              }
            }
            writer.addAlignment(record);
          }
        }
      }
    }
  }

  /**
   * Augments a file containing only unmated alignment. Not reentrant.
   * @param unmatedFile SAM/BAM file to augment
   * @param unmatedOutputFile SAM/BAM file to output.
   * @param calc statistics calculator to feed records through.
   * @throws IOException when an IO error occurs
   */
  public void augmentUnmated(File unmatedFile, File unmatedOutputFile, ReadGroupStatsCalculator calc) throws IOException {
    try (SamReader reader1 = SamUtils.makeSamReader(FileUtils.createFileInputStream(unmatedFile, false))) {
      calc.setupReadGroups(reader1.getFileHeader());
      try (RecordIterator<SAMRecord> it1 = new SkipInvalidRecordsIterator(unmatedFile.getPath(), reader1)) {
        while (it1.hasNext()) {
          addRecord(it1.next());
        }
      }
    }
    try (SamReader reader = SamUtils.makeSamReader(FileUtils.createFileInputStream(unmatedFile, false))) {
      try (SAMFileWriter writer = makeWriter(reader, FileUtils.createOutputStream(unmatedOutputFile, FileUtils.isGzipFilename(unmatedFile) || reader.type() == SamReader.Type.BAM_TYPE), true)) {
        //assume any warnings from input are reported the first time we processed so set Skipping iterator to silent mode.
        final RecordIterator<SAMRecord> it = new SkipInvalidRecordsIterator(unmatedFile.getPath(), reader, true);
        while (it.hasNext()) {
          final SAMRecord r = it.next();
          updateUnmatedRecord(r);
          calc.addRecord(r);
          writer.addAlignment(r);
        }
      }
    }
  }

  /**
   * Augments a file containing unmapped alignments. Not reentrant.
   * @param unmappedFile SAM/BAM file to augment
   * @param outputUnmappedFile SAM/BAM file to output.
   * @param calc statistics calculator to feed records through. <code>null</code> to ignore
   * @throws IOException when an IO error occurs
   */
  public void augmentUnmapped(File unmappedFile, File outputUnmappedFile, ReadGroupStatsCalculator calc) throws IOException {
    calc.calculate();
    try (SamReader reader = SamUtils.makeSamReader(FileUtils.createFileInputStream(unmappedFile, false))) {
      for (final SAMReadGroupRecord srgr : reader.getFileHeader().getReadGroups()) {
        addMachineType(srgr); // Not re-entrant
      }
      try (SAMFileWriter writer = makeWriter(reader, FileUtils.createOutputStream(outputUnmappedFile), false)) {
        final RecordIterator<SAMRecord> it = new SkipInvalidRecordsIterator(unmappedFile.getPath(), reader, true);
        while (it.hasNext()) {
          final SAMRecord r = it.next();
          if (r.getReadUnmappedFlag()) {
            updateUnmappedRecord(r, calc, null);
          }
          writer.addAlignment(r);
        }
      }
    }
  }

  /**
   * Update pair mapping information on an unmated SAM record based on the mapped location of it's mate..
   * @param record record to update.
   */
  public void updateUnmatedRecord(SAMRecord record) {
    if (isAugmentableUnmated(record)) {
      final CutRecord pair;
      if (record.getFirstOfPairFlag()) {
        pair = mRightSide.get(record.getReadName());
      } else {
        pair = mLeftSide.get(record.getReadName());
      }
      if (pair != null) { //NOTE: pair is only put in map if nh == 1
        mAugmentedUnmated++;
        record.setMateReferenceIndex(pair.mRefIndex);
        record.setMateAlignmentStart(pair.mAlignStart);
        record.setMateNegativeStrandFlag(pair.mReverse);
        record.setMateUnmappedFlag(false);
        final int as = pair.mAlignmentScore;
        if (as >= 0) {
          record.setAttribute(SamUtils.ATTRIBUTE_MATE_ALIGNMENT_SCORE, as);
        }
        record.setAttribute(SamUtils.ATTRIBUTE_MATE_END, pair.mAlignEnd);
        if (pair.mRefIndex == record.getReferenceIndex()) {
          final int tlen = InsertHelper.tlen(record.getFirstOfPairFlag(), record.getAlignmentStart(), record.getAlignmentEnd() - record.getAlignmentStart() + 1, pair.mAlignStart, pair.mAlignEnd - pair.mAlignStart + 1);
          record.setInferredInsertSize(tlen);
        } else {
          record.setInferredInsertSize(0);
        }
      }
    }
  }
  /**
   * Updates an unmapped record with pair mapping information.
   * @param record SAM record to update
   * @param calc a read group statistics calculator upon which calc.calculate() has been called.
   * @param referenceGenome reference genome for reference, or null if unknown or PAR region support is not required
   */
  public void updateUnmappedRecord(SAMRecord record, ReadGroupStatsCalculator calc, ReferenceGenome referenceGenome) {
    assert record.getReadUnmappedFlag();
    final CutRecord mate;
    if (record.getFirstOfPairFlag()) {
      mate = mRightSide.get(record.getReadName());
    } else {
      mate = mLeftSide.get(record.getReadName());
    }
    if (mate != null) { //other side was mapped NOTE: mate only in map if nh==1
      mAugmentedUnmapped++;
      record.setMateReferenceIndex(mate.mRefIndex);
      record.setMateAlignmentStart(mate.mAlignStart);
      record.setMateNegativeStrandFlag(mate.mReverse);
      record.setMateUnmappedFlag(false);
      record.setAttribute(SamUtils.ATTRIBUTE_MATE_END, mate.mAlignEnd);

      //make up some start positions etc for this read.
      final String rg = ReadGroupUtils.getReadGroup(record);
      final MachineType mt = mRgMachineTypes.get(rg);

      if (mt != null) {
        final PairOrientation mateOrientation;
        if (mate.mReverse) {
          mateOrientation = record.getFirstOfPairFlag() ? PairOrientation.R2 : PairOrientation.R1;
        } else {
          mateOrientation = record.getFirstOfPairFlag() ? PairOrientation.F2 : PairOrientation.F1;
        }
        final PairOrientation po = mt.orientation().getMateOrientation(mateOrientation);
        if (po != null) {
          record.setReferenceIndex(mate.mRefIndex);
          if (PairOrientation.F1.equals(po) || PairOrientation.F2.equals(po)) {
            record.setReadNegativeStrandFlag(false);
          } else {
            record.setReadNegativeStrandFlag(true);
            //need to RC the read and rev the qualities.

            final byte[] b = new byte[record.getReadBases().length];
            DnaUtils.reverseComplement(record.getReadBases(), b, b.length);
            record.setReadBases(b);

            final byte[] q = new byte[record.getBaseQualities().length];
            for (int i = 0; i < q.length; ++i) {
              q[q.length - 1 - i] = record.getBaseQualities()[i];
            }
            record.setBaseQualities(q);
          }

          final ReadGroupStats rgstats = calc.getStats(rg);
          final int thisFragmentLength;
          if (rgstats != null) {
            thisFragmentLength = (int) rgstats.fragmentMean();
          } else if (record.getReadGroup().getPredictedMedianInsertSize() != null) {
            thisFragmentLength = record.getReadGroup().getPredictedMedianInsertSize();
          } else {
            throw new NoTalkbackSlimException("Could not determine mean fragment size for placing unmapped records.");
          }
          int alignmentStart;
          if (mt.orientation().isMateUpstream(mateOrientation)) {
            alignmentStart = Math.max(1, mate.mAlignStart + thisFragmentLength - record.getReadLength());
          } else {
            alignmentStart = Math.max(1, mate.mAlignEnd - 1 - thisFragmentLength);
          }
          final int refLength = record.getHeader().getSequence(record.getReferenceIndex()).getSequenceLength();
          if (referenceGenome != null) {
            // If our projected position is in the duplicated PAR region, port to the canonical location
            final ReferenceSequence rs = referenceGenome.sequence(record.getReferenceName());
            if (rs.hasDuplicates()) {
              for (Pair<RegionRestriction, RegionRestriction> dup : rs.duplicates()) {
                if (dup.getB().contains(record.getReferenceName(), Math.min(refLength, alignmentStart))) {
                  alignmentStart = dup.getA().getStart() + (alignmentStart - dup.getB().getStart());
                  record.setReferenceName(dup.getA().getSequenceName());
                }
              }
            }
          }
          record.setAlignmentStart(Math.min(refLength, alignmentStart));
        }
      }
    }
  }

}
