/*
 * Copyright 2016 Crown Copyright
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

package stroom.pipeline.server.factory;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import stroom.pipeline.server.errorhandler.ErrorReceiver;
import stroom.pipeline.server.errorhandler.ErrorStatistics;
import stroom.pipeline.server.errorhandler.FatalErrorReceiver;
import stroom.pipeline.server.errorhandler.LoggedException;
import stroom.task.server.TaskCallback;
import stroom.util.logging.StroomLogger;
import stroom.util.shared.Severity;
import stroom.util.shared.VoidResult;

public class SimpleProcessorFactory implements ProcessorFactory {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(SimpleProcessorFactory.class);

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
                final TaskCallback<VoidResult> taskCallback = new TaskCallback<VoidResult>() {
                    @Override
                    public void onSuccess(final VoidResult result) {
                        countDownLatch.countDown();
                    }

                    @Override
                    public void onFailure(final Throwable t) {
                        try {
                            if (t instanceof LoggedException) {
                                // The exception has already been logged so
                                // ignore it.
                                if (LOGGER.isTraceEnabled()) {
                                    LOGGER.trace(t.getMessage(), t);
                                }
                            } else {
                                outputError(t);
                            }
                        } finally {
                            countDownLatch.countDown();
                        }
                    }
                };

                final Thread thread = new Thread() {
                    @Override
                    public void run() {
                        try {
                            processor.process();

                            try {
                                taskCallback.onSuccess(VoidResult.INSTANCE);
                            } catch (final Exception e) {
                                // Ignore any errors that come from handling success.
                                LOGGER.trace(e.getMessage(), e);
                            }
                        } catch (final Throwable t) {
                            try {
                                taskCallback.onFailure(t);
                            } catch (final Exception e) {
                                // Ignore any errors that come from handling failure.
                                LOGGER.trace(e.getMessage(), e);
                            }
                        }
                    }
                };

                thread.start();
            }

            try {
                while (countDownLatch.getCount() > 0) {
                    countDownLatch.await(10, TimeUnit.SECONDS);
                }
            } catch (final InterruptedException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }

        /**
         * Used to handle any errors that may occur during translation.
         */
        private void outputError(final Throwable t) {
            if (errorReceiver != null && !(t instanceof LoggedException)) {
                try {
                    if (t.getMessage() != null) {
                        errorReceiver.log(Severity.FATAL_ERROR, null, "MultiWayProcessor", t.getMessage(), t);
                    } else {
                        errorReceiver.log(Severity.FATAL_ERROR, null, "MultiWayProcessor", t.toString(), t);
                    }
                } catch (final Throwable e) {
                    // Ignore exception as we generated it.
                }

                if (errorReceiver instanceof ErrorStatistics) {
                    ((ErrorStatistics) errorReceiver).checkRecord(-1);
                }
            } else {
                LOGGER.fatal(t, t);
            }
        }
    }

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
}
