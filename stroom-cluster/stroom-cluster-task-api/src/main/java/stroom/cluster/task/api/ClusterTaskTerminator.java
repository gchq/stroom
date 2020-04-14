package stroom.cluster.task.api;

import stroom.task.shared.TaskId;

public interface ClusterTaskTerminator {
    void terminate(String searchName, TaskId ancestorId, String taskName);
}
