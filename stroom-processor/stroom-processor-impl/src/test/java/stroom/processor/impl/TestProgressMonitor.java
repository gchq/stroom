package stroom.processor.impl;

import stroom.processor.impl.ProgressMonitor.FilterProgressMonitor;
import stroom.processor.impl.ProgressMonitor.Phase;
import stroom.processor.shared.ProcessorFilter;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

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
        final String str = test(false, false);
        Assertions.assertThat(str)
                .contains("Created tasks for 2/2 filters"); // 10 + 20
    }

    @Test
    void getSummaryPlusPhaseDetail() {
        final String str = test(false, true);
        Assertions.assertThat(str)
                .contains(Phase.SELECT_NEW_TASKS.getPhaseName() + ": " + 30); // 10 + 20
    }

    @Test
    void getDetail() {
        final String str = test(true, false);
        Assertions.assertThat(str)
                .contains("Filter (id: 1, priority: 10, pipeline: Pipe 1)");
    }

    @Test
    void getDetailPlusPhaseDetail() {
        final String str = test(true, true);
        Assertions.assertThat(str)
                .contains("Filter (id: 1, priority: 10, pipeline: Pipe 1)");
    }

    private String test(final boolean showFilterDetail, final boolean showPhaseDetail) {
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
                .getFullReport(new CreateProcessTasksState(0, 0),
                        showFilterDetail,
                        showPhaseDetail);
        LOGGER.info(str);
        return str;
    }
}
