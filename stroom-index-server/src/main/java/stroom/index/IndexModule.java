/*
 * Copyright 2018 Crown Copyright
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

package stroom.index;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import stroom.entity.FindService;
import stroom.entity.event.EntityEvent;
import stroom.entity.shared.Clearable;
import stroom.explorer.ExplorerActionHandler;
import stroom.importexport.ImportExportActionHandler;
import stroom.index.shared.Index;
import stroom.pipeline.factory.Element;
import stroom.task.TaskHandler;

public class IndexModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(IndexShardManager.class).to(IndexShardManagerImpl.class);
        bind(IndexShardWriterCache.class).to(IndexShardWriterCacheImpl.class);
        bind(IndexConfigCache.class).to(IndexConfigCacheImpl.class);
        bind(IndexService.class).to(IndexServiceImpl.class);
        bind(IndexVolumeService.class).to(IndexVolumeServiceImpl.class);
        bind(IndexShardService.class).to(IndexShardServiceImpl.class);
        bind(Indexer.class).to(IndexerImpl.class);

        final Multibinder<Clearable> clearableBinder = Multibinder.newSetBinder(binder(), Clearable.class);
        clearableBinder.addBinding().to(IndexConfigCacheImpl.class);

        final Multibinder<TaskHandler> taskHandlerBinder = Multibinder.newSetBinder(binder(), TaskHandler.class);
        taskHandlerBinder.addBinding().to(stroom.index.CloseIndexShardActionHandler.class);
        taskHandlerBinder.addBinding().to(stroom.index.DeleteIndexShardActionHandler.class);
        taskHandlerBinder.addBinding().to(stroom.index.FlushIndexShardActionHandler.class);
        taskHandlerBinder.addBinding().to(stroom.index.FetchIndexVolumesActionHandler.class);

        final Multibinder<EntityEvent.Handler> entityEventHandlerBinder = Multibinder.newSetBinder(binder(), EntityEvent.Handler.class);
        entityEventHandlerBinder.addBinding().to(IndexConfigCacheEntityEventHandler.class);

        final Multibinder<Element> elementBinder = Multibinder.newSetBinder(binder(), Element.class);
        elementBinder.addBinding().to(stroom.index.IndexingFilter.class);

        final Multibinder<ExplorerActionHandler> explorerActionHandlerBinder = Multibinder.newSetBinder(binder(), ExplorerActionHandler.class);
        explorerActionHandlerBinder.addBinding().to(stroom.index.IndexServiceImpl.class);

        final Multibinder<ImportExportActionHandler> importExportActionHandlerBinder = Multibinder.newSetBinder(binder(), ImportExportActionHandler.class);
        importExportActionHandlerBinder.addBinding().to(stroom.index.IndexServiceImpl.class);

        final MapBinder<String, Object> entityServiceByTypeBinder = MapBinder.newMapBinder(binder(), String.class, Object.class);
        entityServiceByTypeBinder.addBinding(Index.ENTITY_TYPE).to(stroom.index.IndexServiceImpl.class);

        final Multibinder<FindService> findServiceBinder = Multibinder.newSetBinder(binder(), FindService.class);
        findServiceBinder.addBinding().to(stroom.index.IndexServiceImpl.class);
        findServiceBinder.addBinding().to(stroom.index.IndexShardServiceImpl.class);
    }
}