package stroom.app.guice;

import com.google.inject.AbstractModule;
import stroom.StroomCoreServerJobsModule;
import stroom.benchmark.BenchmarkJobsModule;
import stroom.cache.impl.CacheJobsModule;
import stroom.cluster.lock.impl.db.ClusterLockJobsModule;
import stroom.config.global.impl.db.GlobalConfigJobsModule;
import stroom.dashboard.DashboardJobsModule;
import stroom.data.retention.impl.DataRetentionJobsModule;
import stroom.data.store.impl.DataRetentionJobModule;
import stroom.data.store.impl.fs.FileVolumeJobsModule;
import stroom.meta.impl.db.MetaDbJobsModule;
import stroom.data.store.impl.fs.FileSystemDataStoreJobsModule;
import stroom.index.IndexJobsModule;
import stroom.job.impl.JobSystemJobsModule;
import stroom.node.impl.NodeJobsModule;
import stroom.pipeline.PipelineJobsModule;
import stroom.pipeline.refdata.store.RefDataStoreJobsModule;
import stroom.resource.impl.ResourceJobsModule;
import stroom.search.SearchJobsModule;
import stroom.search.shard.ShardJobsModule;
import stroom.statistics.impl.sql.SQLStatisticsJobsModule;
import stroom.statistics.impl.sql.search.SQLStatisticSearchJobsModule;
import stroom.index.selection.VolumeJobsModule;

public class JobsModule extends AbstractModule {
    @Override
    protected void configure(){
        install(new BenchmarkJobsModule());
        install(new CacheJobsModule());
        install(new ClusterLockJobsModule());
        install(new DashboardJobsModule());
        install(new DataRetentionJobsModule());
        install(new DataRetentionJobModule());
        install(new StroomCoreServerJobsModule());
        install(new VolumeJobsModule());
        install(new FileVolumeJobsModule());
        install(new GlobalConfigJobsModule());
        install(new RefDataStoreJobsModule());
        install(new PipelineJobsModule());
        install(new ResourceJobsModule());
        install(new ShardJobsModule());
        install(new SearchJobsModule());
        install(new IndexJobsModule());
        install(new FileSystemDataStoreJobsModule());
        install(new SQLStatisticsJobsModule());
        install(new SQLStatisticSearchJobsModule());
        install(new MetaDbJobsModule());
        install(new JobSystemJobsModule());
        install(new NodeJobsModule());
    }
}
