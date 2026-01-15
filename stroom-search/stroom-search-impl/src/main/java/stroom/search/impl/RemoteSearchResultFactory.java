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

package stroom.search.impl;

import stroom.query.common.v2.CoprocessorsImpl;
import stroom.query.common.v2.NodeResultSerialiser;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskManager;
import stroom.task.shared.TaskId;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ErrorMessage;

import com.esotericsoftware.kryo.io.Output;

import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

class RemoteSearchResultFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RemoteSearchResultFactory.class);

    private final TaskManager taskManager;
    private final SecurityContext securityContext;

    private volatile CoprocessorsImpl coprocessors;
    private volatile TaskId taskId;
    private volatile boolean destroy;
    private volatile boolean started;
    private volatile List<ErrorMessage> initialisationError;

    RemoteSearchResultFactory(final TaskManager taskManager,
                              final SecurityContext securityContext) {
        this.taskManager = taskManager;
        this.securityContext = securityContext;
    }

    public void write(final OutputStream outputStream) {
        try (final Output output = new Output(outputStream)) {
            if (initialisationError != null) {
                NodeResultSerialiser.write(output, true, coprocessors, initialisationError);
            } else {
                try {
                    // Wait to complete.
                    final boolean complete = coprocessors.getCompletionState().awaitCompletion(1, TimeUnit.SECONDS);

                    // Write completion status.
                    if (!started) {
                        LOGGER.debug(() -> "Node search not started");
                        NodeResultSerialiser.writeEmptyResponse(output, false);

                    } else if (Thread.currentThread().isInterrupted() || destroy) {
                        LOGGER.debug(() -> "Terminated or destroyed: terminated=" +
                                Thread.currentThread().isInterrupted() +
                                ", destroyed=" +
                                destroy);
                        NodeResultSerialiser.writeEmptyResponse(output, true);

                    } else {
                        // Drain all current errors to a list.
                        final List<ErrorMessage> errorsSnapshot = coprocessors.getErrorConsumer().drain();
                        NodeResultSerialiser.write(output, complete, coprocessors, errorsSnapshot);
                    }

                } catch (final InterruptedException e) {
                    LOGGER.debug(e::getMessage, e);
                    NodeResultSerialiser.writeEmptyResponse(output, true);

                    // Keep interrupting.
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public synchronized void destroy() {
        securityContext.asProcessingUser(() -> {
            if (coprocessors != null) {
                coprocessors.clear();
            }

            destroy = true;
            if (taskId != null) {
                taskManager.terminate(taskId);
            }
        });
    }

    public void setCoprocessors(final CoprocessorsImpl coprocessors) {
        this.coprocessors = coprocessors;
    }

    public synchronized void setTaskId(final TaskId taskId) {
        this.taskId = taskId;
        if (destroy) {
            taskManager.terminate(taskId);
            if (coprocessors != null) {
                coprocessors.clear();
            }
        }
    }

    public void setStarted(final boolean started) {
        this.started = started;
    }

    public void setInitialisationError(final List<ErrorMessage> initialisationError) {
        this.initialisationError = initialisationError;
    }
}
