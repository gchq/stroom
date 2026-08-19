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

import stroom.pipeline.benchmark.XmlTransformHarness.RunResult;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the pipeline
 * <pre>XMLParser -&gt; SplitFilter -&gt; XSLTFilter -&gt; XMLWriter -&gt; FileAppender</pre>
 * over a generated <code>records:2</code> file, at a fixed split depth of 1 and every split count
 * from one record per group up to the whole file in a single group.
 * <p>
 * Three things are being established:
 * <ul>
 *     <li>the translation is correct — every record becomes exactly one event, none is lost or
 *     duplicated, and the values carried through are the ones the XSLT should have produced;</li>
 *     <li>the split count is purely a batching decision — it changes how often the XSLT is invoked
 *     and how much is held in memory at once, but the bytes that come out are identical;</li>
 *     <li>a split count large enough to put more than {@value #DEFAULT_MAX_ELEMENTS} elements
 *     through a single transform is rejected rather than silently attempted.</li>
 * </ul>
 * <p>
 * The timings printed by {@link #splitCountChangesBatchingButNotOutput()} are a single wall clock
 * run each and are indicative only. Use {@link XmlTransformSplitBenchmark}
 * (<code>./gradlew :stroom-pipeline-benchmark:jmh</code>) for numbers worth quoting.
 */
class TestXmlTransformSplit {

    private static final int[] SPLIT_COUNTS = {1, 10, 100, 1_000, 10_000, 100_000, 1_000_000};

    private static final String RECORDS_PROP = "stroom.benchmark.records";

    /**
     * Deliberately not the full million. This test runs on every build, and a million records takes
     * around five minutes across the seven split counts. 200,000 is still above the point at which
     * the largest split count trips the element cap, so the sweep covers the same behaviours in
     * roughly a fifth of the time. Use <code>-Dstroom.benchmark.records=1000000</code> for the full
     * run; {@link XmlTransformSplitBenchmark} defaults to a million.
     */
    private static final int RECORD_COUNT = Integer.getInteger(RECORDS_PROP, 200_000);

    /**
     * Small enough that the output can be fully parsed by a DOM parser.
     */
    private static final int SMALL_RECORD_COUNT = 1_000;

    /**
     * Each generated record is seven elements: the <code>record</code> itself plus its six
     * <code>data</code> children. A split group also replays the <code>records</code> root, hence
     * the extra one below.
     */
    private static final int ELEMENTS_PER_RECORD = 7;

    /**
     * The default of {@code XsltConfig.maxElements}. The XSLT filter refuses to accept more than
     * this many elements in a single transform, which is what stops a pipeline with no usable
     * splitter from exhausting memory.
     */
    private static final int DEFAULT_MAX_ELEMENTS = 1_000_000;

    private static XmlTransformHarness harness;

    @BeforeAll
    static void createHarness() throws IOException {
        harness = new XmlTransformHarness();
    }

    @AfterAll
    static void closeHarness() {
        if (harness != null) {
            harness.close();
        }
    }

    /**
     * Proves the translation itself is right, at a scale where the output can be fully parsed.
     */
    @Test
    void translatesRecordsToEvents() throws Exception {
        final int splitCount = 100;
        final Path input = BenchmarkPaths.inputFile(SMALL_RECORD_COUNT);
        final Path output = BenchmarkPaths.outputFile(SMALL_RECORD_COUNT, splitCount);

        final RunResult run = harness.run(input, splitCount, output);
        assertThat(run.errorReceiver().isAllOk())
                .withFailMessage("Pipeline reported errors: %s", run.errorReceiver())
                .isTrue();

        final TransformOutputVerifier.Result result =
                TransformOutputVerifier.verify(output, SMALL_RECORD_COUNT);

        assertThat(result.truncated()).isFalse();
        assertThat(result.eventCount()).isEqualTo(SMALL_RECORD_COUNT);

        // Every record arrived exactly once.
        assertThat(result.distinctLineNos()).isEqualTo(SMALL_RECORD_COUNT);
        assertThat(result.duplicateLineNos()).isZero();
        assertThat(result.outOfRangeLineNos()).isZero();

        // The XSLT reformats the record's separate Date and Time fields into a single ISO timestamp
        // and copies the message and line number through. Pinning the first and last event down
        // checks the transform and the ordering at both ends of the file. Record i is generated at
        // 2010-01-01T00:00:00Z plus i seconds, so record 1000 lands at 00:16:39.
        assertThat(result.firstEvent())
                .contains("<TimeCreated>2010-01-01T00:00:00.000Z</TimeCreated>")
                .contains("<Description>Some message 1</Description>")
                .contains("<Data Name=\"LineNo\" Value=\"1\"/>");
        assertThat(result.lastEvent())
                .contains("<TimeCreated>2010-01-01T00:16:39.000Z</TimeCreated>")
                .contains("<Description>Some message 1000</Description>")
                .contains("<Data Name=\"LineNo\" Value=\"1000\"/>");

        // However many groups the split filter made, the writer folds them back into one document,
        // and that document is well formed XML.
        assertThat(result.rootCount()).isEqualTo(1);
        assertThat(TransformOutputVerifier.parseAllDocuments(output)).isEqualTo(1);

        Files.deleteIfExists(output);
    }

    /**
     * Runs every split count over the full record count, timing each, and requires that all the
     * accepted ones produce byte identical output.
     */
    @Test
    void splitCountChangesBatchingButNotOutput() throws Exception {
        final Path input = BenchmarkPaths.inputFile(RECORD_COUNT);
        System.out.printf("%nInput: %s%n%,d records, %,d bytes, split depth %d%n%n",
                input, RECORD_COUNT, Files.size(input), XmlTransformHarness.SPLIT_DEPTH);
        System.out.printf("%-12s %14s %14s %16s %10s %14s%n",
                "splitCount", "duration(ms)", "records/sec", "output(bytes)", "events", "outputDigest");

        final Map<Integer, String> digestsBySplitCount = new LinkedHashMap<>();

        for (final int splitCount : SPLIT_COUNTS) {
            final Path output = BenchmarkPaths.outputFile(RECORD_COUNT, splitCount);

            if (exceedsElementCap(splitCount)) {
                assertRejectedAsTooLarge(input, splitCount, output);
                continue;
            }

            final RunResult run = harness.run(input, splitCount, output);
            assertThat(run.errorReceiver().isAllOk())
                    .withFailMessage("splitCount=%d reported errors: %s", splitCount, run.errorReceiver())
                    .isTrue();

            final TransformOutputVerifier.Result result = TransformOutputVerifier.verify(output, RECORD_COUNT);

            System.out.printf("%-12d %14.1f %14.0f %16d %10d %14s%n",
                    splitCount,
                    run.durationMillis(),
                    RECORD_COUNT / (run.durationNanos() / 1_000_000_000d),
                    result.outputBytes(),
                    result.eventCount(),
                    result.fileDigest().substring(0, 12));

            assertThat(result.truncated())
                    .withFailMessage("splitCount=%d produced a truncated final event", splitCount)
                    .isFalse();
            assertThat(result.eventCount())
                    .withFailMessage("splitCount=%d produced %d events, expected %d",
                            splitCount, result.eventCount(), RECORD_COUNT)
                    .isEqualTo(RECORD_COUNT);
            assertThat(result.rootCount())
                    .withFailMessage("splitCount=%d produced %d Events roots, expected a single document",
                            splitCount, result.rootCount())
                    .isEqualTo(1);
            assertThat(result.distinctLineNos())
                    .withFailMessage("splitCount=%d saw %d distinct LineNo values, expected %d",
                            splitCount, result.distinctLineNos(), RECORD_COUNT)
                    .isEqualTo(RECORD_COUNT);
            assertThat(result.duplicateLineNos()).isZero();
            assertThat(result.outOfRangeLineNos()).isZero();

            digestsBySplitCount.put(splitCount, result.fileDigest());

            // A million records runs to hundreds of megabytes per output, so do not keep them all.
            Files.deleteIfExists(output);
        }

        assertThat(digestsBySplitCount)
                .withFailMessage("No split count was small enough to run; check %s", RECORDS_PROP)
                .isNotEmpty();
        assertThat(digestsBySplitCount.values())
                .withFailMessage("Output differs between split counts: %s", digestsBySplitCount)
                .containsOnly(digestsBySplitCount.values().iterator().next());
    }

    /**
     * A split count that would put the whole stream through one transform is not a supported
     * configuration; Stroom rejects it and says so. Asserting on that keeps the sweep honest rather
     * than quietly omitting the largest split count.
     */
    private void assertRejectedAsTooLarge(final Path input, final int splitCount, final Path output)
            throws IOException {
        assertThatThrownBy(() -> harness.run(input, splitCount, output))
                .hasMessageContaining("Max element count")
                .hasMessageContaining("split filter");

        System.out.printf("%-12d %14s %14s %16s %10s %14s%n",
                splitCount, "rejected", "-", "-", "-", "> " + DEFAULT_MAX_ELEMENTS + " elements");

        Files.deleteIfExists(output);
        Files.deleteIfExists(output.resolveSibling(output.getFileName() + ".lock"));
    }

    /**
     * True if a group of this many records would exceed what the XSLT filter accepts in one
     * transform. A group is capped by the number of records available, so this only trips when the
     * input itself is large.
     */
    private static boolean exceedsElementCap(final int splitCount) {
        final long recordsPerGroup = splitCount <= 0
                ? RECORD_COUNT
                : Math.min(splitCount, RECORD_COUNT);
        return (recordsPerGroup * ELEMENTS_PER_RECORD) + 1 > DEFAULT_MAX_ELEMENTS;
    }
}
