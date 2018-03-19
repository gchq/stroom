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

package stroom.streamstore;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import stroom.entity.CachingEntityManager;
import stroom.entity.FindService;
import stroom.entity.shared.Clearable;
import stroom.feed.shared.Feed;
import stroom.jobsystem.ClusterLockServiceTransactionHelperImpl;
import stroom.streamstore.shared.StreamType;
import stroom.task.TaskHandler;

import javax.inject.Named;

public class StreamStoreModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(StreamAttributeKeyService.class).to(StreamAttributeKeyServiceImpl.class);
        bind(StreamAttributeMapService.class).to(StreamAttributeMapServiceImpl.class);
        bind(StreamAttributeValueFlush.class).to(StreamAttributeValueFlushImpl.class);
        bind(StreamAttributeValueService.class).to(StreamAttributeValueServiceImpl.class);
        bind(StreamTypeService.class).to(StreamTypeServiceImpl.class);

        final Multibinder<Clearable> clearableBinder = Multibinder.newSetBinder(binder(), Clearable.class);
        clearableBinder.addBinding().to(StreamAttributeKeyServiceImpl.class);
        clearableBinder.addBinding().to(StreamAttributeValueFlushImpl.class);

        final Multibinder<TaskHandler> taskHandlerBinder = Multibinder.newSetBinder(binder(), TaskHandler.class);
        taskHandlerBinder.addBinding().to(stroom.streamstore.DownloadDataHandler.class);
        taskHandlerBinder.addBinding().to(stroom.streamstore.FetchFieldsHandler.class);
        taskHandlerBinder.addBinding().to(stroom.streamstore.StreamDownloadTaskHandler.class);
        taskHandlerBinder.addBinding().to(stroom.streamstore.StreamUploadTaskHandler.class);
        taskHandlerBinder.addBinding().to(stroom.streamstore.UploadDataHandler.class);

        final MapBinder<String, Object> entityServiceByTypeBinder = MapBinder.newMapBinder(binder(), String.class, Object.class);
        entityServiceByTypeBinder.addBinding(StreamType.ENTITY_TYPE).to(stroom.streamstore.StreamTypeServiceImpl.class);

        final Multibinder<FindService> findServiceBinder = Multibinder.newSetBinder(binder(), FindService.class);
        findServiceBinder.addBinding().to(stroom.streamstore.StreamTypeServiceImpl.class);
        findServiceBinder.addBinding().to(stroom.streamstore.StreamAttributeMapServiceImpl.class);
    }

    @Provides
    @Named("cachedStreamTypeService")
    public StreamTypeService cachedStreamTypeService(final CachingEntityManager entityManager) {
        return new StreamTypeServiceImpl(entityManager);
    }
}