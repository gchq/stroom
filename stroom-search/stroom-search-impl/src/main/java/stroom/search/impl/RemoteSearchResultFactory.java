package stroom.search.impl;

import stroom.query.common.v2.CompletionState;
import stroom.query.common.v2.CoprocessorSettingsMap;
import stroom.query.common.v2.Payload;
import stroom.search.coprocessor.Coprocessors;
import stroom.search.resultsender.NodeResult;
import stroom.task.api.TaskManager;
import stroom.task.shared.TaskId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

class RemoteSearchResultFactory {
    private final TaskManager taskManager;

    private volatile Coprocessors coprocessors;
    private volatile CompletionState searchComplete;
    private volatile LinkedBlockingQueue<String> errors;
    private volatile TaskId taskId;
    private volatile boolean destroy;
    private volatile boolean started;

    RemoteSearchResultFactory(final TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    public NodeResult create() {
        if (!started) {
            return new NodeResult(Collections.emptyMap(), Collections.emptyList(), false);
        } else if (Thread.currentThread().isInterrupted()) {
            return new NodeResult(null, null, true);
        }

//        taskContext.setName("Search Result Sender");
//        taskContext.info("Creating search result");

        // Find out if searching is complete.
        final boolean complete = searchComplete.isComplete();

        // Produce payloads for each coprocessor.
        final Map<CoprocessorSettingsMap.CoprocessorKey, Payload> payloadMap = coprocessors.createPayloads();

        // Drain all current errors to a list.
        List<String> errorsSnapshot = new ArrayList<>();
        errors.drainTo(errorsSnapshot);
        if (errorsSnapshot.size() == 0) {
            errorsSnapshot = null;
        }

        // Form a result to send back to the requesting node.
        final NodeResult result = new NodeResult(payloadMap, errorsSnapshot, complete);

//        // Give the result to the callback.
//        taskContext.info("Delivering search result");
        return result;
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

    public void setSearchComplete(final CompletionState searchComplete) {
        this.searchComplete = searchComplete;
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
}
