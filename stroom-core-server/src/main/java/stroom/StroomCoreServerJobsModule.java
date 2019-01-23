package stroom;

import com.google.inject.AbstractModule;
import stroom.data.store.DataStoreJobModule;
import stroom.node.impl.NodeJobsModule;
import stroom.policy.PolicyJobsModule;
import stroom.resource.ResourceJobsModule;
import stroom.streamtask.StreamTaskJobsModule;
import stroom.volume.VolumeJobsModule;

public class StroomCoreServerJobsModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new DataStoreJobModule());
        install(new PolicyJobsModule());
        install(new StreamTaskJobsModule());
        install(new ResourceJobsModule());
        install(new VolumeJobsModule());
        install(new NodeJobsModule());
    }
}
