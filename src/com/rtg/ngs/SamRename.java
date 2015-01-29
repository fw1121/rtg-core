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

import static com.rtg.util.cli.CommonFlagCategories.INPUT_OUTPUT;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;

import com.rtg.launcher.AbstractCli;
import com.rtg.launcher.CommonFlags;
import com.rtg.reader.PrereadNames;
import com.rtg.reader.ReaderUtils;
import com.rtg.reader.SdfId;
import com.rtg.reader.SequencesReader;
import com.rtg.reader.SequencesReaderFactory;
import com.rtg.sam.BamIndexer;
import com.rtg.sam.SamBamConstants;
import com.rtg.sam.SamUtils;
import com.rtg.tabix.TabixIndexer;
import com.rtg.tabix.UnindexableDataException;
import com.rtg.util.intervals.LongRange;
import com.rtg.util.StringUtils;
import com.rtg.util.cli.CFlags;
import com.rtg.util.cli.CommonFlagCategories;
import com.rtg.util.cli.Validator;
import com.rtg.util.diagnostic.Diagnostic;
import com.rtg.util.diagnostic.ErrorType;
import com.rtg.util.diagnostic.NoTalkbackSlimException;
import com.rtg.util.diagnostic.WarningType;
import com.rtg.util.io.FileUtils;

import net.sf.samtools.SAMFileHeader;
import net.sf.samtools.SAMFileReader;
import net.sf.samtools.SAMFileWriter;
import net.sf.samtools.SAMFileWriterFactory;
import net.sf.samtools.SAMRecord;

/**
 * Restore original read names in SAM file
 */
public class SamRename extends AbstractCli {

  private static final String MODULE_NAME = "samrename";
  private static final String RENAME = "_rename";
  static final String OUTPUT = "output";

  private static class SamRenameValidator implements Validator {

    /**
     * Checks if flags are good
     * @param flags the flags
     * @return true if good
     */
    @Override
    public boolean isValid(final CFlags flags) {
      final File infile = (File) flags.getAnonymousValue(0);
      if (!infile.exists()) {
        flags.setParseMessage("Input file \"" + infile + "\" doesn't exist.");
        return false;
      } else if (infile.isDirectory()) {
        flags.setParseMessage("Input file \"" + infile + "\" is a directory.");
        return false;
      } else if (!flags.isSet(OUTPUT) && !infile.isFile()) {
        flags.setParseMessage("Must set output directory when using process substitution as input.");
        return false;
      }
      return CommonFlags.validateReads(flags, true);
    }
  }

  /**
   * set up a flags object for this module
   * @param flags the flags to set up
   */
  public void initFlags(CFlags flags) {
    flags.registerExtendedHelp();
    flags.setDescription("Replaces read identifiers (QNAME field) in a SAM file generated by the RTG map command with the sequence identifiers from the original sequence file.");
    CommonFlagCategories.setCategories(flags);
    flags.registerRequired(File.class, "FILE", "input SAM file").setCategory(INPUT_OUTPUT);
    flags.registerRequired('i', CommonFlags.READS_FLAG, File.class, "SDF", "SDF for the reads in the SAM file").setCategory(INPUT_OUTPUT);
    flags.registerOptional('o', OUTPUT, File.class, "FILE", "renamed output SAM file").setCategory(INPUT_OUTPUT);
    CommonFlags.initNoGzip(flags);
    CommonFlags.initIndexFlags(flags);
    CommonFlags.initReadRange(flags);
    flags.setValidator(new SamRenameValidator());
  }

  /**
   * Renames read ids a SAM file
   * @param sdfReads SDF file containing reads
   * @param input File to rename
   * @param output destination SAM file
   * @param region the read range to rename reads from
   * @throws IOException When an IO Error occurs
   */
  public void renameSam(File sdfReads, File input, File output, LongRange region) throws IOException {
    try {
      renameSamFile(sdfReads, input, output, region);
    } catch (final RuntimeException e) {
      if (output.isFile() && !output.delete()) {
        Diagnostic.warning(WarningType.INFO_WARNING, "Could not delete file \"" + output.getAbsolutePath() + "\"");
      }
      throw e;
    }
  }

  /**
   * main methods
   * @param args parameter arguments
   */
  public static void main(final String[] args) {
    new SamRename().mainExit(args);
  }

  @Override
  protected void initFlags() {
    initFlags(mFlags);
  }

