package stroom.processor.task.client;

import stroom.core.client.ContentManager;
import stroom.data.client.AbstractTabPresenterPlugin;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.task.client.event.OpenProcessorTaskEvent;
import stroom.processor.task.client.presenter.ProcessorTaskPresenter;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Singleton;

@Singleton
public class ProcessorTaskPlugin extends AbstractTabPresenterPlugin<ProcessorFilter, ProcessorTaskPresenter> {

    @Inject
    public ProcessorTaskPlugin(final EventBus eventBus,
                               final ContentManager contentManager,
                               final Provider<ProcessorTaskPresenter> processorTaskPresenterProvider) {
        super(eventBus, contentManager, processorTaskPresenterProvider);

        registerHandler(getEventBus().addHandler(OpenProcessorTaskEvent.getType(), event -> {
            open(event.getProcessorFilter(), true);
        }));
    }

    public void open(final ProcessorFilter processorFilter, final boolean forceOpen) {
        if (processorFilter != null) {
            super.openTabPresenter(
                    forceOpen,
                    processorFilter,
                    processorTaskPresenter ->
                            processorTaskPresenter.setProcessorFilter(processorFilter));
        }
    }

    @Override
    protected String getName() {
        return "Tasks";
    }
}
