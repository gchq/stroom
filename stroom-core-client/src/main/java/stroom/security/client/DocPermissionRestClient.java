/*
 * Copyright 2024 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.security.client;

import stroom.dispatch.client.AbstractRestClient;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.security.shared.DocPermissionResource;
import stroom.security.shared.DocumentUserPermissionsReport;
import stroom.security.shared.DocumentUserPermissionsRequest;
import stroom.util.shared.UserRef;
import stroom.widget.task.client.TaskMonitorFactory;

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
                                            final Consumer<DocumentUserPermissionsReport> consumer,
                                            final TaskMonitorFactory taskMonitorFactory) {
        getDocUserPermissionsReport(docRef, userRef, consumer, null, taskMonitorFactory);
    }

    /// Fetches what permissions a user has on a document, and how.
    ///
    /// @param docRef             The document.
    /// @param userRef            The user.
    /// @param consumer           Given the report.
    /// @param errorHandler       Told of a failure, or `null` for the default (an alert).
    /// @param taskMonitorFactory Shows the fetch's progress.
    public void getDocUserPermissionsReport(final DocRef docRef,
                                            final UserRef userRef,
                                            final Consumer<DocumentUserPermissionsReport> consumer,
                                            final RestErrorHandler errorHandler,
                                            final TaskMonitorFactory taskMonitorFactory) {
        // Fetch permissions.
        final DocumentUserPermissionsRequest request = new DocumentUserPermissionsRequest(docRef, userRef);
        restFactory
                .create(DOC_PERMISSION_RESOURCE)
                .method(res -> res.getDocUserPermissionsReport(request))
                .onSuccess(consumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }
}