  /**
   * @return current name of the module
   */
  @Override
  public String moduleName() {
    return MODULE_NAME;
  }

  @Override
  protected int mainExec(OutputStream out, PrintStream err) throws IOException {
    final File infile = (File) mFlags.getAnonymousValue(0);
    final File sdfReads = (File) mFlags.getValue(CommonFlags.READS_FLAG);
    final boolean gzipOutput = !mFlags.isSet(CommonFlags.NO_GZIP);
    final boolean index = !mFlags.isSet(CommonFlags.NO_INDEX);
    final File outfile;
    if (mFlags.isSet(OUTPUT)) {
      String fileName = ((File) mFlags.getValue(OUTPUT)).getName();
      if (!FileUtils.isGzipFilename(fileName) && !fileName.endsWith(".bam") && gzipOutput) {
        fileName = fileName + FileUtils.GZ_SUFFIX;
      }
      outfile = new File(((File) mFlags.getValue(OUTPUT)).getParentFile(), fileName);
    } else {
      String infileName = infile.getPath();
      if (FileUtils.isGzipFilename(infileName)) {
        infileName = infileName.substring(0, infileName.length() - 3);
      }
      String outfileName;
      if (infileName.endsWith(".sam")) {
        outfileName = infileName.substring(0, infileName.length() - 4) + RENAME + ".sam";
      } else if (infileName.endsWith(".bam")) {
        outfileName = infileName.substring(0, infileName.length() - 4) + RENAME + ".bam";
      } else {
        outfileName = infileName + RENAME;
      }
      if (gzipOutput && !outfileName.endsWith(".bam")) {
        outfileName = outfileName + FileUtils.GZ_SUFFIX;
      }
      outfile = new File(outfileName);
    }
    final LongRange region = CommonFlags.getReaderRestriction(mFlags);

    renameSam(sdfReads, infile, outfile, region);
    if (index) {
      try {
        if (outfile.getName().endsWith(".bam")) {
          final File indexFile =  new File(outfile.getParentFile(), outfile.getName() + BamIndexer.BAM_INDEX_EXTENSION);
          BamIndexer.saveBamIndex(outfile, indexFile);
        } else if (FileUtils.isGzipFilename(outfile)) {
          final File indexFile =  new File(outfile.getParentFile(), outfile.getName() + TabixIndexer.TABIX_EXTENSION);
          new TabixIndexer(outfile, indexFile).saveSamIndex();
        }
      } catch (final UnindexableDataException e) {
        Diagnostic.warning("Cannot produce index for: " + outfile + ": " + e.getMessage());
      }
    }
    out.write(("Rename complete." + StringUtils.LS).getBytes());
    return 0;
  }

  //for test access
  @Override
  protected boolean handleFlags(String[] args, Appendable out, Appendable err) {
    return super.handleFlags(args, out, err);
  }

  /**
   * Rename a SAM input file
   *
   * @param sdfReads SDF for read names
   * @param inFile input SAM file
   * @param outFile output renamed SAM file
   * @param region the read range to rename reads from
   * @throws IOException if IO error
   */
  public static void renameSamFile(final File sdfReads, final File inFile, final File outFile, LongRange region) throws IOException {
    final File sortTempDir = outFile.getParentFile();
    final File aDir;
    if (ReaderUtils.isPairedEndDirectory(sdfReads)) {
      aDir = ReaderUtils.getLeftEnd(sdfReads);
    } else {
      aDir = sdfReads;
    }
    final LongRange reg2 = SequencesReaderFactory.resolveRange(aDir, region);
    final Renamer rename;
    if (ReaderUtils.isPairedEndDirectory(sdfReads)) {
      try (SequencesReader sr = SequencesReaderFactory.createDefaultSequencesReaderCheckEmpty(ReaderUtils.getLeftEnd(sdfReads))) {
        if (!sr.hasNames()) {
          throw new NoTalkbackSlimException("SDF does not contain names, cannot rename");
        }
        rename = new Renamer(new PrereadNames(ReaderUtils.getLeftEnd(sdfReads), reg2), new PrereadNames(ReaderUtils.getRightEnd(sdfReads), reg2), sr.getSdfId(), reg2);
      }
    } else {
      try (SequencesReader sr = SequencesReaderFactory.createDefaultSequencesReaderCheckEmpty(sdfReads)) {
        if (!sr.hasNames()) {
          throw new NoTalkbackSlimException("SDF does not contain names, cannot rename");
        }
        rename = new Renamer(new PrereadNames(sdfReads, reg2), sr.getSdfId(), reg2);
      }
    }
    rename.renameSamFile(sortTempDir, outFile, inFile);
  }

