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

package stroom.floormap.client;

import stroom.core.client.ContentManager;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.document.client.DocInitialisationHandler;
import stroom.document.client.DocumentPlugin;
import stroom.document.client.DocumentPluginEventManager;
import stroom.entity.client.presenter.DocPresenter;
import stroom.floormap.client.presenter.FloorMapInitPresenter;
import stroom.floormap.client.presenter.FloorMapPresenter;
import stroom.floormap.shared.FloorMapDoc;
import stroom.floormap.shared.FloorMapResource;
import stroom.security.client.api.ClientSecurityContext;
import stroom.task.client.TaskMonitorFactory;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.inject.Singleton;

/// Document plugin for [FloorMapDoc] documents.
/// Handles loading and saving floor map documents via the REST API
/// and provides the editor presenter for the content manager.
@Singleton
public class FloorMapPlugin extends DocumentPlugin<FloorMapDoc> {

    private static final FloorMapResource FLOOR_MAP_RESOURCE = GWT.create(FloorMapResource.class);

    private final Provider<FloorMapPresenter> editorProvider;
    private final Provider<FloorMapInitPresenter> initPresenterProvider;
    private final RestFactory restFactory;

    @Inject
    public FloorMapPlugin(final EventBus eventBus,
                          final Provider<FloorMapPresenter> editorProvider,
                          final Provider<FloorMapInitPresenter> initPresenterProvider,
                          final RestFactory restFactory,
                          final ContentManager contentManager,
                          final DocumentPluginEventManager entityPluginEventManager,
                          final ClientSecurityContext securityContext) {
        super(eventBus, contentManager, entityPluginEventManager, securityContext);
        this.editorProvider = editorProvider;
        this.initPresenterProvider = initPresenterProvider;
        this.restFactory = restFactory;
    }

    /// {@inheritDoc}
    @Override
    protected DocPresenter<?, ?> createEditor() {
        return editorProvider.get();
    }

    /// Loads a [FloorMapDoc] from the server by fetching it via the
    /// floor map REST resource.
    ///
    /// @param docRef             the document reference to load
    /// @param resultConsumer     callback for the loaded document
    /// @param errorHandler       callback for REST errors
    /// @param taskMonitorFactory factory for task progress monitoring
    @Override
    public void load(final DocRef docRef,
                     final Consumer<FloorMapDoc> resultConsumer,
                     final RestErrorHandler errorHandler,
                     final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(FLOOR_MAP_RESOURCE)
                .method(res -> res.fetch(docRef.getUuid()))
                .onSuccess(resultConsumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    /// Legacy save method — must not be called.
    /// Floor map documents use the overload that accepts a `postSaveCallback`
    /// for flushing pending temporal-store changes after the document is persisted.
    ///
    /// @throws IllegalStateException always
    @Override
    public void save(final DocRef docRef,
                     final FloorMapDoc document,
                     final Consumer<FloorMapDoc> resultConsumer,
                     final RestErrorHandler errorHandler,
                     final TaskMonitorFactory taskMonitorFactory) {

        throw new IllegalStateException("Old save method called in FloorMapPlugin");
    }

    /// Saves a [FloorMapDoc] to the server, then invokes the
    /// `postSaveCallback` to allow the caller to flush pending
    /// temporal-store changes before notifying the result consumer.
    ///
    /// @param docRef             the document reference
    /// @param document           the document to persist
    /// @param postSaveCallback   callback invoked after a successful save;
    ///         receives the saved doc and the result consumer
    /// @param resultConsumer     final callback for the saved document
    /// @param errorHandler       callback for REST errors
    /// @param taskMonitorFactory factory for task progress monitoring
    @Override
    public void save(final DocRef docRef,
                     final FloorMapDoc document,
                     final BiConsumer<FloorMapDoc, Consumer<FloorMapDoc>> postSaveCallback,
                     final Consumer<FloorMapDoc> resultConsumer,
                     final RestErrorHandler errorHandler,
                     final TaskMonitorFactory taskMonitorFactory) {

        // Sanity check before everything goes async
        Objects.requireNonNull(postSaveCallback);
        Objects.requireNonNull(resultConsumer);

        restFactory
                .create(FLOOR_MAP_RESOURCE)
                .method(res -> res.update(document.getUuid(), document))
                .onSuccess(doc -> postSaveCallback.accept(doc, resultConsumer))
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    @Override
    public String getType() {
        return FloorMapDoc.TYPE;
    }

    /// {@inheritDoc}
    ///
    /// Returns a [FloorMapInitPresenter] that prompts the user
    /// to select Facts Store and Events Store references before the
    /// FloorMap editor opens.
    ///
    /// @return a non-null initialisation handler; each call returns a
    ///         new instance from the injected provider
    @Override
    public DocInitialisationHandler getInitialisationHandler() {
        return initPresenterProvider.get();
    }

    @Override
    protected DocRef getDocRef(final FloorMapDoc document) {
        return DocRefUtil.create(document);
    }
}
