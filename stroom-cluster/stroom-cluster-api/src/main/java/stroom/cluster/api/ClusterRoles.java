package stroom.cluster.api;

import java.util.List;

public class ClusterRoles {

    public static final ClusterRole DATA_RETENTION =
            new ClusterRole("CLUSTER_ROLE_DATA_RETENTION");
    public static final ClusterRole SQL_STATISTIC_AGGREGATION =
            new ClusterRole("CLUSTER_ROLE_SQL_STATISTIC_AGGREGATION");
    public static final ClusterRole SOLR_INDEX_RETENTION =
            new ClusterRole("CLUSTER_ROLE_SOLR_INDEX_RETENTION");
    public static final ClusterRole SOLR_INDEX_OPTIMISE =
            new ClusterRole("CLUSTER_ROLE_SOLR_INDEX_OPTIMISE");
    public static final ClusterRole ELASTIC_INDEX_RETENTION =
            new ClusterRole("CLUSTER_ROLE_ELASTIC_INDEX_RETENTION");
    public static final ClusterRole PROCESSOR_TASK_DELETE =
            new ClusterRole("CLUSTER_ROLE_PROCESSOR_TASK_DELETE");
    public static final ClusterRole META_DELETE =
            new ClusterRole("CLUSTER_ROLE_META_DELETE");
    public static final ClusterRole PHYSICAL_FILE_DELETE =
            new ClusterRole("CLUSTER_ROLE_PHYSICAL_FILE_DELETE");
    public static final ClusterRole REFRESH_FS_VOLUMES =
            new ClusterRole("CLUSTER_ROLE_REFRESH_FS_VOLUMES");
    public static final ClusterRole DATA_DELETE =
            new ClusterRole("CLUSTER_ROLE_DATA_DELETE");
    public static final ClusterRole FIND_ORPHAN_FILES =
            new ClusterRole("CLUSTER_ROLE_FIND_ORPHAN_FILES");

    public static final List<ClusterRole> ALL_ROLES = List.of(
            DATA_RETENTION,
            SQL_STATISTIC_AGGREGATION,
            SOLR_INDEX_RETENTION,
            SOLR_INDEX_OPTIMISE,
            ELASTIC_INDEX_RETENTION,
            PROCESSOR_TASK_DELETE,
            META_DELETE,
            PHYSICAL_FILE_DELETE,
            REFRESH_FS_VOLUMES,
            DATA_DELETE,
            FIND_ORPHAN_FILES);
}
