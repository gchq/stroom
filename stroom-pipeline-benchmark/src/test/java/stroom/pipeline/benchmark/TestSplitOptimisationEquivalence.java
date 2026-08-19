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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the research in {@link SplitOptimisationBenchmark}: the Saxon optimisations must not change
 * what the pipeline produces.
 * <p>
 * Reusing a source tree between records is the risky one — if the reset missed any state, records
 * would bleed into each other and the benchmark would be timing a wrong answer. This runs the same
 * input through the real pipeline with each optimisation and requires byte identical output.
 */
class TestSplitOptimisationEquivalence {

    private static final int RECORD_COUNT = 5_000;

    /**
     * One split count small enough that the per-record path is exercised, and one large enough that
     * a group spans many records.
     */
    private static final int[] SPLIT_COUNTS = {1, 100};

    @AfterEach
    void clearHooks() {
        XsltFilter.experimentalTreeModel = null;
        XsltFilter.experimentalConfigurationCustomiser = null;
    }

    @Test
    void optimisationsDoNotChangeTheOutput() throws Exception {
        for (final int splitCount : SPLIT_COUNTS) {
            final Map<String, String> digestsByOptimisation = new LinkedHashMap<>();

            for (final String optimisation : new String[]{"none", "primedStats", "reusedTree", "both"}) {
                digestsByOptimisation.put(optimisation, runAndDigest(optimisation, splitCount));
            }

            assertThat(digestsByOptimisation.values())
                    .withFailMessage("splitCount=%d: an optimisation changed the output: %s",
                            splitCount, digestsByOptimisation)
                    .containsOnly(digestsByOptimisation.get("none"));
        }
    }

    private String runAndDigest(final String optimisation, final int splitCount) throws Exception {
        final boolean primeStats = "primedStats".equals(optimisation) || "both".equals(optimisation);
        final boolean reuseTree = "reusedTree".equals(optimisation) || "both".equals(optimisation);

        XsltFilter.experimentalConfigurationCustomiser = primeStats
                ? TestSplitOptimisationEquivalence::primeStatistics
                : null;
        if (reuseTree) {
            final ReusableTinyTreeModel model = new ReusableTinyTreeModel();
            XsltFilter.experimentalTreeModel = () -> model;
        } else {
            XsltFilter.experimentalTreeModel = null;
        }

        final Path input = BenchmarkPaths.inputFile(RECORD_COUNT);
        final Path output = BenchmarkPaths.outputFile(RECORD_COUNT, splitCount)
                .resolveSibling("equivalence-" + optimisation + "-" + splitCount + ".xml");

        try (final XmlTransformHarness harness = new XmlTransformHarness()) {
            final XmlTransformHarness.RunResult run = harness.run(input, splitCount, output);
            assertThat(run.errorReceiver().isAllOk())
                    .withFailMessage("%s at splitCount=%d reported errors: %s",
                            optimisation, splitCount, run.errorReceiver())
                    .isTrue();

            final TransformOutputVerifier.Result result =
                    TransformOutputVerifier.verify(output, RECORD_COUNT);

            assertThat(result.eventCount())
                    .withFailMessage("%s at splitCount=%d produced %d events, expected %d",
                            optimisation, splitCount, result.eventCount(), RECORD_COUNT)
                    .isEqualTo(RECORD_COUNT);
            assertThat(result.distinctLineNos()).isEqualTo(RECORD_COUNT);
            assertThat(result.duplicateLineNos()).isZero();

            return result.fileDigest();
        } finally {
            Files.deleteIfExists(output);
            Files.deleteIfExists(output.resolveSibling(output.getFileName() + ".lock"));
        }
    }

    private static void primeStatistics(final Configuration configuration) {
        final Statistics sourceStatistics = configuration.getTreeStatistics().SOURCE_DOCUMENT_STATISTICS;
        for (int i = 0; i < 100; i++) {
            sourceStatistics.updateStatistics(10, 13, 2, 32);
        }
    }
}
