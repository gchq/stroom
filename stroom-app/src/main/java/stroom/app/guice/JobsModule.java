package stroom.app.guice;

import com.google.inject.AbstractModule;
import stroom.cache.impl.CacheJobsModule;
import stroom.cluster.lock.impl.db.ClusterLockJobsModule;
import stroom.config.global.impl.GlobalConfigJobsModule;
import stroom.core.benchmark.BenchmarkJobsModule;
import stroom.core.receive.ProxyAggregationJobsModule;
import stroom.data.retention.impl.DataRetentionJobsModule;
import stroom.data.store.impl.DataRetentionJobModule;
import stroom.data.store.impl.fs.FsDataStoreJobsModule;
import stroom.data.store.impl.fs.FsVolumeJobsModule;
import stroom.index.impl.IndexJobsModule;
import stroom.index.impl.IndexVolumeJobsModule;
import stroom.meta.impl.MetaDbJobsModule;
import stroom.pipeline.PipelineJobsModule;
import stroom.pipeline.refdata.store.RefDataStoreJobsModule;
import stroom.processor.impl.ProcessorTaskJobsModule;
import stroom.resource.impl.ResourceJobsModule;
import stroom.search.impl.SearchJobsModule;
import stroom.search.impl.shard.ShardJobsModule;
import stroom.statistics.impl.sql.SQLStatisticsJobsModule;
import stroom.statistics.impl.sql.search.SQLStatisticSearchJobsModule;
import stroom.storedquery.impl.StoredQueryJobsModule;

public class JobsModule extends AbstractModule {
    @Override
    protected void configure(){
        install(new BenchmarkJobsModule());
        install(new CacheJobsModule());
        install(new ClusterLockJobsModule());
        install(new DataRetentionJobModule());
        install(new DataRetentionJobsModule());
        install(new FsDataStoreJobsModule());
        install(new FsVolumeJobsModule());
        install(new GlobalConfigJobsModule());
        install(new IndexJobsModule());
        install(new PipelineJobsModule());
        install(new RefDataStoreJobsModule());
        install(new ResourceJobsModule());
        install(new SearchJobsModule());
        install(new ShardJobsModule());
        install(new SQLStatisticSearchJobsModule());
        install(new SQLStatisticsJobsModule());
        install(new StoredQueryJobsModule());
        install(new stroom.job.impl.JobSystemJobsModule());
        install(new MetaDbJobsModule());
        install(new stroom.node.impl.NodeJobsModule());
        install(new ProcessorTaskJobsModule());
        install(new ProxyAggregationJobsModule());
        install(new IndexVolumeJobsModule());
    }
}
