package stroom.cluster.task.api;

import stroom.task.shared.TaskId;

import java.io.Serializable;

public class ClusterTaskRef<R> implements Serializable {
    private ClusterTask<R> task;
    private String sourceNode;
    private String targetNode;
    private TaskId sourceTaskId;
    private CollectorId collectorId;

    public ClusterTaskRef() {
    }

    /**
     * This method receives results from worker nodes that have executed tasks
     * using the <code>execAsync()</code> method above. Received results are
     * processed by the named collector in a new task thread so that result
     * consumption does not hold on to the HTTP connection.
     *
     * @param task         The task that was executed on the target worker node.
     * @param targetNode   The worker node that is returning the result.
     * @param sourceTaskId The id of the parent task that owns this worker cluster task.
     * @param collectorId  The id of the collector to send results back to.
     */
    public ClusterTaskRef(final ClusterTask<R> task,
                          final String sourceNode,
                          final String targetNode,
                          final TaskId sourceTaskId,
                          final CollectorId collectorId) {
        this.task = task;
        this.sourceNode = sourceNode;
        this.targetNode = targetNode;
        this.sourceTaskId = sourceTaskId;
        this.collectorId = collectorId;
    }

    public ClusterTask<R> getTask() {
        return task;
    }

    public String getSourceNode() {
        return sourceNode;
    }

    public String getTargetNode() {
        return targetNode;
    }

    public TaskId getSourceTaskId() {
        return sourceTaskId;
    }

    public CollectorId getCollectorId() {
        return collectorId;
    }
}