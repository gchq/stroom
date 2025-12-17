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
import stroom.security.api.UserService;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.FindUserDependenciesCriteria;
import stroom.security.shared.User;
import stroom.security.shared.UserResource;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserDependency;
import stroom.util.shared.UserDesc;
import stroom.util.shared.UserRef;
import stroom.util.user.UserDescUtil;

import event.logging.CreateEventAction;
import event.logging.Outcome;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.Objects;

@AutoLogged
public class UserResourceImpl implements UserResource {

    private final Provider<UserService> userServiceProvider;
    private final Provider<AuthorisationEventLog> authorisationEventLogProvider;
    private final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider;

    @Inject
    public UserResourceImpl(final Provider<UserService> userServiceProvider,
                            final Provider<AuthorisationEventLog> authorisationEventLogProvider,
                            final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider) {
        this.userServiceProvider = userServiceProvider;
        this.authorisationEventLogProvider = authorisationEventLogProvider;
        this.stroomEventLoggingServiceProvider = stroomEventLoggingServiceProvider;
    }

    @Override
    public ResultPage<User> find(final FindUserCriteria criteria) {
        return userServiceProvider.get().find(criteria);
    }

    @Override
    public ResultPage<UserDependency> findDependencies(final FindUserDependenciesCriteria criteria) {

        Objects.requireNonNull(criteria.getUserRef());
        return userServiceProvider.get()
                .fetchUserDependencies(criteria);
    }

    @Override
    public User fetch(final String userUuid) {
        return userServiceProvider.get().loadByUuid(userUuid)
                .orElseThrow(() -> new NotFoundException("User " + userUuid + " does not exist"));
    }

    @Override
    public User fetchBySubjectId(final String subjectId) {
        Objects.requireNonNull(subjectId);
        return userServiceProvider.get()
                .getUserBySubjectId(subjectId)
                .orElseThrow(() -> new NotFoundException("User " + subjectId + " does not exist"));
    }

    @Override
    public boolean delete(final String userUuid) {
        return userServiceProvider.get()
                .delete(Objects.requireNonNull(userUuid));
    }

    @Override
    @AutoLogged(OperationType.MANUALLY_LOGGED)
    public User createUser(final UserDesc userDesc) {
        final CreateEventAction.Builder<Void> builder = CreateEventAction.builder();

        try {
            // Need to do the logging as a lambda so we only log if the creation actually happens
            return userServiceProvider.get()
                    .getOrCreateUser(userDesc, user -> {
                        builder.addUser(StroomEventLoggingUtil.createUser(user));

                        stroomEventLoggingServiceProvider.get().log(
                                "UserResourceImpl.createUser",
                                "Creating new Stroom user " + userDesc,
                                builder.build());
                    });
        } catch (final Exception ex) {
            builder.addUser(StroomEventLoggingUtil.createUser(userDesc))
                    .withOutcome(Outcome.builder()
                            .withSuccess(false)
                            .withDescription(ex.getMessage())
                            .build());

            stroomEventLoggingServiceProvider.get().log("UserResourceImpl.createUser",
                    "Creating new Stroom user " + userDesc, builder.build());

            throw ex;
        }
    }

    @Override
    @AutoLogged(OperationType.MANUALLY_LOGGED)
    public List<User> createUsersFromCsv(final String usersCsvData) {
        final List<UserDesc> externalUsers = UserDescUtil.parseUsersCsvData(usersCsvData);
        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "createUsers"))
                .withDescription(LogUtil.message("Creating batch of {} users", NullSafe.size(externalUsers)))
                .withDefaultEventAction(CreateEventAction.builder()
                        .withObjects(NullSafe.stream(externalUsers)
                                .map(StroomEventLoggingUtil::createUser)
                                .toList())
                        .build())
                .withSimpleLoggedResult(() -> {
                    final UserService userService = userServiceProvider.get();
                    return NullSafe.stream(externalUsers)
                            .map(userService::getOrCreateUser)
                            .toList();
                })
                .getResultAndLog();
    }

    @Override
    @AutoLogged(OperationType.MANUALLY_LOGGED)
    public User createGroup(final String name) {
        final CreateEventAction.Builder<Void> builder = CreateEventAction.builder();

        try {
            // Need to do the logging as a lambda so we only log if the creation actually happens
            return userServiceProvider.get()
                    .getOrCreateUserGroup(name, userGroup -> {
                        builder.withObjects(
                                event.logging.Group.builder()
                                        .withName(name)
                                        .withId(userGroup.getUuid())
                                        .build());
                        stroomEventLoggingServiceProvider.get().log("UserResourceImpl.createGroup",
                                "Creating new user group " + name, builder.build());
                    });
        } catch (final Exception ex) {
            builder.withObjects(
                    event.logging.Group.builder()
                            .withName(name)
                            .build());
            builder.withOutcome(Outcome.builder()
                    .withSuccess(false)
                    .withDescription(ex.getMessage())
                    .build());

            stroomEventLoggingServiceProvider.get().log("UserResourceImpl.createGroup",
                    "Creating new user group " + name, builder.build());

            throw ex;
        }
    }

    @Override
    public User update(final User user) {
        return userServiceProvider.get().update(user);
    }

    @Override
    public User copyGroupsAndPermissions(final String fromUserUuid, final String toUserUuid) {
        return userServiceProvider.get().copyGroupsAndPermissions(fromUserUuid, toUserUuid);
    }

    @Override
    @AutoLogged(OperationType.MANUALLY_LOGGED)
    public Boolean addUserToGroup(final String userUuid,
                                  final String groupUuid) {
        final User userIdForLogging = fetch(userUuid);
        final UserRef userOrGroupRef = userIdForLogging.asRef();
        final User groupIdForLogging = fetch(groupUuid);
        final UserRef groupRef = groupIdForLogging.asRef();

        try {
            final Boolean result = userServiceProvider.get().addUserToGroup(userOrGroupRef, groupRef);
            authorisationEventLogProvider.get().addPermission(
                    userOrGroupRef,
                    groupRef.toDisplayString(),
                    result,
                    null);
            return result;
        } catch (final Exception e) {
            authorisationEventLogProvider.get().addPermission(
                    userOrGroupRef,
                    groupRef.toDisplayString(),
                    false,
                    e.getMessage());
            throw e;
        }
    }

    @Override
    @AutoLogged(OperationType.MANUALLY_LOGGED)
    public Boolean removeUserFromGroup(final String userUuid,
                                       final String groupUuid) {
        final User userIdForLogging = fetch(userUuid);
        final UserRef userRef = userIdForLogging.asRef();
        final User groupIdForLogging = fetch(groupUuid);
        final UserRef groupRef = groupIdForLogging.asRef();

        try {
            final Boolean result = userServiceProvider.get().removeUserFromGroup(userRef, groupRef);
            authorisationEventLogProvider.get().removePermission(
                    userIdForLogging.asRef(),
                    groupIdForLogging.asRef().toDisplayString(),
                    result,
                    null);
            return result;
        } catch (final Exception e) {
            authorisationEventLogProvider.get().removePermission(
                    userIdForLogging.asRef(),
                    groupIdForLogging.asRef().toDisplayString(),
                    false,
                    e.getMessage());
            throw e;
        }
    }
}
