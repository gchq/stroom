package stroom.security.client.presenter;

import stroom.dispatch.client.DefaultErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.explorer.shared.ExplorerResource;
import stroom.security.shared.BulkDocumentPermissionChangeRequest;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

public class ExplorerClient extends AbstractRestClient {

    private static final ExplorerResource EXPLORER_RESOURCE = GWT.create(ExplorerResource.class);

    @Inject
    public ExplorerClient(final EventBus eventBus,
                          final RestFactory restFactory) {
        super(eventBus, restFactory);
    }

    public void changeDocumentPermssions(final BulkDocumentPermissionChangeRequest request,
                                         final Consumer<Boolean> consumer) {
        restFactory
                .create(EXPLORER_RESOURCE)
                .method(res -> res.changeDocumentPermissions(request))
                .onSuccess(consumer)
                .onFailure(new DefaultErrorHandler(this, () -> consumer.accept(false)))
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }
}
