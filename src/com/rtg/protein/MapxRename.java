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
package com.rtg.protein;

import static com.rtg.util.cli.CommonFlagCategories.INPUT_OUTPUT;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.util.ArrayList;

import com.rtg.launcher.AbstractCli;
import com.rtg.launcher.CommonFlags;
import com.rtg.reader.Names;
import com.rtg.reader.SdfId;
import com.rtg.reader.SequencesReader;
import com.rtg.reader.SequencesReaderFactory;
import com.rtg.util.StringUtils;
import com.rtg.util.cli.CFlags;
import com.rtg.util.cli.CommonFlagCategories;
import com.rtg.util.cli.Validator;
import com.rtg.util.diagnostic.Diagnostic;
import com.rtg.util.diagnostic.ErrorType;
import com.rtg.util.diagnostic.NoTalkbackSlimException;
import com.rtg.util.intervals.LongRange;
import com.rtg.util.io.FileUtils;

/**
 * Restore original read names in <code>mapx</code> file
 */
public class MapxRename extends AbstractCli {

  private static final String MODULE_NAME = "mapxrename";
  private static final String SDF_READS = "input";
  private static final String RENAME = "_rename";
  private static final String OUTPUT = "output";

  private static class MapXRenameValidator implements Validator {

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
      final String leftFile = flags.getFlag(SDF_READS).getValue().toString();
      final File left = new File(leftFile);
      if (!left.exists()) {
        Diagnostic.error(ErrorType.INFO_ERROR, "The specified SDF, \"" + left.getPath() + "\", does not exist.");
        return false;
      }
      if (!left.isDirectory()) {
        Diagnostic.error(ErrorType.INFO_ERROR, "The specified file, \"" + left.getPath() + "\", is not an SDF.");
        return false;
      }
      return true;
    }
  }

  @Override
  public String moduleName() {
    return MODULE_NAME;
  }

  @Override
  public String description() {
    return "rename read id to read name in mapx output files";
  }

  @Override
  protected void initFlags() {
    mFlags.setDescription("Replaces read identifiers (read-id field) in a mapx file generated by the RTG mapx command with the sequence identifiers from the original sequence file.");
    CommonFlagCategories.setCategories(mFlags);
    mFlags.registerRequired(File.class, CommonFlags.FILE, "input mapx file").setCategory(INPUT_OUTPUT);
    mFlags.registerRequired('i', SDF_READS, File.class, CommonFlags.SDF, "SDF for the reads in the mapx file").setCategory(INPUT_OUTPUT);
    mFlags.registerOptional('o', OUTPUT, File.class, CommonFlags.FILE, "renamed output mapx file").setCategory(INPUT_OUTPUT);
    mFlags.setValidator(new MapXRenameValidator());
  }

  @Override
  protected int mainExec(OutputStream out, PrintStream err) throws IOException {
    final CFlags flags = mFlags;
    final File infile = (File) flags.getAnonymousValue(0);
    final File sdfReads = (File) flags.getValue(SDF_READS);
    final File outfile;
    if (flags.isSet(OUTPUT)) {
      outfile = (File) flags.getValue(OUTPUT);
    } else {
      String infileName = infile.getAbsolutePath();
      boolean gzipped = false;
      if (FileUtils.isGzipFilename(infileName)) {
        gzipped = true;
        infileName = infileName.substring(0, infileName.length() - 3);
      }
      String outfileName;
      if (infileName.endsWith(".tsv")) {
        outfileName = infileName.substring(0, infileName.length() - 4) + RENAME + ".tsv";

      } else {
        outfileName = infileName + RENAME;
      }
      if (gzipped) {
        outfileName = outfileName + FileUtils.GZ_SUFFIX;
      }
      outfile = new File(outfileName);
    }

    try (SequencesReader sr = SequencesReaderFactory.createDefaultSequencesReaderCheckEmpty(sdfReads)) {
      renameFile(infile, outfile, new Names(sdfReads, LongRange.NONE), sr.getSdfId());
    }
    out.write(("Rename complete." + StringUtils.LS).getBytes());
    return 0;
  }

  // Reads header lines stopping once we find the column headings or EOF
  private static ArrayList<String> readHeader(BufferedReader read) throws IOException {
    final ArrayList<String> header = new ArrayList<>();
    String line;
    while ((line = read.readLine()) != null) {
      header.add(line);
      if ((line.length() > 0) && !line.startsWith("#")) {
        throw new IOException("Unexpected line encountered in header: " + line);
      } else if (line.startsWith(ProteinOutputProcessor.MAPX_OUTPUT_VERSION_HEADER)) {
        // Check version number
        final String[] parts = line.split("\t");
        if ((parts.length != 2) || !parts[1].equals(ProteinOutputProcessor.MAPX_OUTPUT_VERSION)) {
          throw new IOException("Unsupported mapx output format: " + line);
        }
      } else if ((line.startsWith("#")
          && line.contains(ProteinOutputProcessor.HEADER_COL_NAME_READNAME))
          || line.equals(ProteinOutputProcessor.UNMAPPED_HEADER_READ_NAMES)) {
        throw new NoTalkbackSlimException("This file has already been renamed");
      } else if ((line.startsWith("#")
          && line.contains(ProteinOutputProcessor.HEADER_COL_NAME_READID))
          || line.equals(ProteinOutputProcessor.UNMAPPED_HEADER)) {
        return header;
      }
    }
    throw new IOException("Unexpected end of file while reading header");
  }

  private static void writeModifiedHeader(BufferedWriter writer, ArrayList<String> header) throws IOException {
    for (int i = 0; i < header.size() - 1; ++i) {
      writer.write(header.get(i));
      writer.newLine();
    }
    writer.write(header.get(header.size() - 1).replace("read-id", "read-name"));
    writer.newLine();
  }

  private static SdfId getSdfId(ArrayList<String> header) {
    for (final String line : header) {
      if (line.startsWith(ProteinOutputProcessor.MAPX_READ_SDF_ID_HEADER + "\t")) {
        try {
          return new SdfId(line.substring(line.indexOf('\t') + 1));
        } catch (final NumberFormatException e) {
          throw new NoTalkbackSlimException("Malformed READ-SDF-ID from mapx header." + e.getMessage());
        }
      }
    }
    return new SdfId(0L);
  }

  private static void checkSdfId(ArrayList<String> header, SdfId sdfId) {
    final SdfId myGuid = getSdfId(header);
    if (!myGuid.check(sdfId)) {
      throw new NoTalkbackSlimException("SDF-ID of given SDF does not match SDF used during mapping.");
    } else if (!myGuid.available()) {
      Diagnostic.warning("No READ-SDF-ID found in mapx header, unable to verify read-id correctness.");
    }
  }

  private static void renameFile(final File resultsFile, final File renamedFile, Names names, SdfId sdfId) throws IOException {
    int idColumn = -1;
    try (BufferedReader read = new BufferedReader(new InputStreamReader(FileUtils.createInputStream(resultsFile, false)))) {
      final OutputStream baseOutStream = FileUtils.createOutputStream(renamedFile);
      try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(baseOutStream))) {
        final ArrayList<String> header = readHeader(read);

        // Set id column, num cols from the header line
        final String colHeader = header.get(header.size() - 1).substring(1);
        final String[] colHeadings = colHeader.split("\t");
        for (int i = 0; i < colHeadings.length; ++i) {
          if (colHeadings[i].equals("read-id")) {
            idColumn = i;
            break;
          }
        }
        if (idColumn == -1) {
          throw new IOException("Unrecognized column header line: " + colHeader);
        }

        checkSdfId(header, sdfId);

        writeModifiedHeader(writer, header);

        String line;
        // Read/convert header
        while ((line = read.readLine()) != null) {
          if (line.length() > 0) {
            if (line.charAt(0) == '#') {
              //this is special case happens only for topn output filter
              if (line.startsWith("#read ")) {
                final String[] parts = line.split(" ");
                if (parts.length == 8) {
                  // #read 3 had 6 results with score-indel 4
                  line = replaceIdWithName(parts, 1, ' ', names);
                }
              }
            } else {
              // Expected column format:
              // #template-name\tframe\tread-id\ttemplate-start\tscore\tscore-indel
              final String[] parts = line.split("\t");
              // Following is sanity check, avoid changing lines with funny number of fields
              if (parts.length > idColumn) {
                line = replaceIdWithName(parts, idColumn, '\t', names);
              }
            }
          }
          writer.write(line);
          writer.newLine();
        }
      }
    }
  }

  private static String replaceIdWithName(final String[] parts, final int id, final char sep, Names names) {
    final String line;
    final int readId = Integer.parseInt(parts[id]);
    parts[id] = names.name(readId);
    final StringBuilder sb = new StringBuilder();
    for (int k = 0; k < parts.length; ++k) {
      if (k != 0) {
        sb.append(sep);
      }
      sb.append(parts[k]);
    }
    line = sb.toString();
    return line;
  }
}
