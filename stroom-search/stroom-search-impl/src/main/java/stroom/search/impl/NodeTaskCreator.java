package stroom.search.impl;

import stroom.query.api.v2.Query;
import stroom.task.api.TaskContext;

import java.util.Map;

public interface NodeTaskCreator {

    Map<String, NodeSearchTask> createNodeSearchTasks(FederatedSearchTask task,
                                                      Query query,
                                                      TaskContext parentContext);

}
