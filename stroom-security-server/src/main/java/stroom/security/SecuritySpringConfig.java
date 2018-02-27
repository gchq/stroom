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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import stroom.entity.StroomEntityManager;
import stroom.entity.event.EntityEventBus;
import stroom.explorer.ExplorerNodeService;
import stroom.explorer.ExplorerService;
import stroom.jobsystem.ClusterLockService;
import stroom.logging.AuthenticationEventLog;
import stroom.logging.AuthorisationEventLog;
import stroom.properties.StroomPropertyService;
import stroom.servlet.HttpServletRequestHolder;
import stroom.util.cache.CacheManager;
import stroom.util.spring.StroomBeanStore;
import stroom.util.spring.StroomScope;

import javax.inject.Provider;
import javax.validation.constraints.NotNull;

@Configuration
public class SecuritySpringConfig {
    @Bean
    public AuthenticationServiceClients authenticationServiceClients(
            @Value("#{propertyConfigurer.getProperty('stroom.security.apiToken')}") final String ourApiToken,
            @Value("#{propertyConfigurer.getProperty('stroom.auth.services.url')}") final String authServiceUrl,
            @Value("#{propertyConfigurer.getProperty('stroom.authentication.required')}") final String authRequired) {
        return new AuthenticationServiceClients(ourApiToken, authServiceUrl, authRequired);
    }

    @Bean
    public AuthenticationService authenticationService(
            final UserService userService,
            final UserAppPermissionService userAppPermissionService,
            final SecurityContext securityContext) {
        return new AuthenticationServiceImpl(userService, userAppPermissionService, securityContext);
    }

    @Bean
    public AuthorisationResource authorisationResource(final SecurityContext securityContext) {
        return new AuthorisationResource(securityContext);
    }

