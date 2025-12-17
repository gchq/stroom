/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.security.client.presenter;

import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.AnnotationTag;
import stroom.credentials.shared.Credentials;
import stroom.dispatch.client.DefaultErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.explorer.shared.PermissionChangeResource;
import stroom.security.shared.SingleDocumentPermissionChangeRequest;
import stroom.task.client.TaskMonitorFactory;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Set;
import java.util.function.Consumer;

public class PermissionChangeClient extends AbstractRestClient {

    /**
     * The document types that this class handles.
     */
    private static final Set<String> SUPPORTED_TYPES = Set.of(Annotation.TYPE,
            AnnotationTag.TYPE,
            Credentials.TYPE);

    /** Resource to talk to */
    private static final PermissionChangeResource PERMISSION_CHANGE_RESOURCE =
            GWT.create(PermissionChangeResource.class);

    @Inject
    public PermissionChangeClient(final EventBus eventBus,
                                  final RestFactory restFactory) {
        super(eventBus, restFactory);
    }

    /**
     * Changes document permissions for non-explorer document types.
     */
    public void changeDocumentPermissions(final SingleDocumentPermissionChangeRequest request,
                                          final Consumer<Boolean> consumer,
                                          final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(PERMISSION_CHANGE_RESOURCE)
                .method(res -> res.changeDocumentPermissions(request))
                .onSuccess(consumer)
                .onFailure(new DefaultErrorHandler(this, () -> consumer.accept(false)))
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    /**
     * Returns true if the given docType is supported by this class.
     * @param docType The docType to check.
     * @return true if you should use this class, false if not.
     */
    public boolean handlesType(final String docType) {
        return SUPPORTED_TYPES.contains(docType);
    }

}
