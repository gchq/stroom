package stroom;

import com.google.inject.AbstractModule;
import stroom.data.store.DataRetentionJobModule;
import stroom.data.retention.DataRetentionJobsModule;
import stroom.streamtask.StreamTaskJobsModule;
import stroom.volume.VolumeJobsModule;

public class StroomCoreServerJobsModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new DataRetentionJobModule());
        install(new DataRetentionJobsModule());
        install(new StreamTaskJobsModule());
        install(new VolumeJobsModule());
    }
}
