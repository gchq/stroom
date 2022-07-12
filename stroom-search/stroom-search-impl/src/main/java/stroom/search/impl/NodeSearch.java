package stroom.search.impl;

import stroom.cluster.api.ClusterMember;
import stroom.query.api.v2.Query;
import stroom.task.api.TaskContext;

import java.util.List;

public interface NodeSearch {

    void searchNode(ClusterMember sourceNode,
                    ClusterMember targetNode,
                    List<Long> shards,
                    AsyncSearchTask task,
                    Query query,
                    TaskContext taskContext);
}
