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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import stroom.entity.event.EntityEventBus;
import stroom.entity.event.EntityEventBusImpl;
import stroom.logging.DocumentEventLog;
import stroom.properties.StroomPropertyService;
import stroom.security.SecurityContext;
import stroom.task.TaskManager;
import stroom.util.cache.CacheManager;
import stroom.util.spring.StroomBeanStore;
import stroom.util.spring.StroomScope;

import javax.inject.Named;
import javax.inject.Provider;

@Configuration
public class EntitySpringConfig {
    @Bean
    public StroomDatabaseInfo stroomDatabaseInfo(final StroomPropertyService propertyService) {
        return new StroomDatabaseInfo(propertyService);
    }

//    @Bean("stroomEntityManager")
//    @Primary
//    public StroomEntityManager stroomEntityManager(final StroomBeanStore beanStore,
//                                                   final Provider<EntityEventBus> eventBusProvider,
//                                                   final Provider<StroomDatabaseInfo> stroomDatabaseInfoProvider) {
//        return new StroomEntityManagerImpl(beanStore, eventBusProvider, stroomDatabaseInfoProvider);
//    }

    @Bean("cachingEntityManager")
    public CachingEntityManager cachingEntityManager(@Named("stroomEntityManager") final StroomEntityManager stroomEntityManager, final CacheManager cacheManager) {
        return new CachingEntityManager(stroomEntityManager, cacheManager);
    }

    @Bean
    public DocumentPermissionCache documentPermissionCache(final CacheManager cacheManager,
                                                           final SecurityContext securityContext) {
        return new DocumentPermissionCacheImpl(cacheManager, securityContext);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public EntityReferenceFindHandler entityReferenceFindHandler(final EntityServiceBeanRegistry beanRegistry,
                                                                 final DocumentEventLog documentEventLog) {
        return new EntityReferenceFindHandler(beanRegistry, documentEventLog);
    }

//    @Bean
//    public EntityServiceBeanRegistry entityServiceBeanRegistry(final StroomBeanStore beanStore) {
//        return new EntityServiceBeanRegistry(beanStore);
//    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public EntityServiceDeleteHandler entityServiceDeleteHandler(final GenericEntityService entityService) {
        return new EntityServiceDeleteHandler(entityService);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public EntityServiceFindDeleteHandler entityServiceFindDeleteHandler(final EntityServiceBeanRegistry beanRegistry,
                                                                         final DocumentEventLog documentEventLog) {
        return new EntityServiceFindDeleteHandler(beanRegistry, documentEventLog);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public EntityServiceFindHandler entityServiceFindHandler(final EntityServiceBeanRegistry beanRegistry,
                                                             final DocumentEventLog documentEventLog) {
        return new EntityServiceFindHandler(beanRegistry, documentEventLog);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public EntityServiceFindReferenceHandler entityServiceFindReferenceHandler(final EntityServiceBeanRegistry beanRegistry) {
        return new EntityServiceFindReferenceHandler(beanRegistry);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public EntityServiceFindSummaryHandler entityServiceFindSummaryHandler(final EntityServiceBeanRegistry beanRegistry,
                                                                           final DocumentEventLog documentEventLog) {
        return new EntityServiceFindSummaryHandler(beanRegistry, documentEventLog);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public EntityServiceSaveHandler entityServiceSaveHandler(final EntityServiceBeanRegistry beanRegistry,
                                                             final DocumentEventLog entityEventLog) {
        return new EntityServiceSaveHandler(beanRegistry, entityEventLog);
    }

    @Bean
    public GenericEntityMarshaller genericEntityMarshaller() {
        return new GenericEntityMarshallerImpl();
    }

    @Bean
    public GenericEntityService genericEntityService(final EntityServiceBeanRegistry entityServiceBeanRegistry) {
        return new GenericEntityServiceImpl(entityServiceBeanRegistry);
    }

    @Bean
    public EntityEventBus entityEventBus(final StroomBeanStore stroomBeanStore, final TaskManager taskManager) {
        return new EntityEventBusImpl(stroomBeanStore, taskManager);
    }
}