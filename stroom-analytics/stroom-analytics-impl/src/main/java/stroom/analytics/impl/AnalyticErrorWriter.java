package stroom.analytics.impl;

import stroom.data.store.api.Store;
import stroom.pipeline.DefaultErrorWriter;
import stroom.pipeline.ErrorWriterProxy;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.ErrorStatistics;
import stroom.pipeline.errorhandler.LoggedException;
import stroom.pipeline.errorhandler.RecordErrorReceiver;
import stroom.pipeline.state.RecordCount;
import stroom.task.api.TaskContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.pipeline.scope.PipelineScoped;
import stroom.util.shared.Severity;

import org.slf4j.MarkerFactory;

import java.io.IOException;
import java.util.function.Consumer;
import javax.inject.Inject;

@PipelineScoped
public class AnalyticErrorWriter {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AnalyticErrorWriter.class);

    private final RecordErrorReceiver recordErrorReceiver;
    private final ErrorReceiverProxy errorReceiverProxy;
    private final ErrorWriterProxy errorWriterProxy;
    private final RecordCount recordCount;
    private final Store store;

    @Inject
    public AnalyticErrorWriter(final RecordErrorReceiver recordErrorReceiver,
                               final ErrorReceiverProxy errorReceiverProxy,
                               final ErrorWriterProxy errorWriterProxy,
                               final RecordCount recordCount,
                               final Store store) {
        this.recordErrorReceiver = recordErrorReceiver;
        this.errorReceiverProxy = errorReceiverProxy;
        this.errorWriterProxy = errorWriterProxy;
        this.recordCount = recordCount;
        this.store = store;
    }

    void exec(final String errorFeedName,
              final String analyticUuid,
              final String pipelineUuid,
              final Runnable runnable) {
        // Setup the error handler and receiver.
        errorReceiverProxy.setErrorReceiver(recordErrorReceiver);

        try (final AnalyticRuleProcessInfoOutputStreamProvider processInfoOutputStreamProvider =
                new AnalyticRuleProcessInfoOutputStreamProvider(
                        store,
                        errorFeedName,
                        analyticUuid,
                        pipelineUuid,
                        recordCount,
                        errorReceiverProxy)) {

            try {
                final DefaultErrorWriter errorWriter = new DefaultErrorWriter();
                errorWriter.addOutputStreamProvider(processInfoOutputStreamProvider);
                errorWriterProxy.setErrorWriter(errorWriter);

                runnable.run();

            } catch (final Exception e) {
                outputError(e);
            } finally {
                // Ensure we are no longer interrupting if necessary.
                if (Thread.interrupted()) {
                    LOGGER.debug(() -> "Cleared interrupt flag");
                }
            }
        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    private void outputError(final Exception e) {
        outputError(e, Severity.FATAL_ERROR);
    }

    /**
     * Used to handle any errors that may occur during translation.
     */
    private void outputError(final Exception e, final Severity severity) {
        if (errorReceiverProxy != null && !(e instanceof LoggedException)) {
            try {
                if (e.getMessage() != null) {
                    errorReceiverProxy.log(severity, null, "AnalyticsExecutor", e.getMessage(), e);
                } else {
                    errorReceiverProxy.log(severity, null, "AnalyticsExecutor", e.toString(), e);
                }
            } catch (final RuntimeException e2) {
                // Ignore exception as we generated it.
            }

            if (errorReceiverProxy.getErrorReceiver() instanceof ErrorStatistics) {
                ((ErrorStatistics) errorReceiverProxy.getErrorReceiver()).checkRecord(-1);
            }
        } else {
            LOGGER.error(MarkerFactory.getMarker("FATAL"), e.getMessage(), e);
        }
    }
}
