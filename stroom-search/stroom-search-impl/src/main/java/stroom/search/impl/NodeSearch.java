package stroom.search.impl;

import stroom.task.api.TaskContext;

public interface NodeSearch {

    void searchNode(String sourceNode,
                    String targetNode,
                    FederatedSearchTask task,
                    NodeSearchTask nodeSearchTask,
                    TaskContext taskContext);
}
