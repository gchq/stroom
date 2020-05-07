package stroom.app.guice;

import stroom.core.benchmark.BenchmarkJobsModule;
import stroom.core.receive.ProxyAggregationJobsModule;
import stroom.data.store.impl.DataRetentionJobModule;
import stroom.data.store.impl.fs.FsVolumeJobsModule;

import com.google.inject.AbstractModule;

public class JobsModule extends AbstractModule {
    @Override
    protected void configure() {

        // Job modules with no other obvious home
        install(new BenchmarkJobsModule());
        install(new DataRetentionJobModule());
        install(new FsVolumeJobsModule());
        install(new stroom.node.impl.NodeJobsModule());
        install(new ProxyAggregationJobsModule());
    }
}
