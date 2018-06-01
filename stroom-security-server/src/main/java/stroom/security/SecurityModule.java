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
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;
import stroom.entity.event.EntityEvent;
import stroom.entity.shared.Clearable;
import stroom.logging.EventInfoProvider;
import stroom.properties.StroomPropertyService;
import stroom.security.SecurityConfig.JwtConfig;
import stroom.task.TaskHandler;

public class SecurityModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(DocumentPermissionService.class).to(DocumentPermissionServiceImpl.class);
        bind(AuthenticationService.class).to(AuthenticationServiceImpl.class);
        bind(AuthorisationService.class).to(AuthorisationServiceImpl.class);
        bind(UserAppPermissionService.class).to(UserAppPermissionServiceImpl.class);
        bind(UserService.class).to(UserServiceImpl.class);

        final Multibinder<EventInfoProvider> eventInfoProviderBinder = Multibinder.newSetBinder(binder(), EventInfoProvider.class);
        eventInfoProviderBinder.addBinding().to(UserEventInfoProvider.class);

        final Multibinder<Clearable> clearableBinder = Multibinder.newSetBinder(binder(), Clearable.class);
        clearableBinder.addBinding().to(DocumentPermissionsCache.class);
        clearableBinder.addBinding().to(UserAppPermissionsCache.class);
        clearableBinder.addBinding().to(UserGroupsCache.class);
        clearableBinder.addBinding().to(UserCache.class);

        final Multibinder<TaskHandler> taskHandlerBinder = Multibinder.newSetBinder(binder(), TaskHandler.class);
        taskHandlerBinder.addBinding().to(stroom.security.ChangeDocumentPermissionsHandler.class);
        taskHandlerBinder.addBinding().to(stroom.security.ChangeUserHandler.class);
        taskHandlerBinder.addBinding().to(stroom.security.CheckDocumentPermissionHandler.class);
        taskHandlerBinder.addBinding().to(stroom.security.CreateUserHandler.class);
        taskHandlerBinder.addBinding().to(stroom.security.DeleteUserHandler.class);
        taskHandlerBinder.addBinding().to(stroom.security.FetchAllDocumentPermissionsHandler.class);
        taskHandlerBinder.addBinding().to(stroom.security.FetchUserAndPermissionsHandler.class);
        taskHandlerBinder.addBinding().to(stroom.security.FetchUserAppPermissionsHandler.class);
        taskHandlerBinder.addBinding().to(stroom.security.FetchUserRefHandler.class);
        taskHandlerBinder.addBinding().to(stroom.security.LogoutHandler.class);

        final Multibinder<EntityEvent.Handler> entityEventHandlerBinder = Multibinder.newSetBinder(binder(), EntityEvent.Handler.class);
        entityEventHandlerBinder.addBinding().to(DocumentPermissionsCache.class);
        entityEventHandlerBinder.addBinding().to(UserAppPermissionsCache.class);
        entityEventHandlerBinder.addBinding().to(UserGroupsCache.class);
    }

    @Provides
    public JwtConfig jwtConfig(final StroomPropertyService stroomPropertyService) {
        final JwtConfig jwtConfig = new JwtConfig();
        jwtConfig.setJwtIssuer(stroomPropertyService.getProperty("stroom.auth.jwt.issuer"));
        jwtConfig.setEnableTokenRevocationCheck(stroomPropertyService.getBooleanProperty("stroom.auth.jwt.enabletokenrevocationcheck", false));
        return jwtConfig;
    }

    @Provides
    public SecurityConfig securityConfig(final StroomPropertyService stroomPropertyService, final JwtConfig jwtConfig) {
        final SecurityConfig securityConfig = new SecurityConfig();
        securityConfig.setAuthenticationServiceUrl(stroomPropertyService.getProperty("stroom.auth.authentication.service.url"));
        securityConfig.setAdvertisedStroomUrl(stroomPropertyService.getProperty("stroom.advertisedUrl"));
        securityConfig.setAuthenticationRequired(stroomPropertyService.getBooleanProperty("stroom.authentication.required", true));
        securityConfig.setApiToken(stroomPropertyService.getProperty("stroom.security.apiToken"));
        securityConfig.setAuthenticationServicesUrl(stroomPropertyService.getProperty("stroom.auth.services.url"));
        securityConfig.setJwtConfig(jwtConfig);
        return securityConfig;
    }

//
//    @Bean
//    public UserSecurityMethodInterceptor userSecurityMethodInterceptor(final SecurityContext securityContext) {
//        return new UserSecurityMethodInterceptor(securityContext);
//    }
//
//
//    @Bean(name = "securityFilter")
//    public SecurityFilter securityFilter(
//            SecurityConfig securityConfig,
//            JWTService jwtService,
//            AuthenticationServiceClients authenticationServiceClients,
//            AuthenticationService authenticationService) {
//        return new SecurityFilter(
//                securityConfig,
//                jwtService,
//                authenticationServiceClients,
//                authenticationService);
//    }
}