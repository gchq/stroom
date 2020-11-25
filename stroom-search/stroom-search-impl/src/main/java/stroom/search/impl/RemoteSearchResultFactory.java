package stroom.search.impl;

import stroom.query.common.v2.CompletionState;
import stroom.query.common.v2.Coprocessors;
import stroom.query.common.v2.NodeResultSerialiser;
import stroom.task.api.TaskManager;
import stroom.task.shared.TaskId;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.esotericsoftware.kryo.io.Output;

import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

class RemoteSearchResultFactory {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RemoteSearchResultFactory.class);

    private final TaskManager taskManager;

    private final CompletionState completionState = new CompletionState();
    private volatile Coprocessors coprocessors;
    private volatile TaskId taskId;
    private volatile boolean destroy;
    private volatile boolean started;

    RemoteSearchResultFactory(final TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    public void write(final OutputStream outputStream) {
        try (final Output output = new Output(outputStream)) {
            try {
                // Wait to complete.
                final boolean complete = completionState.awaitCompletion(1, TimeUnit.SECONDS);

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
                    final List<String> errorsSnapshot = coprocessors.getErrorConsumer().drain();

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

    public synchronized void destroy() {
        destroy = true;
        if (taskId != null) {
            taskManager.terminate(taskId);
        }
    }

    public void setCoprocessors(final Coprocessors coprocessors) {
        this.coprocessors = coprocessors;
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
