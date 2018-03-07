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

package stroom.security;

import com.google.inject.AbstractModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import stroom.entity.StroomEntityManager;
import stroom.entity.event.EntityEventBus;
import stroom.explorer.ExplorerService;
import stroom.util.cache.CacheManager;

import javax.inject.Provider;

public class MockSecurityModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(UserService.class).to(MockUserService.class);
    }

//    @Bean("userService")
////    @Profile(StroomSpringProfiles.TEST)
//    public UserService userService() {
//        return new MockUserService();
//    }
//
//    @Bean
//    public UserGroupsCache userGroupsCache(final CacheManager cacheManager,
//                                           final UserService userService,
//                                           final Provider<EntityEventBus> eventBusProvider) {
//        return new UserGroupsCache(cacheManager, userService, eventBusProvider);
//    }
//
//    @Bean
//    public DocumentPermissionsCache documentPermissionsCache(final CacheManager cacheManager,
//                                                             final DocumentPermissionService documentPermissionService,
//                                                             final Provider<EntityEventBus> eventBusProvider) {
//        return new DocumentPermissionsCache(cacheManager, documentPermissionService, eventBusProvider);
//    }
//
//    @Bean
//    public DocumentPermissionService documentPermissionService(final StroomEntityManager entityManager,
//                                                               final DocumentTypePermissions documentTypePermissions) {
//        return new DocumentPermissionServiceImpl(entityManager, documentTypePermissions);
//    }
//
//    @Bean
//    public DocumentTypePermissions documentTypePermissions(final ExplorerService explorerService) {
//        return new DocumentTypePermissions(explorerService);
//    }
}