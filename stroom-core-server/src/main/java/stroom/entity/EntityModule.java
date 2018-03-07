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

package stroom.entity;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import stroom.entity.event.EntityEventBus;
import stroom.entity.event.EntityEventBusImpl;
import stroom.logging.DocumentEventLog;
import stroom.security.SecurityContext;
import stroom.task.TaskHandler;
import stroom.task.TaskManager;
import stroom.util.cache.CacheManager;
import stroom.util.spring.StroomBeanStore;
import stroom.util.spring.StroomScope;

import javax.inject.Named;
import javax.inject.Provider;

public class EntityModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(GenericEntityService.class).to(GenericEntityServiceImpl.class);
        bind(StroomEntityManager.class).to(StroomEntityManagerImpl.class);
        bind(EntityEventBus.class).to(EntityEventBusImpl.class);
        bind(DocumentPermissionCache.class).to(DocumentPermissionCacheImpl.class);

        final Multibinder<TaskHandler> taskHandlerBinder = Multibinder.newSetBinder(binder(), TaskHandler.class);
        taskHandlerBinder.addBinding().to(stroom.entity.EntityReferenceFindHandler.class);
        taskHandlerBinder.addBinding().to(stroom.entity.EntityServiceDeleteHandler.class);
        taskHandlerBinder.addBinding().to(stroom.entity.EntityServiceFindDeleteHandler.class);
        taskHandlerBinder.addBinding().to(stroom.entity.EntityServiceFindHandler.class);
        taskHandlerBinder.addBinding().to(stroom.entity.EntityServiceFindReferenceHandler.class);
        taskHandlerBinder.addBinding().to(stroom.entity.EntityServiceFindSummaryHandler.class);
        taskHandlerBinder.addBinding().to(stroom.entity.EntityServiceSaveHandler.class);
    }

//
// TODO: @66 DON'T THINK THIS IS NEEDED ANYMORE SO DELETE IT
//    @Bean
//    public GenericEntityMarshaller genericEntityMarshaller() {
//        return new GenericEntityMarshallerImpl();
//    }
//
}