    @Bean
    public AuthorisationService authorisationService(final SecurityContext securityContext) {
        return new AuthorisationServiceImpl(securityContext);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public ChangeDocumentPermissionsHandler changeDocumentPermissionsHandler(final DocumentPermissionService documentPermissionService,
                                                                             final DocumentPermissionsCache documentPermissionsCache,
                                                                             final SecurityContext securityContext,
                                                                             final ExplorerNodeService explorerNodeService) {
        return new ChangeDocumentPermissionsHandler(documentPermissionService, documentPermissionsCache, securityContext, explorerNodeService);
    }

    @Bean
    @Scope(StroomScope.TASK)
    public ChangeUserHandler changeUserHandler(final UserService userService,
                                               final UserAppPermissionService userAppPermissionService,
                                               final AuthorisationEventLog authorisationEventLog,
                                               final UserGroupsCache userGroupsCache,
                                               final UserAppPermissionsCache userAppPermissionsCache) {
        return new ChangeUserHandler(userService, userAppPermissionService, authorisationEventLog, userGroupsCache, userAppPermissionsCache);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public CheckDocumentPermissionHandler checkDocumentPermissionHandler(final SecurityContext securityContext) {
        return new CheckDocumentPermissionHandler(securityContext);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public CreateUserHandler createUserHandler(final UserService userService) {
        return new CreateUserHandler(userService);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public DeleteUserHandler deleteUserHandler(final UserService userService) {
        return new DeleteUserHandler(userService);
    }

    @Bean
    public DocumentPermissionService documentPermissionService(final StroomEntityManager entityManager,
                                                               final DocumentTypePermissions documentTypePermissions) {
        return new DocumentPermissionServiceImpl(entityManager, documentTypePermissions);
    }

    @Bean
    public DocumentPermissionsCache documentPermissionsCache(final CacheManager cacheManager,
                                                             final DocumentPermissionService documentPermissionService,
                                                             final Provider<EntityEventBus> eventBusProvider) {
        return new DocumentPermissionsCache(cacheManager, documentPermissionService, eventBusProvider);
    }

    @Bean
    public DocumentTypePermissions documentTypePermissions(final ExplorerService explorerService) {
        return new DocumentTypePermissions(explorerService);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public FetchAllDocumentPermissionsHandler fetchAllDocumentPermissionsHandler(final DocumentPermissionsCache documentPermissionsCache,
                                                                                 final SecurityContext securityContext) {
        return new FetchAllDocumentPermissionsHandler(documentPermissionsCache, securityContext);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public FetchUserAndPermissionsHandler fetchUserAndPermissionsHandler(final SecurityContext securityContext,
                                                                         final UserAndPermissionsHelper userAndPermissionsHelper) {
        return new FetchUserAndPermissionsHandler(securityContext, userAndPermissionsHelper);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public FetchUserAppPermissionsHandler fetchUserAppPermissionsHandler(final UserAppPermissionsCache userAppPermissionsCache) {
        return new FetchUserAppPermissionsHandler(userAppPermissionsCache);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public FetchUserRefHandler fetchUserRefHandler(final UserService userService,
                                                   final SecurityContext securityContext) {
        return new FetchUserRefHandler(userService, securityContext);
    }

    @Bean
    public JWTService jWTService(@NotNull @Value("#{propertyConfigurer.getProperty('stroom.auth.services.url')}") final String authenticationServiceUrl,
                                 @NotNull @Value("#{propertyConfigurer.getProperty('stroom.auth.jwt.issuer')}") final String authJwtIssuer,
                                 @NotNull @Value("#{propertyConfigurer.getProperty('stroom.auth.jwt.enabletokenrevocationcheck')}") final boolean enableTokenRevocationCheck,
                                 final AuthenticationServiceClients authenticationServiceClients) {
        return new JWTService(authenticationServiceUrl, authJwtIssuer, enableTokenRevocationCheck, authenticationServiceClients);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public LogoutHandler logoutHandler(final HttpServletRequestHolder httpServletRequestHolder,
                                       final AuthenticationEventLog eventLog) {
        return new LogoutHandler(httpServletRequestHolder, eventLog);
    }

    @Bean
    public SessionResource sessionResource(final AuthenticationEventLog eventLog) {
        return new SessionResource(eventLog);
    }

    @Bean
    public UserAndPermissionsHelper userAndPermissionsHelper(final UserGroupsCache userGroupsCache,
                                                             final UserAppPermissionsCache userAppPermissionsCache) {
        return new UserAndPermissionsHelper(userGroupsCache, userAppPermissionsCache);
    }

    @Bean
    public UserAppPermissionService userAppPermissionService(final StroomEntityManager entityManager,
                                                             final ClusterLockService clusterLockService,
                                                             final StroomBeanStore beanStore) {
        return new UserAppPermissionServiceImpl(entityManager, clusterLockService, beanStore);
    }

    @Bean
    public UserAppPermissionsCache userAppPermissionsCache(final CacheManager cacheManager,
                                                           final UserAppPermissionService userAppPermissionService,
                                                           final Provider<EntityEventBus> eventBusProvider) {
        return new UserAppPermissionsCache(cacheManager, userAppPermissionService, eventBusProvider);
    }

    @Bean
    public UserCache userCache(final CacheManager cacheManager,
                               final UserService userService) {
        return new UserCache(cacheManager, userService);
    }

    @Bean
    public UserEventInfoProvider userEventInfoProvider() {
        return new UserEventInfoProvider();
    }

    @Bean
    public UserGroupsCache userGroupsCache(final CacheManager cacheManager,
                                           final UserService userService,
                                           final Provider<EntityEventBus> eventBusProvider) {
        return new UserGroupsCache(cacheManager, userService, eventBusProvider);
    }

    @Bean
    public UserSecurityMethodInterceptor userSecurityMethodInterceptor(final SecurityContext securityContext) {
        return new UserSecurityMethodInterceptor(securityContext);
    }

    @Bean("userService")
//    @Profile(StroomSpringProfiles.PROD)
    public UserService userService(final StroomEntityManager entityManager,
                                   final DocumentPermissionService documentPermissionService) {
        return new UserServiceImpl(entityManager, documentPermissionService);
    }

    @Bean(name = "securityConfig")
    public SecurityConfig securityConfig(final StroomPropertyService stroomPropertyService) {
        final SecurityConfig securityConfig = new SecurityConfig();
        securityConfig.setAuthenticationServiceUrl(stroomPropertyService.getProperty("stroom.auth.authentication.service.url"));
        securityConfig.setAdvertisedStroomUrl(stroomPropertyService.getProperty("stroom.advertisedUrl"));
        securityConfig.setAuthenticationRequired(stroomPropertyService.getBooleanProperty("stroom.authentication.required", true));
        return securityConfig;
    }

    @Bean(name = "securityFilter")
    public SecurityFilter securityFilter(
            SecurityConfig securityConfig,
            JWTService jwtService,
            AuthenticationServiceClients authenticationServiceClients,
            AuthenticationService authenticationService) {
        return new SecurityFilter(
                securityConfig,
                jwtService,
                authenticationServiceClients,
                authenticationService);
    }
}