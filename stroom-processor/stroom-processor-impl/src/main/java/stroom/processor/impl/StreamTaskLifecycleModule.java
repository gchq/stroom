package stroom.processor.impl;

import stroom.lifecycle.api.AbstractLifecycleModule;
import stroom.lifecycle.api.RunnableWrapper;

import javax.inject.Inject;

public class StreamTaskLifecycleModule extends AbstractLifecycleModule {
    @Override
    protected void configure() {
        super.configure();
        bindStartup().to(ProcessorFilterTaskCreatorStartup.class);
        bindShutdown().to(ProcessorFilterTaskCreatorShutdown.class);
    }

    private static class ProcessorFilterTaskCreatorStartup extends RunnableWrapper {
        @Inject
        ProcessorFilterTaskCreatorStartup(final ProcessorFilterTaskCreatorImpl processorFilterTaskCreator) {
            super(processorFilterTaskCreator::startup);
        }
    }

    private static class ProcessorFilterTaskCreatorShutdown extends RunnableWrapper {
        @Inject
        ProcessorFilterTaskCreatorShutdown(final ProcessorFilterTaskCreatorImpl processorFilterTaskCreator) {
            super(processorFilterTaskCreator::shutdown);
        }
    }
}
