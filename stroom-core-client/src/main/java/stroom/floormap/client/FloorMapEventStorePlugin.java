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

package stroom.floormap.client;

import stroom.core.client.ContentManager;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.document.client.DocumentPlugin;
import stroom.document.client.DocumentPluginEventManager;
import stroom.entity.client.presenter.DocPresenter;
import stroom.floormap.client.presenter.FloorMapEventStorePresenter;
import stroom.floormap.shared.FloorMapEventStoreDoc;
import stroom.floormap.shared.FloorMapEventStoreResource;
import stroom.planb.shared.AbstractPlanBSettings;
import stroom.security.client.api.ClientSecurityContext;
import stroom.task.client.TaskMonitorFactory;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;
import javax.inject.Singleton;

/// Makes a [FloorMapEventStoreDoc] creatable and openable from the explorer.
@Singleton
public class FloorMapEventStorePlugin extends DocumentPlugin<FloorMapEventStoreDoc> {

    private static final FloorMapEventStoreResource RESOURCE =
            GWT.create(FloorMapEventStoreResource.class);

    private final Provider<FloorMapEventStorePresenter> editorProvider;
    private final RestFactory restFactory;

    @Inject
    public FloorMapEventStorePlugin(
            final EventBus eventBus,
            final Provider<FloorMapEventStorePresenter> editorProvider,
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
                     final Consumer<FloorMapEventStoreDoc> resultConsumer,
                     final RestErrorHandler errorHandler,
                     final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.fetch(docRef.getUuid()))
                .onSuccess(resultConsumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    @Override
    public void save(final DocRef docRef,
                     final FloorMapEventStoreDoc document,
                     final Consumer<FloorMapEventStoreDoc> resultConsumer,
                     final RestErrorHandler errorHandler,
                     final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.update(document.getUuid(), document))
                .onSuccess(resultConsumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    @Override
    public String getType() {
        return FloorMapEventStoreDoc.TYPE;
    }

    @Override
    protected DocRef getDocRef(final FloorMapEventStoreDoc document) {
        return DocRefUtil.create(document);
    }

    @Override
    protected String getPreSaveError(final FloorMapEventStoreDoc doc) {
        // Expiry against retention is checked server side, where the saved document is to hand.
        return AbstractPlanBSettings.validationError(doc.getSettings());
    }
}
