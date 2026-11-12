/*
 * Copyright 2026 Crown Copyright
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

package stroom.dataconnection.client;

import stroom.core.client.ContentManager;
import stroom.dataconnection.client.presenter.DataConnectionPresenter;
import stroom.dataconnection.shared.DataConnectionDoc;
import stroom.dataconnection.shared.DataConnectionResource;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.document.client.DocumentPlugin;
import stroom.document.client.DocumentPluginEventManager;
import stroom.entity.client.presenter.DocPresenter;
import stroom.security.client.api.ClientSecurityContext;
import stroom.task.client.TaskMonitorFactory;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;
import javax.inject.Singleton;

/// Opens, loads and saves Data Connection documents in the UI.
@Singleton
public class DataConnectionPlugin extends DocumentPlugin<DataConnectionDoc> {

    private static final DataConnectionResource DATA_CONNECTION_RESOURCE =
            GWT.create(DataConnectionResource.class);

    private final Provider<DataConnectionPresenter> editorProvider;
    private final RestFactory restFactory;

    /// @param eventBus                   The event bus.
    /// @param editorProvider             Makes the document's editor.
    /// @param restFactory                Calls the server.
    /// @param contentManager             Manages the open tabs.
    /// @param entityPluginEventManager   Handles document events.
    /// @param securityContext            The user's permissions.
    @Inject
    public DataConnectionPlugin(final EventBus eventBus,
                                    final Provider<DataConnectionPresenter> editorProvider,
                                    final RestFactory restFactory,
                                    final ContentManager contentManager,
                                    final DocumentPluginEventManager entityPluginEventManager,
                                    final ClientSecurityContext securityContext) {
        super(eventBus, contentManager, entityPluginEventManager, securityContext);
        this.editorProvider = editorProvider;
        this.restFactory = restFactory;
    }

    @Override
    protected DocPresenter<?, ?> createEditor() {
        return editorProvider.get();
    }

    @Override
    public void load(final DocRef docRef,
                     final Consumer<DataConnectionDoc> resultConsumer,
                     final RestErrorHandler errorHandler,
                     final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(DATA_CONNECTION_RESOURCE)
                .method(res -> res.fetch(docRef.getUuid()))
                .onSuccess(resultConsumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    @Override
    public void save(final DocRef docRef,
                     final DataConnectionDoc document,
                     final Consumer<DataConnectionDoc> resultConsumer,
                     final RestErrorHandler errorHandler,
                     final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(DATA_CONNECTION_RESOURCE)
                .method(res -> res.update(document.getUuid(), document))
                .onSuccess(resultConsumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    @Override
    public String getType() {
        return DataConnectionDoc.TYPE;
    }

    @Override
    protected DocRef getDocRef(final DataConnectionDoc document) {
        return DocRefUtil.create(document);
    }
}
