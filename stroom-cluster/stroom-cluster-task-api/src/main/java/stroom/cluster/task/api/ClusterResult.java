package stroom.cluster.task.api;

import java.io.Serializable;

public class ClusterResult<R> implements Serializable {
    private ClusterTaskRef<R> clusterTaskRef;
    private R result;
    private Throwable throwable;
    private boolean success;

    public ClusterResult() {
    }

    /**
     * @param clusterTaskRef Reference to the cluster task and associated state.
     * @param result         The result of the remote task execution.
     * @param throwable      An exception that may have been thrown during remote task
     *                       execution in the result of task failure.
     * @param success        Whether or not the remote task executed successfully.
     */
    private ClusterResult(final ClusterTaskRef<R> clusterTaskRef,
                          final R result,
                          final Throwable throwable,
                          final boolean success) {
        this.clusterTaskRef = clusterTaskRef;
        this.result = result;
        this.throwable = throwable;
        this.success = success;
    }

    public static <R> ClusterResult<R> success(final ClusterTaskRef<R> clusterTaskRef,
                                               final R result) {
        return new ClusterResult<>(clusterTaskRef, result, null, true);
    }

    public static <R> ClusterResult<R> failure(final ClusterTaskRef<R> clusterTaskRef,
                                               final Throwable throwable) {
        return new ClusterResult<>(clusterTaskRef, null, throwable, false);
    }

    public ClusterTaskRef<R> getClusterTaskRef() {
        return clusterTaskRef;
    }

    public R getResult() {
        return result;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public boolean isSuccess() {
        return success;
    }
}
