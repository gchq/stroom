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
import com.google.inject.multibindings.Multibinder;
import stroom.entity.event.EntityEvent;
import stroom.entity.shared.Clearable;
import stroom.logging.EventInforProviderBinder;
import stroom.security.shared.ChangeDocumentPermissionsAction;
import stroom.security.shared.ChangeUserAction;
import stroom.security.shared.CheckDocumentPermissionAction;
import stroom.security.shared.CopyPermissionsFromParentAction;
import stroom.security.shared.CreateUserAction;
import stroom.security.shared.DeleteUserAction;
import stroom.security.shared.FetchAllDocumentPermissionsAction;
import stroom.security.shared.FetchUserAndPermissionsAction;
import stroom.security.shared.FetchUserRefAction;
import stroom.security.shared.LogoutAction;
import stroom.task.api.TaskHandlerBinder;
import stroom.util.HasHealthCheck;

public class SecurityModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(DocumentPermissionService.class).to(DocumentPermissionServiceImpl.class);
        bind(AuthenticationService.class).to(AuthenticationServiceImpl.class);
        bind(AuthorisationService.class).to(AuthorisationServiceImpl.class);
        bind(UserAppPermissionService.class).to(UserAppPermissionServiceImpl.class);
        bind(UserService.class).to(UserServiceImpl.class);

        EventInforProviderBinder.create(binder())
                .bind(UserEventInfoProvider.class);

        final Multibinder<Clearable> clearableBinder = Multibinder.newSetBinder(binder(), Clearable.class);
        clearableBinder.addBinding().to(DocumentPermissionsCache.class);
        clearableBinder.addBinding().to(UserAppPermissionsCache.class);
        clearableBinder.addBinding().to(UserGroupsCache.class);
        clearableBinder.addBinding().to(UserCache.class);

        TaskHandlerBinder.create(binder())
                .bind(ChangeDocumentPermissionsAction.class, ChangeDocumentPermissionsHandler.class)
                .bind(ChangeUserAction.class, ChangeUserHandler.class)
                .bind(CheckDocumentPermissionAction.class, CheckDocumentPermissionHandler.class)
                .bind(CreateUserAction.class, CreateUserHandler.class)
                .bind(DeleteUserAction.class, DeleteUserHandler.class)
                .bind(FetchAllDocumentPermissionsAction.class, FetchAllDocumentPermissionsHandler.class)
                .bind(FetchUserAndPermissionsAction.class, FetchUserAndPermissionsHandler.class)
                .bind(CopyPermissionsFromParentAction.class, CopyPermissionsFromParentHandler.class)
                .bind(FetchUserRefAction.class, FetchUserRefHandler.class)
                .bind(LogoutAction.class, LogoutHandler.class);

        final Multibinder<EntityEvent.Handler> entityEventHandlerBinder = Multibinder.newSetBinder(binder(), EntityEvent.Handler.class);
        entityEventHandlerBinder.addBinding().to(DocumentPermissionsCache.class);
        entityEventHandlerBinder.addBinding().to(UserAppPermissionsCache.class);
        entityEventHandlerBinder.addBinding().to(UserGroupsCache.class);

        final Multibinder<HasHealthCheck> hasHealthCheckBinder = Multibinder.newSetBinder(binder(), HasHealthCheck.class);
        hasHealthCheckBinder.addBinding().to(JWTService.class);
    }

//    @Provides
//    public JwtConfig jwtConfig(final PropertyService propertyService) {
//        final JwtConfig jwtConfig = new JwtConfig();
//        jwtConfig.setJwtIssuer(propertyService.getProperty("stroom.auth.jwt.issuer"));
//        jwtConfig.setEnableTokenRevocationCheck(propertyService.getBooleanProperty("stroom.auth.jwt.enabletokenrevocationcheck", false));
//        return jwtConfig;
//    }
//
//    @Provides
//    public SecurityConfig securityConfig(final PropertyService propertyService, final JwtConfig jwtConfig) {
//        final SecurityConfig securityConfig = new SecurityConfig();
//        securityConfig.setAuthenticationServiceUrl(propertyService.getProperty("stroom.auth.authentication.service.url"));
//        securityConfig.setAdvertisedStroomUrl(propertyService.getProperty("stroom.advertisedUrl"));
//        securityConfig.setAuthenticationRequired(propertyService.getBooleanProperty("stroom.authentication.required", true));
//        securityConfig.setApiToken(propertyService.getProperty("stroom.security.apiToken"));
//        securityConfig.setAuthServicesBaseUrl(propertyService.getProperty("stroom.auth.services.url"));
//        securityConfig.setJwtConfig(jwtConfig);
//        return securityConfig;
//    }
//
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