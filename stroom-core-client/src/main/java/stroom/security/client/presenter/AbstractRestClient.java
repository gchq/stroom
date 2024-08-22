package stroom.security.client.presenter;

import stroom.dispatch.client.DefaultErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.explorer.shared.ExplorerResource;
import stroom.security.shared.BulkDocumentPermissionChangeRequest;
import stroom.task.client.TaskListener;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

public abstract class AbstractRestClient implements HasHandlers {

    protected final EventBus eventBus;
    protected final RestFactory restFactory;
    protected TaskListener taskListener;

    public AbstractRestClient(final EventBus eventBus,
                              final RestFactory restFactory) {
        this.eventBus = eventBus;
        this.restFactory = restFactory;
    }

    public void setTaskListener(final TaskListener taskListener) {
        this.taskListener = taskListener;
    }

    @Override
    public void fireEvent(final GwtEvent<?> gwtEvent) {
        eventBus.fireEvent(gwtEvent);
    }
}
