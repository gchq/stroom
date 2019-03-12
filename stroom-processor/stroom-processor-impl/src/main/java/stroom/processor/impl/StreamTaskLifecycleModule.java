package stroom.processor.impl;

import stroom.lifecycle.api.AbstractLifecycleModule;
import stroom.lifecycle.api.RunnableWrapper;

import javax.inject.Inject;

public class StreamTaskLifecycleModule extends AbstractLifecycleModule {
    @Override
    protected void configure() {
        super.configure();
        bindStartup().to(ProcessorTaskManagerStartup.class);
        bindShutdown().to(ProcessorTaskManagerShutdown.class);
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
