/*
 * Copyright 2016-2026 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.pipeline.benchmark;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Generates <code>records:2</code> XML of the same shape as
 * <code>stroom-pipeline/src/test/resources/TestXMLTransformer/XML-EVENTS.nxml</code>, but with an
 * arbitrary number of records.
 * <p>
 * Every record is unique. Record <code>i</code> (zero based) carries:
 * <ul>
 *     <li>a Date/Time of {@link #BASE_TIME} plus <code>i</code> seconds, so no two records share a
 *     timestamp;</li>
 *     <li><code>LineNo</code> of <code>i + 1</code>;</li>
 *     <li><code>User</code> of <code>user{i + 1}</code>;</li>
 *     <li><code>Message</code> of <code>Some message {i + 1}</code>.</li>
 * </ul>
 * The timestamp matters most. It is the one field the XSLT genuinely transforms rather than copies
 * (via <code>stroom:format-date</code>), so a distinct timestamp per record lets a verifier prove
 * the transform ran for every record instead of just proving that bytes were copied through.
 * <p>
 * The original ten record file spaces records a minute apart. This spaces them a second apart so
 * that a million records still land in a comprehensible range (2010-01-01 to 2010-01-12); the
 * generated file is otherwise byte for byte the same shape.
 */
public final class XMLEventsDataGenerator {

    /**
     * 2010-01-01T00:00:00Z, matching the fixed date used by the original ten record test file.
     */
    public static final Instant BASE_TIME = Instant.parse("2010-01-01T00:00:00Z");

    private static final int MAX_RECORDS = 100_000_000;

    private static final String HEADER = """
            <?xml version="1.0" encoding="UTF-8"?>
            <records xmlns="records:2" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"\
             xsi:schemaLocation="records:2 file://records-v2.0.xsd" version="2.0">
            """;
    private static final String FOOTER = "</records>\n";

    private static final int WRITE_BUFFER_SIZE = 1024 * 256;

    private XMLEventsDataGenerator() {
        // Utility class.
    }

    /**
     * Returns the canonical file name for a given record count, e.g. <code>XML-EVENTS-1000000.nxml</code>.
     */
    public static String fileName(final int recordCount) {
        return "XML-EVENTS-" + recordCount + ".nxml";
    }

    /**
     * Generates the file if it is not already present, and returns its path.
     * <p>
     * Generation writes to a temporary sibling and then atomically moves it into place, so a file
     * present at <code>path</code> is always complete. That makes generating the larger files a
     * one-off cost shared by every subsequent run, including forked JMH JVMs.
     */
    public static Path generateIfNecessary(final Path path, final int recordCount) throws IOException {
        if (Files.isRegularFile(path)) {
            return path;
        }

        Files.createDirectories(path.getParent());
        final Path partial = path.resolveSibling(path.getFileName() + ".partial");
        generate(partial, recordCount);
        Files.move(partial, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return path;
    }

    /**
     * Unconditionally writes <code>recordCount</code> records to <code>path</code>.
     */
    public static void generate(final Path path, final int recordCount) throws IOException {
        if (recordCount < 1 || recordCount > MAX_RECORDS) {
            throw new IllegalArgumentException(
                    "recordCount must be between 1 and " + MAX_RECORDS + " but was " + recordCount);
        }

        try (final Writer writer = new BufferedWriter(
                Files.newBufferedWriter(path, StandardCharsets.UTF_8), WRITE_BUFFER_SIZE)) {
            writer.write(HEADER);
            final StringBuilder sb = new StringBuilder(256);
            for (int i = 0; i < recordCount; i++) {
                sb.setLength(0);
                appendRecord(sb, i);
                writer.write(sb.toString());
            }
            writer.write(FOOTER);
        }
    }

    /**
     * Appends the single record line for zero based record index <code>i</code>.
     */
    public static void appendRecord(final StringBuilder sb, final int i) {
        final LocalDateTime dateTime = LocalDateTime.ofInstant(BASE_TIME.plusSeconds(i), ZoneOffset.UTC);
        final long recordNo = i + 1L;

        sb.append("<record><data name=\"Date\" value=\"");
        appendPadded(sb, dateTime.getDayOfMonth(), 2);
        sb.append('/');
        appendPadded(sb, dateTime.getMonthValue(), 2);
        sb.append('/');
        appendPadded(sb, dateTime.getYear(), 4);
        sb.append("\"/><data name=\"Time\" value=\"");
        appendPadded(sb, dateTime.getHour(), 2);
        sb.append(':');
        appendPadded(sb, dateTime.getMinute(), 2);
        sb.append(':');
        appendPadded(sb, dateTime.getSecond(), 2);
        sb.append("\"/><data name=\"FileNo\" value=\"1\"/><data name=\"LineNo\" value=\"");
        sb.append(recordNo);
        sb.append("\"/><data name=\"User\" value=\"user");
        sb.append(recordNo);
        sb.append("\"/><data name=\"Message\" value=\"Some message ");
        sb.append(recordNo);
        sb.append("\"/></record>\n");
    }

    private static void appendPadded(final StringBuilder sb, final int value, final int width) {
        final String s = Integer.toString(value);
        for (int i = s.length(); i < width; i++) {
            sb.append('0');
        }
        sb.append(s);
    }

    /**
     * Produces the data file without running a test, e.g.
     * <code>XMLEventsDataGenerator /path/to/XML-EVENTS-1000000.nxml 1000000</code>.
     */
    public static void main(final String[] args) throws IOException {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: XMLEventsDataGenerator <path> <recordCount>");
        }
        final Path path = Path.of(args[0]);
        final int recordCount = Integer.parseInt(args[1]);
        final long start = System.currentTimeMillis();
        generate(path, recordCount);
        System.out.printf("Wrote %,d records (%,d bytes) to %s in %,dms%n",
                recordCount, Files.size(path), path, System.currentTimeMillis() - start);
    }
}
