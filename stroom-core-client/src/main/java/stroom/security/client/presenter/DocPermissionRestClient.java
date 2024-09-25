package stroom.security.client.presenter;

import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.security.shared.DocPermissionResource;
import stroom.security.shared.DocumentUserPermissionsReport;
import stroom.security.shared.DocumentUserPermissionsRequest;
import stroom.util.shared.UserRef;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

public class DocPermissionRestClient extends AbstractRestClient {

    private static final DocPermissionResource DOC_PERMISSION_RESOURCE = GWT.create(DocPermissionResource.class);

    @Inject
    public DocPermissionRestClient(final EventBus eventBus,
                                   final RestFactory restFactory) {
        super(eventBus, restFactory);
    }

    public void getDocUserPermissionsReport(final DocRef docRef,
                                            final UserRef userRef,
                                            final Consumer<DocumentUserPermissionsReport> consumer) {
        // Fetch permissions.
        final DocumentUserPermissionsRequest request = new DocumentUserPermissionsRequest(docRef, userRef);
        restFactory
                .create(DOC_PERMISSION_RESOURCE)
                .method(res -> res.getDocUserPermissionsReport(request))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }
}
