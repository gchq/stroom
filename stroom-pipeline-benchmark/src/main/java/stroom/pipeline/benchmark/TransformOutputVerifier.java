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

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.BitSet;
import java.util.HexFormat;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Inspects the <code>event-logging:3</code> output of {@link XmlTransformHarness} and reports what
 * it found, in a single streaming pass so that it copes with multi-gigabyte files.
 * <p>
 * Although the {@code SplitFilter} makes the {@code XSLTFilter} run once per group of
 * {@code splitCount} records, the {@code XMLWriter} folds those groups back into one document: the
 * output carries a single XML declaration and a single {@code Events} root however the input was
 * split. The scan therefore expects {@link Result#rootCount()} of 1, and
 * {@link Result#fileDigest()} is directly comparable between runs that differ only in split count.
 * <p>
 * {@link Result#eventsDigest()} covers just the <code>&lt;Event&gt; … &lt;/Event&gt;</code> regions,
 * ignoring the surrounding framing. It is the more specific of the two: were the framing ever to
 * change, it would still say whether the translated records themselves were affected.
 */
public final class TransformOutputVerifier {

    private static final byte[] EVENT_OPEN = "<Event>".getBytes(StandardCharsets.UTF_8);
    private static final byte[] EVENT_CLOSE = "</Event>".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ROOT_OPEN = "<Events ".getBytes(StandardCharsets.UTF_8);
    private static final byte[] LINE_NO = "Name=\"LineNo\" Value=\"".getBytes(StandardCharsets.UTF_8);

    private static final int READ_BUFFER_SIZE = 1024 * 1024;

    private static final String XML_DECLARATION = "<?xml ";
    private static final long MAX_PARSE_BYTES = 64L * 1024 * 1024;

    private TransformOutputVerifier() {
        // Utility class.
    }

    /**
     * Scans <code>outputFile</code>, expecting <code>expectedRecords</code> events.
     *
     * @param expectedRecords used to size the LineNo bit set and to range check LineNo values. A
     *                        mismatch is reported in the {@link Result} rather than thrown, so that
     *                        callers can assert on it and see the whole picture on failure.
     */
    public static Result verify(final Path outputFile, final int expectedRecords) throws IOException {
        final MessageDigest eventsDigest = newDigest();
        final MessageDigest fileDigest = newDigest();
        final BitSet lineNosSeen = new BitSet(expectedRecords + 1);

        long eventCount = 0;
        long rootCount = 0;
        long duplicateLineNos = 0;
        long outOfRangeLineNos = 0;
        String firstEvent = null;
        // Held as bytes so we do not build a String per event, which would dominate the scan.
        byte[] lastEventBytes = null;

        // Match progress for each pattern we care about.
        int openMatch = 0;
        int closeMatch = 0;
        int rootMatch = 0;
        int lineNoMatch = 0;

        boolean inEvent = false;
        boolean readingLineNo = false;
        long lineNoValue = 0;

        // Each event is ~800 bytes, so buffering one at a time to digest it is cheap and keeps the
        // scan free of chunk-boundary special cases.
        final ByteArrayOutputStream eventBuffer = new ByteArrayOutputStream(2048);
        final byte[] buffer = new byte[READ_BUFFER_SIZE];

        try (final InputStream in = Files.newInputStream(outputFile)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                fileDigest.update(buffer, 0, read);

                for (int i = 0; i < read; i++) {
                    final byte b = buffer[i];

                    if (inEvent) {
                        eventBuffer.write(b);

                        if (readingLineNo) {
                            if (b >= '0' && b <= '9') {
                                lineNoValue = (lineNoValue * 10) + (b - '0');
                            } else {
                                readingLineNo = false;
                                if (lineNoValue < 1 || lineNoValue > expectedRecords) {
                                    outOfRangeLineNos++;
                                } else if (lineNosSeen.get((int) lineNoValue)) {
                                    duplicateLineNos++;
                                } else {
                                    lineNosSeen.set((int) lineNoValue);
                                }
                            }
                        } else {
                            lineNoMatch = advance(lineNoMatch, b, LINE_NO);
                            if (lineNoMatch == LINE_NO.length) {
                                lineNoMatch = 0;
                                readingLineNo = true;
                                lineNoValue = 0;
                            }
                        }

                        closeMatch = advance(closeMatch, b, EVENT_CLOSE);
                        if (closeMatch == EVENT_CLOSE.length) {
                            closeMatch = 0;
                            inEvent = false;
                            eventCount++;

                            final byte[] event = eventBuffer.toByteArray();
                            eventsDigest.update(event);
                            if (firstEvent == null) {
                                firstEvent = new String(event, StandardCharsets.UTF_8);
                            }
                            lastEventBytes = event;
                            eventBuffer.reset();
                        }
                    } else {
                        rootMatch = advance(rootMatch, b, ROOT_OPEN);
                        if (rootMatch == ROOT_OPEN.length) {
                            rootMatch = 0;
                            rootCount++;
                        }

                        openMatch = advance(openMatch, b, EVENT_OPEN);
                        if (openMatch == EVENT_OPEN.length) {
                            openMatch = 0;
                            inEvent = true;
                            eventBuffer.reset();
                            eventBuffer.write(EVENT_OPEN, 0, EVENT_OPEN.length);
                        }
                    }
                }
            }
        }

        final String lastEvent = lastEventBytes == null
                ? null
                : new String(lastEventBytes, StandardCharsets.UTF_8);

        return new Result(
                Files.size(outputFile),
                eventCount,
                rootCount,
                lineNosSeen.cardinality(),
                duplicateLineNos,
                outOfRangeLineNos,
                inEvent,
                firstEvent,
                lastEvent,
                HexFormat.of().formatHex(eventsDigest.digest()),
                HexFormat.of().formatHex(fileDigest.digest()));
    }

    /**
     * Parses <code>outputFile</code> with a real XML parser and returns how many documents it held,
     * throwing if any of them is not well formed.
     * <p>
     * This reads the whole file into memory, so it is only appropriate for the small scale checks.
     * It exists because the streaming scan proves the events are all present and unchanged across
     * split counts, but says nothing about whether what the {@code XMLWriter} framed them in is
     * actually valid XML. It splits on the XML declaration so that it still reports honestly if a
     * change ever made the writer emit more than one document.
     */
    public static int parseAllDocuments(final Path outputFile) throws IOException, SAXException,
            ParserConfigurationException {
        final long size = Files.size(outputFile);
        if (size > MAX_PARSE_BYTES) {
            throw new IllegalArgumentException("Refusing to parse " + size
                                               + " bytes in memory; this check is for small inputs only");
        }

        final String content = Files.readString(outputFile, StandardCharsets.UTF_8);
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        final DocumentBuilder documentBuilder = factory.newDocumentBuilder();

        int from = content.indexOf(XML_DECLARATION);
        if (from < 0) {
            throw new SAXException("No XML declaration found in " + outputFile);
        }

        int count = 0;
        while (from >= 0) {
            final int next = content.indexOf(XML_DECLARATION, from + XML_DECLARATION.length());
            final String document = next < 0
                    ? content.substring(from)
                    : content.substring(from, next);
            documentBuilder.parse(new InputSource(new StringReader(document)));
            count++;
            from = next;
        }
        return count;
    }

    /**
     * Advances a pattern match by one byte, restarting at the current byte on a mismatch.
     * <p>
     * This is only correct for patterns whose longest proper border is empty, i.e. no proper prefix
     * of the pattern is also a suffix of it. All four patterns here qualify, so no KMP-style failure
     * table is needed. Adding a pattern that does not qualify would need a real matcher.
     */
    private static int advance(final int matched, final byte b, final byte[] pattern) {
        if (b == pattern[matched]) {
            return matched + 1;
        }
        // Mismatched, but the current byte may still start a fresh match.
        return b == pattern[0]
                ? 1
                : 0;
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required to be available", e);
        }
    }

    /**
     * What a scan of the output found.
     *
     * @param outputBytes       size of the output file.
     * @param eventCount        number of complete <code>&lt;Event&gt;</code> elements.
     * @param rootCount         number of <code>&lt;Events&gt;</code> root elements; expected to be 1.
     * @param distinctLineNos   how many distinct in-range LineNo values were seen.
     * @param duplicateLineNos  how many LineNo values were seen more than once.
     * @param outOfRangeLineNos how many LineNo values fell outside 1..expectedRecords.
     * @param truncated         true if the file ended part way through an event.
     * @param firstEvent        the first event in full, for spot checking content.
     * @param lastEvent         the last event in full, for spot checking content.
     * @param eventsDigest      SHA-256 over the event regions only, concatenated in document order.
     * @param fileDigest        SHA-256 over the whole output file.
     */
    public record Result(long outputBytes,
                         long eventCount,
                         long rootCount,
                         int distinctLineNos,
                         long duplicateLineNos,
                         long outOfRangeLineNos,
                         boolean truncated,
                         String firstEvent,
                         String lastEvent,
                         String eventsDigest,
                         String fileDigest) {

    }
}
