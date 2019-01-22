package stroom.streamtask;

import stroom.lifecycle.api.AbstractLifecycleModule;
import stroom.lifecycle.api.RunnableWrapper;

import javax.inject.Inject;

public class StreamTaskLifecycleModule extends AbstractLifecycleModule {
    @Override
    protected void configure() {
        super.configure();
        bindStartup().to(StreamTaskCreatorStartup.class);
        bindShutdown().to(StreamTaskCreatorShutdown.class);
    }

    private static class StreamTaskCreatorStartup extends RunnableWrapper {
        @Inject
        StreamTaskCreatorStartup(final StreamTaskCreatorImpl streamTaskCreator) {
            super(streamTaskCreator::startup);
        }
    }

    private static class StreamTaskCreatorShutdown extends RunnableWrapper {
        @Inject
        StreamTaskCreatorShutdown(final StreamTaskCreatorImpl streamTaskCreator) {
            super(streamTaskCreator::shutdown);
        }
    }
}
