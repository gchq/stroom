package stroom.search.impl;

import stroom.query.common.v2.CompletionState;
import stroom.query.common.v2.CoprocessorSettingsMap;
import stroom.query.common.v2.Payload;
import stroom.search.coprocessor.Coprocessors;
import stroom.search.resultsender.NodeResult;
import stroom.task.api.TaskManager;
import stroom.task.shared.TaskId;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

class RemoteSearchResultFactory {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RemoteSearchResultFactory.class);

    private final TaskManager taskManager;

    private final CompletionState completionState = new CompletionState();
    private volatile Coprocessors coprocessors;
    private volatile LinkedBlockingQueue<String> errors;
    private volatile TaskId taskId;
    private volatile boolean destroy;
    private volatile boolean started;

    RemoteSearchResultFactory(final TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    public NodeResult create() {
        try {
            // Wait to complete.
            final boolean complete = completionState.awaitCompletion(1, TimeUnit.SECONDS);

            if (!started) {
                LOGGER.debug(() -> "Node search not started");
                return new NodeResult(null, null, false);
            } else if (Thread.currentThread().isInterrupted() || destroy) {
                LOGGER.debug(() -> "Terminated or destroyed: terminated=" +
                        Thread.currentThread().isInterrupted() +
                        ", destroyed=" +
                        destroy);
                return new NodeResult(null, null, true);
            }

            // Produce payloads for each coprocessor.
            final Map<CoprocessorSettingsMap.CoprocessorKey, Payload> payloadMap = coprocessors.createPayloads();

            // Drain all current errors to a list.
            final List<String> errorsSnapshot = new ArrayList<>();
            errors.drainTo(errorsSnapshot);
            LOGGER.debug(() -> "" +
                    "Result: payload=" +
                    payloadMap +
                    ", error=" +
                    errorsSnapshot +
                    ", complete=" +
                    complete);

            // Form a result to send back to the requesting node.
            return new NodeResult(payloadMap, errorsSnapshot, complete);

        } catch (final InterruptedException e) {
            LOGGER.debug(e::getMessage, e);
            // Keep interrupting.
            Thread.currentThread().interrupt();

            return new NodeResult(null, null, true);
        }
    }

    public synchronized void destroy() {
        destroy = true;
        if (taskId != null) {
            taskManager.terminate(taskId);
        }
    }

    public void setCoprocessors(final Coprocessors coprocessors) {
        this.coprocessors = coprocessors;
    }

    public void setErrors(final LinkedBlockingQueue<String> errors) {
        this.errors = errors;
    }

    public synchronized void setTaskId(final TaskId taskId) {
        this.taskId = taskId;
        if (destroy) {
            taskManager.terminate(taskId);
        }
    }

    public void setStarted(final boolean started) {
        this.started = started;
    }

    public void complete() {
        this.completionState.complete();
    }
}
