package stroom.processor.impl;

import stroom.processor.impl.ProgressMonitor.Phase;
import stroom.processor.shared.ProcessorFilter;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

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
        final ProgressMonitor progressMonitor = new ProgressMonitor(
                new ProcessorConfig(),
                150);

        for (final Phase phase : Phase.values()) {
            progressMonitor.logPhase(phase, PROCESSOR_FILTER_1, () -> 10);
            progressMonitor.logPhase(phase, PROCESSOR_FILTER_2, () -> 20);
        }

        final String str = LogUtil.inBoxOnNewLine(progressMonitor.getSummary());
        LOGGER.info(str);
        Assertions.assertThat(str)
                .contains(Phase.SELECT_NEW_TASKS.getPhaseName() + ": " + 30); // 10 + 20
    }

    @Test
    void getDetail() {
        final ProgressMonitor progressMonitor = new ProgressMonitor(
                new ProcessorConfig(),
                150);

        for (final Phase phase : Phase.values()) {
            progressMonitor.logPhase(phase, PROCESSOR_FILTER_1, () -> 10);
            progressMonitor.logPhase(phase, PROCESSOR_FILTER_2, () -> 20);
        }
        final String str = LogUtil.inBoxOnNewLine(progressMonitor.getDetail());
        LOGGER.info(str);
        Assertions.assertThat(str)
                .contains("Filter (id: 1, pipeline: Pipe 1)");
    }
}
