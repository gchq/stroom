package stroom;

import com.google.inject.AbstractModule;
import stroom.data.store.DataRetentionJobModule;
import stroom.data.retention.PolicyJobsModule;
import stroom.streamtask.StreamTaskJobsModule;
import stroom.volume.VolumeJobsModule;

public class StroomCoreServerJobsModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new DataRetentionJobModule());
        install(new PolicyJobsModule());
        install(new StreamTaskJobsModule());
        install(new VolumeJobsModule());
    }
}
