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

package stroom.pipeline.factory;

import stroom.pipeline.errorhandler.ErrorReceiver;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.ErrorStatistics;
import stroom.pipeline.errorhandler.LoggedException;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TerminateHandlerFactory;
import stroom.util.pipeline.scope.PipelineScoped;
import stroom.util.shared.ElementId;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@PipelineScoped
class ProcessorFactoryImpl implements ProcessorFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessorFactoryImpl.class);
    private static final ElementId MULTIWAY_ELEMENT_ID = new ElementId("MultiWayProcessor");

    private final Executor executor;
    private final TaskContextFactory taskContextFactory;
    private final ErrorReceiverProxy errorReceiverProxy;

    @Inject
    public ProcessorFactoryImpl(final Executor executor,
                                final TaskContextFactory taskContextFactory,
                                final ErrorReceiverProxy errorReceiverProxy) {
        this.executor = executor;
        this.taskContextFactory = taskContextFactory;
        this.errorReceiverProxy = errorReceiverProxy;
    }

    @Override
    public Processor create(final List<Processor> processors) {
        if (processors == null || processors.isEmpty()) {
            return null;
        }

        if (processors.size() == 1) {
            return processors.get(0);
        }

        final TaskContext taskContext = taskContextFactory.current();
        return new MultiWayProcessor(processors, executor, taskContextFactory, taskContext, errorReceiverProxy);
    }


    // --------------------------------------------------------------------------------


    static class MultiWayProcessor implements Processor {

        private final List<Processor> processors;
        private final Executor executor;
        private final TaskContextFactory taskContextFactory;
        private final TaskContext parentTaskContext;
        private final ErrorReceiver errorReceiver;

        MultiWayProcessor(final List<Processor> processors,
                          final Executor executor,
                          final TaskContextFactory taskContextFactory,
                          final TaskContext parentTaskContext,
                          final ErrorReceiver errorReceiver) {
            this.processors = processors;
            this.executor = executor;
            this.taskContextFactory = taskContextFactory;
            this.parentTaskContext = parentTaskContext;
            this.errorReceiver = errorReceiver;
        }

        @Override
        public void process() {
            final CountDownLatch countDownLatch = new CountDownLatch(processors.size());
            for (final Processor processor : processors) {
                final Runnable runnable = taskContextFactory.childContext(
                        parentTaskContext,
                        "Process",
                        TerminateHandlerFactory.NOOP_FACTORY,
                        taskContext -> processor.process());
                CompletableFuture
                        .runAsync(runnable, executor)
                        .whenComplete((r, t) -> {
                            if (t != null) {
                                while (t instanceof CompletionException) {
                                    t = t.getCause();
                                }
                                outputError(t);
                            }
                            countDownLatch.countDown();
                        });
            }

            try {
                boolean success = false;
                while (!parentTaskContext.isTerminated() && !success) {
                    success = countDownLatch.await(10, TimeUnit.SECONDS);
                }
            } catch (final InterruptedException e) {
                LOGGER.error(e.getMessage(), e);

                // Continue to interrupt this thread.
                Thread.currentThread().interrupt();
            }
        }

        /**
         * Used to handle any errors that may occur during translation.
         */
        private void outputError(final Throwable t) {
            if (errorReceiver != null && !(t instanceof LoggedException)) {
                try {
                    if (t.getMessage() != null) {
                        errorReceiver.log(Severity.FATAL_ERROR,
                                null,
                                MULTIWAY_ELEMENT_ID,
                                t.getMessage(),
                                t);
                    } else {
                        errorReceiver.log(Severity.FATAL_ERROR,
                                null,
                                MULTIWAY_ELEMENT_ID,
                                t.toString(),
                                t);
                    }
                } catch (final RuntimeException e) {
                    // Ignore exception as we generated it.
                }

                if (errorReceiver instanceof ErrorStatistics) {
                    ((ErrorStatistics) errorReceiver).checkRecord(-1);
                }
            } else {
                LOGGER.error(MarkerFactory.getMarker("FATAL"), "Unable to output error!", t);
            }
        }
    }
}
