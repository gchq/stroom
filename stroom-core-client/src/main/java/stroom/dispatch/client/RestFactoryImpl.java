package stroom.dispatch.client;

import stroom.task.client.TaskEndEvent;
import stroom.task.client.TaskStartEvent;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import org.fusesource.restygwt.client.Defaults;
import org.fusesource.restygwt.client.Dispatcher;

class RestFactoryImpl implements RestFactory, HasHandlers {

    private final EventBus eventBus;

    @Inject
    public RestFactoryImpl(final EventBus eventBus, final Dispatcher dispatcher) {
        this.eventBus = eventBus;

        String hostPageBaseUrl = GWT.getHostPageBaseURL();
        hostPageBaseUrl = hostPageBaseUrl.substring(0, hostPageBaseUrl.lastIndexOf("/"));
        hostPageBaseUrl = hostPageBaseUrl.substring(0, hostPageBaseUrl.lastIndexOf("/"));
        final String apiUrl = hostPageBaseUrl + "/api/";
        Defaults.setServiceRoot(apiUrl);
        Defaults.setDispatcher(dispatcher);
    }

    @Override
    public <R> Rest<R> create() {
        return new RestImpl<>(this);
    }

    @Override
    public <R> Rest<R> createQuiet() {
        return new QuietRestImpl<>(this);
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEvent(event);
    }

    @Override
    public String getImportFileURL() {
        return GWT.getHostPageBaseURL() + "importfile.rpc";
    }

    private static class RestImpl<R> extends AbstractRest<R> {

        private final HasHandlers hasHandlers;

        RestImpl(final HasHandlers hasHandlers) {
            super(hasHandlers);
            this.hasHandlers = hasHandlers;
        }

        @Override
        protected void incrementTaskCount() {
            // Add the task to the map.
            TaskStartEvent.fire(hasHandlers);
        }

        @Override
        protected void decrementTaskCount() {
            // Remove the task from the task count.
            TaskEndEvent.fire(hasHandlers);
        }
    }

    private static class QuietRestImpl<R> extends AbstractRest<R> {

        QuietRestImpl(final HasHandlers hasHandlers) {
            super(hasHandlers);
        }

        @Override
        protected void incrementTaskCount() {
            // Do nothing.
        }

        @Override
        protected void decrementTaskCount() {
            // Do nothing.
        }
    }
}
