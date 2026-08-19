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

import stroom.pipeline.filter.XsltFilter;

import net.sf.saxon.Configuration;
import net.sf.saxon.tree.tiny.Statistics;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * The split count sweep, run through the real Stroom pipeline
 * <pre>XMLParser -&gt; SplitFilter -&gt; XSLTFilter -&gt; XMLWriter -&gt; FileAppender</pre>
 * for each of the Saxon optimisations under investigation, reported as time.
 * <p>
 * The optimisations are:
 * <ul>
 *     <li><b>none</b> — Stroom as it stands;</li>
 *     <li><b>primedStats</b> — the Saxon configuration's self-tuning source-document statistics are
 *     driven down to their floor up front, so early documents are not sized for 4000 nodes. Public
 *     Saxon API only;</li>
 *     <li><b>reusedTree</b> — a custom {@code TreeModel} whose builder keeps its {@code TinyTree}
 *     between documents rather than allocating one per record;</li>
 *     <li><b>both</b> — the two together.</li>
 * </ul>
 * <p>
 * Both are applied through the experimental static hooks on {@link XsltFilter}, because
 * {@code Controller} hardcodes its tree model and Stroom builds a new transformer per document.
 * Those hooks exist only for this research and are set and cleared per trial here.
 * <p>
 * Run with:
 * <pre>
 * ./gradlew :stroom-pipeline-benchmark:jmh -Pjmh.args="SplitOptimisation"
 * ./gradlew :stroom-pipeline-benchmark:jmh -Pjmh.args="SplitOptimisation -p recordCount=20000"
 * </pre>
 * Note that {@code primedStats} is expected to fade as the record count rises: Saxon's statistics
 * converge on their own after a few thousand documents, so the longer the stream the less there is
 * to fix. Lower {@code recordCount} to see it.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(value = 1, jvmArgsAppend = "-Xmx8g")
@Warmup(iterations = 1)
@Measurement(iterations = 3)
public class SplitOptimisationBenchmark {

    @Benchmark
    public void run(final ExecutionPlan plan, final Blackhole blackhole) {
        blackhole.consume(plan.harness.run(plan.input, plan.splitCountValue, plan.output));
    }

    @State(Scope.Benchmark)
    public static class ExecutionPlan {

        @Param({"1", "10", "100", "1000", "10000"})
        public String splitCount;

        @Param({"none", "primedStats", "reusedTree", "both"})
        public String optimisation;

        @Param({"100000"})
        public String recordCount;

        private XmlTransformHarness harness;
        private Path input;
        private Path output;
        private int splitCountValue;

        @Setup(Level.Trial)
        public void setUp() throws IOException {
            splitCountValue = Integer.parseInt(splitCount);
            final int records = Integer.parseInt(recordCount);

            final boolean primeStats = "primedStats".equals(optimisation) || "both".equals(optimisation);
            final boolean reuseTree = "reusedTree".equals(optimisation) || "both".equals(optimisation);

            // Install before the harness runs, so the hooks are in place when the pipeline starts.
            XsltFilter.experimentalConfigurationCustomiser = primeStats
                    ? ExecutionPlan::primeStatistics
                    : null;

            if (reuseTree) {
                // One model, and therefore one retained tree, for the whole trial. Safe because a
                // single pipeline is driven by a single thread here.
                final ReusableTinyTreeModel model = new ReusableTinyTreeModel();
                XsltFilter.experimentalTreeModel = () -> model;
            } else {
                XsltFilter.experimentalTreeModel = null;
            }

            input = BenchmarkPaths.inputFile(records);
            output = BenchmarkPaths.outputFile(records, splitCountValue)
                    .resolveSibling("opt-" + optimisation + "-" + splitCountValue + ".xml");
            harness = new XmlTransformHarness();
        }

        /**
         * Drives the configuration's source-document statistics down to their floor so the first
         * trees are sized for a small record rather than for Saxon's default of 4000 nodes. The
         * running mean starts with a weight of 5, so a few dozen updates converge it.
         */
        private static void primeStatistics(final Configuration configuration) {
            final Statistics sourceStatistics =
                    configuration.getTreeStatistics().SOURCE_DOCUMENT_STATISTICS;
            for (int i = 0; i < 100; i++) {
                sourceStatistics.updateStatistics(10, 13, 2, 32);
            }
        }

        @TearDown(Level.Trial)
        public void tearDown() throws IOException {
            XsltFilter.experimentalTreeModel = null;
            XsltFilter.experimentalConfigurationCustomiser = null;

            if (harness != null) {
                harness.close();
            }
            if (output != null) {
                Files.deleteIfExists(output);
                Files.deleteIfExists(output.resolveSibling(output.getFileName() + ".lock"));
            }
        }
    }
}
