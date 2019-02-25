package stroom;

import com.google.inject.AbstractModule;
import stroom.streamtask.StreamTaskJobsModule;

public class StroomCoreServerJobsModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new StreamTaskJobsModule());
    }
}