  private static class Renamer {

    private final PrereadNames mNames;
    private final PrereadNames mNamesLeft;
    private final PrereadNames mNamesRight;
    private final SdfId mSdfId;
    private final LongRange mRegion;

    Renamer(PrereadNames names, SdfId sdfId, LongRange region) {
      mNames = names;
      mNamesLeft = null;
      mNamesRight = null;
      mSdfId = sdfId;
      mRegion = region;
    }

    Renamer(PrereadNames namesLeft, PrereadNames namesRight, SdfId sdfId, LongRange region) {
      mNames = null;
      mNamesLeft = namesLeft;
      mNamesRight = namesRight;
      mSdfId = sdfId;
      mRegion = region;
    }


    void renameSamFile(final File sortTempDir, final File outFile, final File resultsFile) throws IOException {
      final int offset = mRegion.getStart() > 0 ? (int) mRegion.getStart() : 0;
      try (InputStream bis = FileUtils.createFileInputStream(resultsFile, false)) {
        final SAMFileReader read = new SAMFileReader(bis);
        final SAMFileHeader header = read.getFileHeader();
        SamUtils.checkReadsGuid(header, mSdfId);
        SamUtils.addProgramRecord(header);
        try (OutputStream baseOutStream = FileUtils.createOutputStream(outFile, FileUtils.isGzipFilename(outFile), false)) {
          final SAMFileWriterFactory fact = new SAMFileWriterFactory();
          fact.setMaxRecordsInRam(5000000);
          fact.setTempDirectory(sortTempDir);
          try (SAMFileWriter writer = outFile.getName().endsWith(".bam")
            ? fact.makeBAMWriter(header, true, baseOutStream, false)
            : fact.makeSAMWriter(header, true, baseOutStream)) {
            for (SAMRecord line : read) {
              final String nameInNumbers = line.getReadName();
              final int samIndex = Integer.parseInt(nameInNumbers);
              final int nameIndex = samIndex - offset;
              if (samIndex < 0) {
                throw new NoTalkbackSlimException(ErrorType.SAM_BAD_FORMAT, resultsFile.getAbsolutePath(), "The read ids are negative.");
              } else if (nameIndex < 0) {
                throw new NoTalkbackSlimException(ErrorType.INFO_ERROR, "SAM file contains read ids lower than the read range given.");
              } else if (mRegion.getEnd() > 0 && samIndex >= mRegion.getEnd()) {
                throw new NoTalkbackSlimException(ErrorType.INFO_ERROR, "SAM file contains read ids higher than the read range given.");
              }
              final String name;
              if (mNames != null) {
                if (line.getReadPairedFlag()) {
                  Diagnostic.warning("Read comes from paired data but only one SDF passed in");
                }
                if (nameIndex >= mNames.length()) {
                  throw new NoTalkbackSlimException(ErrorType.INFO_ERROR, "SAM file contains read ids higher than the reads SDF contains.");
                }
                name = mNames.name(nameIndex);
              } else {
                if (!line.getReadPairedFlag()) {
                  Diagnostic.warning("Read comes from non-paired data but only two SDF passed in");
                }
                if (line.getFirstOfPairFlag()) {
                  if (nameIndex >= mNamesLeft.length()) {
                    throw new NoTalkbackSlimException(ErrorType.INFO_ERROR, "SAM file contains read ids higher than the reads SDF contains.");
                  }
                  name = mNamesLeft.name(nameIndex);
                } else {
                  if (nameIndex >= mNamesRight.length()) {
                    throw new NoTalkbackSlimException(ErrorType.INFO_ERROR, "SAM file contains read ids higher than the reads SDF contains.");
                  }
                  name = mNamesRight.name(nameIndex);
                }
              }
              // set names of read and mate of read
              line.setReadName(SamUtils.samReadName(name, (line.getFlags() & SamBamConstants.SAM_READ_IS_PAIRED) != 0));
              writer.addAlignment(line);
              // reset progress
            }
          } catch (final NumberFormatException e) {
            throw new NoTalkbackSlimException(ErrorType.SAM_BAD_FORMAT, resultsFile.getAbsolutePath(), "The read ids are not numbers.");
          }
        }
      }
    }
  }
}
