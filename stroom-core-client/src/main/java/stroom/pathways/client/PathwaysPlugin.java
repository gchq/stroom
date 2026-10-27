/*
 * Copyright 2019 Crown Copyright
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

package stroom.pathways.client;

import stroom.core.client.ContentManager;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.document.client.DocumentPlugin;
import stroom.document.client.DocumentPluginEventManager;
import stroom.entity.client.component.DocumentEditorHost;
import stroom.pathways.client.component.PathwaysEditor;
import stroom.pathways.shared.PathwaysDoc;
import stroom.pathways.shared.PathwaysResource;
import stroom.security.client.api.ClientSecurityContext;
import stroom.task.client.TaskMonitorFactory;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;
import javax.inject.Singleton;

@Singleton
public class PathwaysPlugin extends DocumentPlugin<PathwaysDoc> {

    private static final PathwaysResource PATHWAYS_RESOURCE = GWT.create(PathwaysResource.class);

    private final EventBus eventBus;
    private final Provider<PathwaysEditor> editorProvider;
    private final RestFactory restFactory;

    @Inject
    public PathwaysPlugin(final EventBus eventBus,
                          final Provider<PathwaysEditor> editorProvider,
                          final RestFactory restFactory,
                          final ContentManager contentManager,
                          final DocumentPluginEventManager entityPluginEventManager,
                          final ClientSecurityContext securityContext) {
        super(eventBus, contentManager, entityPluginEventManager, securityContext);
        this.eventBus = eventBus;
        this.editorProvider = editorProvider;
        this.restFactory = restFactory;
    }

    @Override
    protected DocumentEditorHost<?> createEditor() {
        return new DocumentEditorHost<>(eventBus, editorProvider.get(), PathwaysDoc.TYPE);
    }

    @Override
    public void load(final DocRef docRef,
                     final Consumer<PathwaysDoc> resultConsumer,
                     final RestErrorHandler errorHandler,
                     final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(PATHWAYS_RESOURCE)
                .method(res -> res.fetch(docRef.getUuid()))
                .onSuccess(resultConsumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    @Override
    public void save(final DocRef docRef,
                     final PathwaysDoc document,
                     final Consumer<PathwaysDoc> resultConsumer,
                     final RestErrorHandler errorHandler,
                     final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(PATHWAYS_RESOURCE)
                .method(res -> res.update(document.getUuid(), document))
                .onSuccess(resultConsumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    @Override
    public String getType() {
        return PathwaysDoc.TYPE;
    }

    @Override
    protected DocRef getDocRef(final PathwaysDoc document) {
        return DocRefUtil.create(document);
    }
}

