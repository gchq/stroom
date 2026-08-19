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
 * Attributes the cost of running with a small split count.
 * <p>
 * {@link SaxonReuseBenchmark} shows that in isolation Saxon charges roughly 1.4µs per record for
 * starting and finishing a transform rather than batching, yet the full pipeline shows well over
 * ten times that. This runs the same pipeline twice, once as
 * <pre>XMLParser -&gt; SplitFilter -&gt; XSLTFilter -&gt; XMLWriter -&gt; FileAppender</pre>
 * and once with the {@code XSLTFilter} taken out, at a small and a large split count. Any penalty
 * that survives the removal of the XSLT element belongs to the surrounding pipeline — the
 * {@code SplitFilter} replaying buffered start events, the {@code XMLWriter} opening and closing a
 * document, the appender inserting a segment marker — and not to Saxon.
 * <p>
 * Run with:
 * <pre>
 * ./gradlew :stroom-pipeline-benchmark:jmh -Pjmh.args="SplitOverheadAttribution -prof gc"
 * </pre>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(value = 1, jvmArgsAppend = "-Xmx8g")
@Warmup(iterations = 1)
@Measurement(iterations = 3)
public class SplitOverheadAttributionBenchmark {

    @Benchmark
    public void run(final ExecutionPlan plan, final Blackhole blackhole) {
        blackhole.consume(plan.harness.run(plan.input, plan.splitCountValue, plan.output, plan.withXsltValue));
    }

    @State(Scope.Benchmark)
    public static class ExecutionPlan {

        /**
         * One record per group versus a group size at which batching has already paid off.
         */
        @Param({"1", "100"})
        public String splitCount;

        @Param({"true", "false"})
        public String withXslt;

        @Param({"100000"})
        public String recordCount;

        private XmlTransformHarness harness;
        private Path input;
        private Path output;
        private int splitCountValue;
        private boolean withXsltValue;

        @Setup(Level.Trial)
        public void setUp() throws IOException {
            splitCountValue = Integer.parseInt(splitCount);
            withXsltValue = Boolean.parseBoolean(withXslt);
            final int records = Integer.parseInt(recordCount);

            input = BenchmarkPaths.inputFile(records);
            // Keep the two pipeline shapes on separate paths so they cannot collide.
            output = BenchmarkPaths.outputFile(records, splitCountValue)
                    .resolveSibling("attribution-" + withXsltValue + "-" + splitCountValue + ".xml");
            harness = new XmlTransformHarness();
        }

        @TearDown(Level.Trial)
        public void tearDown() throws IOException {
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
