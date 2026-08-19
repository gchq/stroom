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
 * Times the pipeline
 * <pre>XMLParser -&gt; SplitFilter -&gt; XSLTFilter -&gt; XMLWriter -&gt; FileAppender</pre>
 * at split depth 1 across every split count, to show what batching costs.
 * <p>
 * Run with:
 * <pre>
 * ./gradlew :stroom-pipeline-benchmark:jmh
 * ./gradlew :stroom-pipeline-benchmark:jmh -Pjmh.args="-f 3 -wi 2 -i 5"
 * ./gradlew :stroom-pipeline-benchmark:jmh -Pjmh.args="-p recordCount=100000"
 * </pre>
 * <p>
 * A single invocation processes the whole input file, which takes seconds rather than nanoseconds,
 * so this uses {@link Mode#SingleShotTime} with explicit iteration counts rather than throughput
 * mode. The defaults are kept low because each iteration writes hundreds of megabytes; raise them
 * with <code>-Pjmh.args</code> when the numbers need to be tighter.
 * <p>
 * What is measured is one full {@code Pipeline} run: the injector, the document store entries and
 * the compiled stylesheet are all built once per trial in {@link ExecutionPlan#setUp()} and shared,
 * so warm up cost is not charged to the measurement. Deleting the previous output is inside the
 * measured region because {@code FileAppender} refuses to overwrite; an unlink is negligible next
 * to a multi-second transform.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(value = 1, jvmArgsAppend = "-Xmx8g")
@Warmup(iterations = 1)
@Measurement(iterations = 3)
public class XmlTransformSplitBenchmark {

    @Benchmark
    public void transform(final ExecutionPlan plan, final Blackhole blackhole) {
        blackhole.consume(plan.harness.run(plan.input, plan.splitCountValue, plan.output));
    }

    @State(Scope.Benchmark)
    public static class ExecutionPlan {

        /**
         * Records per output group.
         * <p>
         * This stops at 100,000 rather than going up to the full million. At a million records per
         * group the XSLT filter would see seven million elements in one transform and reject it,
         * because {@code XsltConfig.maxElements} defaults to a million — so there is nothing to
         * time. {@code TestXmlTransformSplit} covers that rejection. Override with
         * <code>-Pjmh.args="-p splitCount=…"</code>.
         */
        @Param({"1", "10", "100", "1000", "10000", "100000"})
        public String splitCount;

        @Param({"1000000"})
        public String recordCount;

        private XmlTransformHarness harness;
        private Path input;
        private Path output;
        private int splitCountValue;

        @Setup(Level.Trial)
        public void setUp() throws IOException {
            splitCountValue = Integer.parseInt(splitCount);
            final int records = Integer.parseInt(recordCount);

            // Generated once and reused; a forked JVM finds the file already there.
            input = BenchmarkPaths.inputFile(records);
            output = BenchmarkPaths.outputFile(records, splitCountValue);

            // Building the injector, the document store entries and the compiled stylesheet happens
            // once per trial and is not charged to any measured iteration. The remaining first-run
            // costs are absorbed by the warm-up iteration rather than by an extra pass here, which
            // at a million records would be minutes of wall clock for no benefit.
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
