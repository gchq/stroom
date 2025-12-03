/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.processor.impl;

import stroom.processor.impl.ProgressMonitor.FilterProgressMonitor;
import stroom.processor.impl.ProgressMonitor.Phase;
import stroom.processor.shared.ProcessorFilter;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class TestProgressMonitor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestProgressMonitor.class);

    private static final ProcessorFilter PROCESSOR_FILTER_1 = new ProcessorFilter();
    private static final ProcessorFilter PROCESSOR_FILTER_2 = new ProcessorFilter();


    static {
        PROCESSOR_FILTER_1.setId(1);
        PROCESSOR_FILTER_1.setPipelineName("Pipe 1");
        PROCESSOR_FILTER_2.setId(2);
        PROCESSOR_FILTER_2.setPipelineName("Pipe 2");
    }

    @Test
    void getSummary() {
        final String str = test(false, false, false);
        Assertions.assertThat(str)
                .contains("Inspected 2/2 filters"); // 10 + 20
    }

    @Test
    void getSummaryPlusPhaseDetail() {
        final String str = test(false, true, true);
        Assertions.assertThat(str)
                .contains(Phase.QUEUE_CREATED_TASKS.getPhaseName() + ": " + 30); // 10 + 20
    }

    @Test
    void getDetail() {
        final String str = test(true, true, false);
        Assertions.assertThat(str)
                .contains("Filter (id: 1, priority: 10, pipeline: Pipe 1)");
    }

    @Test
    void getDetailPlusPhaseDetail() {
        final String str = test(true, true, true);
        Assertions.assertThat(str)
                .contains("Filter (id: 1, priority: 10, pipeline: Pipe 1)");
    }

    private String test(final boolean showFilterDetail,
                        final boolean showSummaryPhaseDetail,
                        final boolean showFilterPhaseDetail) {
        final ProgressMonitor progressMonitor = new ProgressMonitor(2);

        final FilterProgressMonitor progressMonitor1 = progressMonitor.logFilter(PROCESSOR_FILTER_1, 0);
        final FilterProgressMonitor progressMonitor2 = progressMonitor.logFilter(PROCESSOR_FILTER_2, 0);
        for (final Phase phase : Phase.values()) {
            DurationTimer durationTimer = DurationTimer.start();
            progressMonitor1.logPhase(phase, durationTimer, 10);
            durationTimer = DurationTimer.start();
            progressMonitor2.logPhase(phase, durationTimer, 20);
        }
        progressMonitor1.complete();
        progressMonitor2.complete();

        final String str = progressMonitor
                .getFullReport("SUMMARY",
                        new QueueProcessTasksState(0, 0),
                        showFilterDetail,
                        showSummaryPhaseDetail,
                        showFilterPhaseDetail);
        LOGGER.info(str);
        return str;
    }

    @Test
    void testCreateInfo() {
        testCreate(false, false, false);
    }

    @Test
    void testCreateDebug() {
        testCreate(true, true, false);
    }

    @Test
    void testCreateTrace() {
        testCreate(true, true, true);
    }

    private void testCreate(final boolean showFilterDetail,
                            final boolean showSummaryPhaseDetail,
                            final boolean showFilterPhaseDetail) {
        final ProgressMonitor progressMonitor = new ProgressMonitor(2);
        final FilterProgressMonitor progressMonitor1 = progressMonitor.logFilter(PROCESSOR_FILTER_1, 0);
        final FilterProgressMonitor progressMonitor2 = progressMonitor.logFilter(PROCESSOR_FILTER_2, 0);

        for (final FilterProgressMonitor fpm : List.of(progressMonitor1, progressMonitor2)) {
            final DurationTimer durationTimer = DurationTimer.start();
            fpm.logPhase(Phase.FIND_META_FOR_FILTER, durationTimer, 200);
            fpm.add(200);
            fpm.logPhase(Phase.CREATE_STREAM_MAP,
                    durationTimer,
                    100);
            fpm.complete();
        }

        LOGGER.info(() -> progressMonitor.getFullReport(
                "CREATE NEW TASKS",
                null,
                showFilterDetail,
                showSummaryPhaseDetail,
                showFilterPhaseDetail));
    }
}
