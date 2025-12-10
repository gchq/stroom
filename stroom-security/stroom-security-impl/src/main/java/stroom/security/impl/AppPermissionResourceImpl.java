/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.api.StroomEventLoggingUtil;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.security.api.AppPermissionService;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.security.api.exception.AuthenticationException;
import stroom.security.shared.AbstractAppPermissionChange;
import stroom.security.shared.AbstractAppPermissionChange.AddAppPermission;
import stroom.security.shared.AbstractAppPermissionChange.RemoveAppPermission;
import stroom.security.shared.AppPermission;
import stroom.security.shared.AppPermissionResource;
import stroom.security.shared.AppUserPermissions;
import stroom.security.shared.AppUserPermissionsReport;
import stroom.security.shared.FetchAppUserPermissionsRequest;
import stroom.security.shared.HasUserRef;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;

import event.logging.AddGroups;
import event.logging.AuthorisationActionType;
import event.logging.AuthoriseEventAction;
import event.logging.ComplexLoggedOutcome;
import event.logging.Group;
import event.logging.Permission;
import event.logging.PermissionAttribute;
import event.logging.Permissions;
import event.logging.RemoveGroups;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

@AutoLogged(OperationType.MANUALLY_LOGGED)
class AppPermissionResourceImpl implements AppPermissionResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AppPermissionResourceImpl.class);

    private final Provider<SecurityContext> securityContextProvider;
    private final Provider<AppPermissionService> appPermissionServiceProvider;
    private final Provider<UserAndPermissionsHelper> userAndPermissionsHelperProvider;
    private final Provider<AuthenticationConfig> authenticationConfigProvider;
    private final Provider<AuthorisationEventLog> authorisationEventLogProvider;
    private final Provider<UserAppPermissionsCache> userAppPermissionsCacheProvider;
    private final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider;

    @Inject
    AppPermissionResourceImpl(final Provider<SecurityContext> securityContextProvider,
                              final Provider<AppPermissionService> appPermissionServiceProvider,
                              final Provider<UserAndPermissionsHelper> userAndPermissionsHelperProvider,
                              final Provider<AuthenticationConfig> authenticationConfigProvider,
                              final Provider<AuthorisationEventLog> authorisationEventLogProvider,
                              final Provider<UserAppPermissionsCache> userAppPermissionsCacheProvider,
                              final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider) {
        this.securityContextProvider = securityContextProvider;
        this.appPermissionServiceProvider = appPermissionServiceProvider;
        this.userAndPermissionsHelperProvider = userAndPermissionsHelperProvider;
        this.authenticationConfigProvider = authenticationConfigProvider;
        this.authorisationEventLogProvider = authorisationEventLogProvider;
        this.userAppPermissionsCacheProvider = userAppPermissionsCacheProvider;
        this.stroomEventLoggingServiceProvider = stroomEventLoggingServiceProvider;
    }

    @AutoLogged(OperationType.VIEW)
    @Override
    public ResultPage<AppUserPermissions> fetchAppUserPermissions(final FetchAppUserPermissionsRequest request) {
        return appPermissionServiceProvider.get().fetchAppUserPermissions(request);
    }

    @Override
    @AutoLogged(OperationType.VIEW)
    public AppUserPermissions getEffectiveAppPermissions() {
        final SecurityContext securityContext = securityContextProvider.get();
        final UserIdentity userIdentity = securityContext.getUserIdentity();
        if (userIdentity == null) {
            LOGGER.debug("Null userIdentity");
            return null;
        } else {
            if (userIdentity instanceof HasUserRef) {
                final boolean preventLogin = authenticationConfigProvider.get().isPreventLogin();
                if (preventLogin) {
                    final boolean isAdmin = securityContext.isAdmin();
                    LOGGER.debug("Preventing login for all but admin, isAdmin: {}", isAdmin);
                    if (!securityContext.isAdmin()) {
                        throw new AuthenticationException("Stroom is down for maintenance. Please try again later.");
                    }
                }
                final AppUserPermissions userAndPermissions = new AppUserPermissions(
                        securityContext.getUserRef(),
                        userAndPermissionsHelperProvider.get().getCurrentAppPermissions());

                LOGGER.debug("Returning {}", userAndPermissions);
                return userAndPermissions;
            } else {
                LOGGER.debug(LogUtil.message("Wrong type of user, expecting: {}, got: {}",
                        HasUserRef.class.getSimpleName(),
                        userIdentity.getClass().getSimpleName()));
                return null;
            }
        }
    }

    @Override
    @AutoLogged(OperationType.VIEW)
    public AppUserPermissionsReport getAppUserPermissionsReport(final UserRef user) {
        return appPermissionServiceProvider.get().getAppUserPermissionsReport(user);
    }

    @Override
    @AutoLogged(OperationType.MANUALLY_LOGGED)
    public Boolean changeAppPermission(final AbstractAppPermissionChange request) {
        final UserRef userRef = request.getUserRef();
        if (userRef != null) {
            final Permission permission = Permission
                    .builder()
                    .withAllowAttributes(PermissionAttribute.AUTHOR)
                    .withUser(StroomEventLoggingUtil.createUser(userRef))
                    .withGroup(Group
                            .builder()
                            .withType(request.getPermission().getDisplayValue())
                            .build())
                    .build();
            final Group group = Group
                    .builder()
                    .withPermissions(Permissions.builder().addPermissions(permission).build())
                    .build();

            // Modify user/user group feature permissions.
            if (request instanceof final AddAppPermission addAppPermission) {
                final AuthoriseEventAction action = AuthoriseEventAction
                        .builder()
                        .withAction(AuthorisationActionType.MODIFY)
                        .addUser(StroomEventLoggingUtil.createUser(userRef))
                        .withAddGroups(AddGroups.builder().addGroups(group).build())
                        .build();
                stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                        .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "changeAppPermission"))
                        .withDescription("Add application permission for user")
                        .withDefaultEventAction(action)
                        .withComplexLoggedResult(searchEventAction -> {
                            addPermission(userRef, addAppPermission.getPermission());
                            return ComplexLoggedOutcome.success(true, action);
                        })
                        .getResultAndLog();

            } else if (request instanceof final RemoveAppPermission removeAppPermission) {
                final AuthoriseEventAction action = AuthoriseEventAction
                        .builder()
                        .withAction(AuthorisationActionType.MODIFY)
                        .addUser(StroomEventLoggingUtil.createUser(userRef))
                        .withRemoveGroups(RemoveGroups.builder().addGroups(group).build())
                        .build();
                stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                        .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "changeAppPermission"))
                        .withDescription("Remove application permission for user")
                        .withDefaultEventAction(action)
                        .withComplexLoggedResult(searchEventAction -> {
                            removePermission(userRef, removeAppPermission.getPermission());
                            return ComplexLoggedOutcome.success(true, action);
                        })
                        .getResultAndLog();
            }

            // Clear cached application permissions for this user.
            userAppPermissionsCacheProvider.get().remove(userRef);
        }

        return true;
    }

    private void addPermission(final UserRef user, final AppPermission permission) {
        try {
            appPermissionServiceProvider.get().addPermission(user, permission);
            authorisationEventLogProvider.get()
                    .addPermission(user, permission.getDisplayValue(), true, null);
        } catch (final RuntimeException e) {
            authorisationEventLogProvider.get()
                    .addPermission(user, permission.getDisplayValue(), false, e.getMessage());
        }
    }

    private void removePermission(final UserRef user, final AppPermission permission) {
        try {
            appPermissionServiceProvider.get().removePermission(user, permission);
            authorisationEventLogProvider.get()
                    .removePermission(user, permission.getDisplayValue(), true, null);
        } catch (final RuntimeException e) {
            authorisationEventLogProvider.get()
                    .removePermission(user, permission.getDisplayValue(), false, e.getMessage());
        }
    }
}
