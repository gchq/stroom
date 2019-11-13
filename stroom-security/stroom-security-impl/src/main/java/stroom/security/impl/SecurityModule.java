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

package stroom.security.impl;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import stroom.entity.shared.EntityEvent;
import stroom.security.api.AuthenticationService;
import stroom.security.api.DocumentPermissionService;
import stroom.security.impl.event.PermissionChangeEvent;
import stroom.security.impl.event.PermissionChangeEventLifecycleModule;
import stroom.security.impl.event.PermissionChangeEventModule;
import stroom.security.shared.ChangeDocumentPermissionsAction;
import stroom.security.shared.ChangeUserAction;
import stroom.security.shared.CheckDocumentPermissionAction;
import stroom.security.shared.CopyPermissionsFromParentAction;
import stroom.security.shared.CreateUserAction;
import stroom.security.shared.DeleteUserAction;
import stroom.security.shared.FetchAllDocumentPermissionsAction;
import stroom.security.shared.FetchUserAction;
import stroom.security.shared.FetchUserAndPermissionsAction;
import stroom.security.shared.LogoutAction;
import stroom.task.api.TaskHandlerBinder;
import stroom.util.shared.RestResource;
import stroom.util.guice.FilterBinder;
import stroom.util.guice.FilterInfo;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.HealthCheckBinder;
import stroom.util.shared.Clearable;

import javax.servlet.http.HttpSessionListener;

public class SecurityModule extends AbstractModule {
    private static final String MATCH_ALL_PATHS = "/*";

    @Override
    protected void configure() {
        install(new PermissionChangeEventModule());
        install(new PermissionChangeEventLifecycleModule());

        bind(DocumentPermissionService.class).to(DocumentPermissionServiceImpl.class);
        bind(AuthenticationService.class).to(AuthenticationServiceImpl.class);
        bind(AuthorisationService.class).to(AuthorisationServiceImpl.class);
        bind(UserAppPermissionService.class).to(UserAppPermissionServiceImpl.class);
        bind(UserService.class).to(UserServiceImpl.class);

        FilterBinder.create(binder())
                .bind(new FilterInfo(SecurityFilter.class.getSimpleName(), MATCH_ALL_PATHS),
                        SecurityFilter.class);

        // Provide object info to the logging service.
        GuiceUtil.buildMultiBinder(binder(), Clearable.class)
                .addBinding(UserDocumentPermissionsCache.class)
                .addBinding(UserAppPermissionsCache.class)
                .addBinding(UserGroupsCache.class)
                .addBinding(UserCache.class);

        GuiceUtil.buildMultiBinder(binder(), HttpSessionListener.class)
                .addBinding(SessionMap.class);

        TaskHandlerBinder.create(binder())
                .bind(ChangeDocumentPermissionsAction.class, ChangeDocumentPermissionsHandler.class)
                .bind(ChangeUserAction.class, ChangeUserHandler.class)
                .bind(CheckDocumentPermissionAction.class, CheckDocumentPermissionHandler.class)
                .bind(CreateUserAction.class, CreateUserHandler.class)
                .bind(DeleteUserAction.class, DeleteUserHandler.class)
                .bind(FetchAllDocumentPermissionsAction.class, FetchAllDocumentPermissionsHandler.class)
                .bind(FetchUserAndPermissionsAction.class, FetchUserAndPermissionsHandler.class)
                .bind(CopyPermissionsFromParentAction.class, CopyPermissionsFromParentHandler.class)
                .bind(FetchUserAction.class, FetchUserHandler.class)
                .bind(LogoutAction.class, LogoutHandler.class);

        final Multibinder<EntityEvent.Handler> entityEventHandlerBinder = Multibinder.newSetBinder(binder(), EntityEvent.Handler.class);
        entityEventHandlerBinder.addBinding().to(UserGroupsCache.class);

        final Multibinder<PermissionChangeEvent.Handler> permissionChangeEventHandlerBinder = Multibinder.newSetBinder(binder(), PermissionChangeEvent.Handler.class);
        permissionChangeEventHandlerBinder.addBinding().to(UserDocumentPermissionsCache.class);

        HealthCheckBinder.create(binder())
                .bind(JWTService.class);

        GuiceUtil.buildMultiBinder(binder(), RestResource.class)
                .addBinding(UserResourceImpl.class)
                .addBinding(UserAppPermissionResourceImpl.class)
                .addBinding(DocumentPermissionResourceImpl.class);
    }
}