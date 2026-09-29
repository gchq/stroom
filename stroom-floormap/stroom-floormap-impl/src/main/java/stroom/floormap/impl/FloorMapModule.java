/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.floormap.impl;

import stroom.docstore.api.ContentIndexable;
import stroom.docstore.api.DocumentActionHandlerBinder;
import stroom.event.logging.api.ObjectInfoProviderBinder;
import stroom.explorer.api.ExplorerActionHandler;
import stroom.floormap.shared.FloorMapDoc;
import stroom.floormap.shared.FloorMapEventStoreDoc;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.planb.impl.PlanBDocumentTypes;
import stroom.query.api.datasource.DataSourceProvider;
import stroom.query.common.v2.IndexFieldProvider;
import stroom.query.common.v2.SearchProvider;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.RestResourcesBinder;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;

/// Guice dependency injection module for the floor map feature.
///
/// Binds the [FloorMapStore] implementation and registers the
/// explorer action handler, import/export handler, content indexer,
/// document action handler, event logging object info provider, and
/// REST resource for floor map documents.
public class FloorMapModule extends AbstractModule {

    @Override
    protected void configure() {

        bind(FloorMapStore.class).to(FloorMapStoreImpl.class);

        GuiceUtil.buildMultiBinder(binder(), ExplorerActionHandler.class)
                .addBinding(FloorMapStoreImpl.class);
        GuiceUtil.buildMultiBinder(binder(), ImportExportActionHandler.class)
                .addBinding(FloorMapStoreImpl.class);
        GuiceUtil.buildMultiBinder(binder(), ContentIndexable.class)
                .addBinding(FloorMapStoreImpl.class);

        DocumentActionHandlerBinder.create(binder())
                .bind(FloorMapDoc.TYPE, FloorMapStoreImpl.class);

        // Provide object info to the logging service.
        ObjectInfoProviderBinder.create(binder())
                .bind(FloorMapDoc.class, FloorMapDocObjectInfoProvider.class);

        RestResourcesBinder.create(binder())
                .bind(FloorMapResourceImpl.class)
                .bind(FloorMapEventStoreResourceImpl.class);

        bindEventStore();
    }

    /// The FloorMap Event Store: a document of ours describing a Plan B store.
    ///
    /// The [PlanBDocumentTypes] multibinding is the load-bearing line. It is what tells
    /// `PlanBDocCache` that documents of this type are Plan B stores, so a pipeline writing to
    /// a map name resolves one exactly as it resolves a `PlanBDoc`, and `ShardManager`
    /// finds it when deciding whether a shard is still live. Without it the document would persist
    /// and display but nothing could ever write to it, and its shard would be swept as an orphan.
    /// `PathwaysModule` registers `TracesDoc` the same way.
    private void bindEventStore() {
        bind(FloorMapEventStoreStore.class).to(FloorMapEventStoreStoreImpl.class);

        Multibinder.newSetBinder(binder(), String.class, PlanBDocumentTypes.class)
                .addBinding()
                .toInstance(FloorMapEventStoreDoc.TYPE);

        GuiceUtil.buildMultiBinder(binder(), ExplorerActionHandler.class)
                .addBinding(FloorMapEventStoreStoreImpl.class);
        GuiceUtil.buildMultiBinder(binder(), ImportExportActionHandler.class)
                .addBinding(FloorMapEventStoreStoreImpl.class);

        DocumentActionHandlerBinder.create(binder())
                .bind(FloorMapEventStoreDoc.TYPE, FloorMapEventStoreStoreImpl.class);

        // The read. Plan B's StateSearchProvider answers for PlanBDoc.TYPE only, and providers are
        // resolved by document type, so our type needs ours - which is what lets the read mode be
        // stated by the caller rather than inferred from the shape of the expression.
        GuiceUtil.buildMultiBinder(binder(), DataSourceProvider.class)
                .addBinding(FloorMapEventStoreSearchProvider.class);
        GuiceUtil.buildMultiBinder(binder(), SearchProvider.class)
                .addBinding(FloorMapEventStoreSearchProvider.class);
        GuiceUtil.buildMultiBinder(binder(), IndexFieldProvider.class)
                .addBinding(FloorMapEventStoreSearchProvider.class);
    }
}
