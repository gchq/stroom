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
import stroom.pipeline.errorhandler.ErrorStatistics;
import stroom.pipeline.errorhandler.FatalErrorReceiver;
import stroom.pipeline.errorhandler.LoggedException;
import stroom.util.shared.ElementId;
import stroom.util.shared.Severity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SimpleProcessorFactory implements ProcessorFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleProcessorFactory.class);
    private static final ElementId MULTIWAY_ELEMENT_ID = new ElementId("MultiWayProcessor");

    private final ErrorReceiver errorReceiver;

    public SimpleProcessorFactory() {
        this(new FatalErrorReceiver());
    }

    public SimpleProcessorFactory(final ErrorReceiver errorReceiver) {
        this.errorReceiver = errorReceiver;
    }

    @Override
    public Processor create(final List<Processor> processors) {
        if (processors == null || processors.size() == 0) {
            return null;
        }

        if (processors.size() == 1) {
            return processors.get(0);
        }

        return new MultiWayProcessor(processors, errorReceiver);
    }

    private static class MultiWayProcessor implements Processor {

        private final List<Processor> processors;
        private final ErrorReceiver errorReceiver;

        public MultiWayProcessor(final List<Processor> processors, final ErrorReceiver errorReceiver) {
            this.processors = processors;
            this.errorReceiver = errorReceiver;
        }

        @Override
        public void process() {
            final CountDownLatch countDownLatch = new CountDownLatch(processors.size());

            for (final Processor processor : processors) {
                CompletableFuture.runAsync(() -> {
                    try {
                        processor.process();

                        try {
                            countDownLatch.countDown();
                        } catch (final RuntimeException e) {
                            // Ignore any errors that come from handling success.
                            LOGGER.trace(e.getMessage(), e);
                        }
                    } catch (final RuntimeException e) {
                        try {
                            try {
                                if (e instanceof LoggedException) {
                                    // The exception has already been logged so
                                    // ignore it.
                                    if (LOGGER.isTraceEnabled()) {
                                        LOGGER.trace(e.getMessage(), e);
                                    }
                                } else {
                                    outputError(e);
                                }
                            } finally {
                                countDownLatch.countDown();
                            }
                        } catch (final RuntimeException e2) {
                            // Ignore any errors that come from handling failure.
                            LOGGER.trace(e2.getMessage(), e2);
                        }
                    }
                });
            }

            try {
                while (countDownLatch.getCount() > 0) {
                    countDownLatch.await(10, TimeUnit.SECONDS);
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
                LOGGER.error(MarkerFactory.getMarker("FATAL"), t.getMessage(), t);
            }
        }
    }
}
