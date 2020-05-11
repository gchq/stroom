package stroom.processor.impl;

import stroom.lifecycle.api.LifecycleBinder;
import stroom.util.RunnableWrapper;

import com.google.inject.AbstractModule;

import javax.inject.Inject;

public class StreamTaskLifecycleModule extends AbstractModule {

    @Override
    protected void configure() {

        LifecycleBinder.create(binder())
                .bindStartupTaskTo(ProcessorTaskManagerStartup.class)
                .bindShutdownTaskTo(ProcessorTaskManagerShutdown.class);
    }

    private static class ProcessorTaskManagerStartup extends RunnableWrapper {
        @Inject
        ProcessorTaskManagerStartup(final ProcessorTaskManagerImpl processorTaskManager) {
            super(processorTaskManager::startup);
        }
    }

    private static class ProcessorTaskManagerShutdown extends RunnableWrapper {
        @Inject
        ProcessorTaskManagerShutdown(final ProcessorTaskManagerImpl processorTaskManager) {
            super(processorTaskManager::shutdown);
        }
    }
}
