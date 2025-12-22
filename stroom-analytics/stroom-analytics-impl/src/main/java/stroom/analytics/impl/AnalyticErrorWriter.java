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

package stroom.analytics.impl;

import stroom.data.store.api.Store;
import stroom.feed.api.VolumeGroupNameProvider;
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
import stroom.util.shared.ElementId;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import org.slf4j.MarkerFactory;

import java.io.IOException;
import java.util.function.Function;

@PipelineScoped
public class AnalyticErrorWriter {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AnalyticErrorWriter.class);
    private static final ElementId ELEMENT_ID = new ElementId("AnalyticsExecutor");

    private final RecordErrorReceiver recordErrorReceiver;
    private final ErrorReceiverProxy errorReceiverProxy;
    private final ErrorWriterProxy errorWriterProxy;
    private final RecordCount recordCount;
    private final Store store;
    private final VolumeGroupNameProvider volumeGroupNameProvider;

    @Inject
    public AnalyticErrorWriter(final RecordErrorReceiver recordErrorReceiver,
                               final ErrorReceiverProxy errorReceiverProxy,
                               final ErrorWriterProxy errorWriterProxy,
                               final RecordCount recordCount,
                               final Store store,
                               final VolumeGroupNameProvider volumeGroupNameProvider) {
        this.recordErrorReceiver = recordErrorReceiver;
        this.errorReceiverProxy = errorReceiverProxy;
        this.errorWriterProxy = errorWriterProxy;
        this.recordCount = recordCount;
        this.store = store;
        this.volumeGroupNameProvider = volumeGroupNameProvider;
    }

    <R> R exec(final String errorFeedName,
               final String pipelineUuid,
               final TaskContext taskContext,
               final Function<TaskContext, R> function) {
        // Setup the error handler and receiver.
        errorReceiverProxy.setErrorReceiver(recordErrorReceiver);

        try (final AnalyticRuleProcessInfoOutputStreamProvider processInfoOutputStreamProvider =
                new AnalyticRuleProcessInfoOutputStreamProvider(
                        store,
                        errorFeedName,
                        pipelineUuid,
                        recordCount,
                        errorReceiverProxy,
                        volumeGroupNameProvider)) {

            try {
                final DefaultErrorWriter errorWriter = new DefaultErrorWriter();
                errorWriter.addOutputStreamProvider(processInfoOutputStreamProvider);
                errorWriterProxy.setErrorWriter(errorWriter);

                return function.apply(taskContext);

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

        return null;
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
                    errorReceiverProxy.log(severity, null, ELEMENT_ID, e.getMessage(), e);
                } else {
                    errorReceiverProxy.log(severity, null, ELEMENT_ID, e.toString(), e);
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
