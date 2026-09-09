/*
 * Copyright 2026 Crown Copyright
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

package stroom.data.store.impl;


import stroom.receive.common.S3EventConfig;
import stroom.receive.common.S3EventService;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TerminateHandlerFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.time.Instant;

public class S3EventPollManager {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(S3EventPollManager.class);

    private final TaskContextFactory taskContextFactory;
    private final S3EventService s3EventService;
    private final Provider<S3EventConfig> s3EventConfigProvider;

    @Inject
    public S3EventPollManager(final TaskContextFactory taskContextFactory,
                              final S3EventService s3EventService,
                              final Provider<S3EventConfig> s3EventConfigProvider) {
        this.taskContextFactory = taskContextFactory;
        this.s3EventService = s3EventService;
        this.s3EventConfigProvider = s3EventConfigProvider;
    }

    /// This will continually poll for S3 events until S3EventConfig#rePollDuration is reached
    /// or the task is terminated.
    public void poll() {
        final S3EventConfig s3EventConfig = s3EventConfigProvider.get();
        final TaskContext parentTaskContext = taskContextFactory.current();

        final Instant endTime = Instant.now().plus(s3EventConfig.getRePollDuration());

        // Create a child context to no-op the termination so we can stop at our own pace
        final Runnable runnable = taskContextFactory.childContext(
                parentTaskContext,
                "SQS poll",
                TerminateHandlerFactory.NOOP_FACTORY,
                taskContext -> {
                    do {
                        LOGGER.debug("poll() - Calling poll on service");
                        // Don't pass in the end time so that the whole batch of messages from the
                        // poll is processed in full.
                        s3EventService.poll(() ->
                                shouldContinuePolling(null, taskContext));
                    } while (shouldContinuePolling(endTime, taskContext));
                });

        runnable.run();
    }

    private boolean shouldContinuePolling(final Instant endTime, final TaskContext taskContext) {
        if (taskContext.isTerminated()) {
            LOGGER.debug("shouldContinuePolling() - task {} terminated", taskContext);
            return false;
        }
        if (Thread.currentThread().isInterrupted()) {
            LOGGER.debug("shouldContinuePolling() - Thread interrupted");
            return false;
        }
        if (endTime != null && Instant.now().isAfter(endTime)) {
            LOGGER.debug("shouldContinuePolling() - endTime {} reached", endTime);
            return false;
        }
        return true;
    }
}
