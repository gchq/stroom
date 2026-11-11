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

package stroom.externaldatasource.client;

import stroom.core.client.ContentManager;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.document.client.DocumentPlugin;
import stroom.document.client.DocumentPluginEventManager;
import stroom.entity.client.presenter.DocPresenter;
import stroom.externaldatasource.client.presenter.ExternalDataSourcePresenter;
import stroom.externaldatasource.shared.ExternalDataSourceDoc;
import stroom.externaldatasource.shared.ExternalDataSourceResource;
import stroom.security.client.api.ClientSecurityContext;
import stroom.task.client.TaskMonitorFactory;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;
import javax.inject.Singleton;

/// Opens, loads and saves External Data Source documents in the UI.
@Singleton
public class ExternalDataSourcePlugin extends DocumentPlugin<ExternalDataSourceDoc> {

    private static final ExternalDataSourceResource EXTERNAL_DATA_SOURCE_RESOURCE =
            GWT.create(ExternalDataSourceResource.class);

    private final Provider<ExternalDataSourcePresenter> editorProvider;
    private final RestFactory restFactory;

    /// @param eventBus                   The event bus.
    /// @param editorProvider             Makes the document's editor.
    /// @param restFactory                Calls the server.
    /// @param contentManager             Manages the open tabs.
    /// @param entityPluginEventManager   Handles document events.
    /// @param securityContext            The user's permissions.
    @Inject
    public ExternalDataSourcePlugin(final EventBus eventBus,
                                    final Provider<ExternalDataSourcePresenter> editorProvider,
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
                     final Consumer<ExternalDataSourceDoc> resultConsumer,
                     final RestErrorHandler errorHandler,
                     final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(EXTERNAL_DATA_SOURCE_RESOURCE)
                .method(res -> res.fetch(docRef.getUuid()))
                .onSuccess(resultConsumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    @Override
    public void save(final DocRef docRef,
                     final ExternalDataSourceDoc document,
                     final Consumer<ExternalDataSourceDoc> resultConsumer,
                     final RestErrorHandler errorHandler,
                     final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(EXTERNAL_DATA_SOURCE_RESOURCE)
                .method(res -> res.update(document.getUuid(), document))
                .onSuccess(resultConsumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    @Override
    public String getType() {
        return ExternalDataSourceDoc.TYPE;
    }

    @Override
    protected DocRef getDocRef(final ExternalDataSourceDoc document) {
        return DocRefUtil.create(document);
    }
}
