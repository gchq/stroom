package stroom.processor.impl;

import stroom.lifecycle.api.AbstractLifecycleModule;
import stroom.lifecycle.api.RunnableWrapper;

import javax.inject.Inject;

public class StreamTaskLifecycleModule extends AbstractLifecycleModule {
    @Override
    protected void configure() {
        super.configure();
        bindStartup().to(ProcessorFilterTaskManagerStartup.class);
        bindShutdown().to(ProcessorFilterTaskManagerShutdown.class);
    }

    private static class ProcessorFilterTaskManagerStartup extends RunnableWrapper {
        @Inject
        ProcessorFilterTaskManagerStartup(final ProcessorFilterTaskManagerImpl processorFilterTaskManager) {
            super(processorFilterTaskManager::startup);
        }
    }

    private static class ProcessorFilterTaskManagerShutdown extends RunnableWrapper {
        @Inject
        ProcessorFilterTaskManagerShutdown(final ProcessorFilterTaskManagerImpl processorFilterTaskManager) {
            super(processorFilterTaskManager::shutdown);
        }
    }
}
