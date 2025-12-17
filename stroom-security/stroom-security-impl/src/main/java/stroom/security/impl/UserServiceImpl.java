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

import stroom.activity.api.ActivityService;
import stroom.docref.DocRef;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.security.api.ContentPackUserService;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserService;
import stroom.security.impl.event.PermissionChangeEvent;
import stroom.security.impl.event.PermissionChangeEventBus;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.FindUserContext;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.FindUserDependenciesCriteria;
import stroom.security.shared.User;
import stroom.storedquery.api.StoredQueryService;
import stroom.ui.config.shared.UserPreferencesService;
import stroom.util.AuditUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.CompareUtil;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.HasUserDependencies;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResultPage;
import stroom.util.shared.StringUtil;
import stroom.util.shared.UserDependency;
import stroom.util.shared.UserDesc;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

class UserServiceImpl implements UserService, ContentPackUserService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(UserServiceImpl.class);

    private final SecurityContext securityContext;
    private final UserDao userDao;
    private final PermissionChangeEventBus permissionChangeEventBus;
    private final Map<String, Provider<HasUserDependencies>> hasUserDependenciesProviderMap;
    private final UserCache userCache;
    private final DocRefInfoService docRefInfoService;
    private final StoredQueryService storedQueryService;
    private final UserPreferencesService userPreferencesService;
    private final ActivityService activityService;

    @Inject
    UserServiceImpl(final SecurityContext securityContext,
                    final UserDao userDao,
                    final PermissionChangeEventBus permissionChangeEventBus,
                    final Map<String, Provider<HasUserDependencies>> hasDependenciesSet,
                    final UserCache userCache,
                    final DocRefInfoService docRefInfoService,
                    final StoredQueryService storedQueryService,
                    final UserPreferencesService userPreferencesService,
                    final ActivityService activityService) {
        this.securityContext = securityContext;
        this.userDao = userDao;
        this.permissionChangeEventBus = permissionChangeEventBus;
        this.hasUserDependenciesProviderMap = hasDependenciesSet;
        this.userCache = userCache;
        this.docRefInfoService = docRefInfoService;
        this.storedQueryService = storedQueryService;
        this.userPreferencesService = userPreferencesService;
        this.activityService = activityService;
    }

    @Override
    public User getOrCreateUser(final UserDesc userDesc, final Consumer<User> onCreateAction) {
        final Optional<User> optional = userDao.getUserBySubjectId(userDesc.getSubjectId());
        return optional.orElseGet(() -> {
            final User user = new User();
            AuditUtil.stamp(securityContext, user);
            final String subjectId = userDesc.getSubjectId();
            user.setSubjectId(subjectId);
            // Make sure we set a display name even if it is the same as the subject id.
            user.setDisplayName(NullSafe.nonBlankStringElse(userDesc.getDisplayName(), subjectId));
            user.setFullName(userDesc.getFullName());
            user.setGroup(false);
            user.setEnabled(true);

            return securityContext.secureResult(AppPermission.MANAGE_USERS_PERMISSION, () ->
                    userDao.tryCreate(user, persistedUser -> {
                        fireUserChangeEvent(persistedUser.asRef());
                        if (onCreateAction != null) {
                            onCreateAction.accept(persistedUser);
                        }
                    }));
        });
    }

    @Override
    public User getOrCreateUserGroup(final String name, final Consumer<User> onCreateAction) {
        final Optional<User> optional = userDao.getGroupByName(name);
        return optional.orElseGet(() -> {
            final User user = new User();
            AuditUtil.stamp(securityContext, user);
            user.setSubjectId(name);
            user.setDisplayName(name);
            user.setGroup(true);
            user.setEnabled(true);

            return securityContext.secureResult(AppPermission.MANAGE_USERS_PERMISSION, () ->
                    userDao.tryCreate(user, persistedUser -> {
                        fireUserChangeEvent(persistedUser.asRef());
                        if (onCreateAction != null) {
                            onCreateAction.accept(persistedUser);
                        }
                    }));
        });
    }

    @Override
    public Optional<User> getUserBySubjectId(final String subjectId) {
        if (!NullSafe.isBlankString(subjectId)) {
            return userDao.getUserBySubjectId(subjectId);
        } else {
            return Optional.empty();
        }
    }

    @Override
    public Optional<User> getGroupByName(final String groupName) {
        if (!NullSafe.isBlankString(groupName)) {
            return userDao.getGroupByName(groupName);
        } else {
            return Optional.empty();
        }
    }

    @Override
    public Optional<User> loadByUuid(final String uuid) {
        return userDao.getByUuid(uuid);
    }

    @Override
    public User update(final User user) {
        AuditUtil.stamp(securityContext, user);
        return securityContext.secureResult(AppPermission.MANAGE_USERS_PERMISSION, () -> {
            final User updatedUser = userDao.update(user);

            // If the updated user is a group then we need to let all children know there has been a change as we cache
            // parent groups for children.
            if (updatedUser.isGroup()) {
                final ResultPage<User> resultPage = findUsersInGroup(updatedUser.getUuid(), new FindUserCriteria());
                for (final User child : resultPage.getValues()) {
                    fireUserChangeEvent(child.asRef());
                }
            }

            fireUserChangeEvent(updatedUser.asRef());
            return updatedUser;
        });
    }

    @Override
    public User copyGroupsAndPermissions(final String fromUserUuid, final String toUserUuid) {
        final User toUser = userDao.getByUuid(toUserUuid).orElseThrow();
        AuditUtil.stamp(securityContext, toUser);

        return securityContext.secureResult(AppPermission.MANAGE_USERS_PERMISSION, () -> {
            userDao.copyGroupsAndPermissions(fromUserUuid, toUserUuid);

            fireUserChangeEvent(toUser.asRef());

            return toUser;
        });
    }

    @Override
    public ResultPage<User> find(final FindUserCriteria criteria) {
        return securityContext.secureResult(() -> {
            if (securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION)) {
                return userDao.find(criteria);
            } else {
                final String currentUserUuid = NullSafe
                        .get(securityContext, SecurityContext::getUserRef, UserRef::getUuid);
                return userDao.findRelatedUsers(currentUserUuid, criteria);
            }
        });
    }

    @Override
    public UserRef getUserByUuid(final String uuid, final FindUserContext context) {
        if (uuid == null) {
            return null;
        }

        final String currentUserUuid = NullSafe.get(securityContext, SecurityContext::getUserRef, UserRef::getUuid);
        final Optional<User> optional = securityContext.secureResult(() -> {
            if (securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION) ||
                uuid.equals(currentUserUuid)) {
                return userCache.getByUuid(uuid);
            } else {
                return userDao.getByUuid(uuid, currentUserUuid, context);
            }
        });
        return optional.map(User::asRef).orElse(null);
    }

    @Override
    public ResultPage<User> findUsersInGroup(final String groupUuid, final FindUserCriteria criteria) {
        // See if the user is allowed to see the requested group.
        if (!securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION)) {
            throw new PermissionException(
                    securityContext.getUserRef(),
                    "You do not have permission to manage users.");
        }
        return userDao.findUsersInGroup(groupUuid, criteria);
    }

    @Override
    public ResultPage<User> findGroupsForUser(final String userUuid, final FindUserCriteria criteria) {
        // See if the user is allowed to see for the requested user.
        if (!securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION)) {
            if (!securityContext.getUserRef().getUuid().equals(userUuid)) {
                throw new PermissionException(
                        securityContext.getUserRef(),
                        "You are only allowed to see your own groups.");
            }
        }
        return userDao.findGroupsForUser(userUuid, criteria);
    }

    @Override
    public Boolean addUserToGroup(final UserRef userOrGroupRef, final UserRef groupRef) {
        Objects.requireNonNull(userOrGroupRef);
        Objects.requireNonNull(groupRef);
        securityContext.secure(AppPermission.MANAGE_USERS_PERMISSION, () -> {
            final String userUuid = userOrGroupRef.getUuid();
            final String groupUuid = groupRef.getUuid();

            // Make sure we aren't creating a cyclic dependency.
            checkCyclicDependency(userUuid, groupUuid, new HashSet<>());

            userDao.addUserToGroup(userUuid, groupUuid);
            fireUserChangeEvent(userOrGroupRef);
            fireUserChangeEvent(groupRef);
        });
        return true;
    }

    private void checkCyclicDependency(final String userUuid,
                                       final String groupUuid,
                                       final Set<String> examined) {
        if (userUuid.equals(groupUuid)) {
            throw new RuntimeException("Attempt to add a user/group to a group that would create a cyclic dependency");
        }
        if (!examined.contains(groupUuid)) {
            examined.add(groupUuid);

            final ResultPage<User> groups = userDao.findGroupsForUser(
                    groupUuid,
                    new FindUserCriteria.Builder().pageRequest(PageRequest.unlimited()).build());
            for (final User group : groups.getValues()) {
                checkCyclicDependency(userUuid, group.getUuid(), examined);
            }
        }
    }

    @Override
    public Boolean removeUserFromGroup(final UserRef userOrGroupRef, final UserRef groupRef) {
        Objects.requireNonNull(userOrGroupRef);
        Objects.requireNonNull(groupRef);
        securityContext.secure(AppPermission.MANAGE_USERS_PERMISSION, () -> {
            userDao.removeUserFromGroup(userOrGroupRef.getUuid(), groupRef.getUuid());
            fireUserChangeEvent(userOrGroupRef);
            fireUserChangeEvent(groupRef);
        });
        return true;
    }

    @Override
    public boolean delete(final String userUuid) {
        Objects.requireNonNull(userUuid);
        securityContext.secure(AppPermission.MANAGE_USERS_PERMISSION, () -> {

            final UserRef userRef = userDao.getByUuid(userUuid)
                    .map(User::asRef)
                    .orElseThrow(() ->
                            new RuntimeException(LogUtil.message("User not found with userUuid {}", userUuid)));

            final List<UserDependency> allUserDependencies = NullSafe.valuesOf(hasUserDependenciesProviderMap)
                    .stream()
                    .map(Provider::get)
                    .flatMap(hasUserDependencies ->
                            hasUserDependencies.getUserDependencies(userRef).stream())
                    .toList();

            if (NullSafe.isEmptyCollection(allUserDependencies)) {
                doDelete(userRef);
            } else {
                final List<String> detailLines = allUserDependencies.stream()
                        .filter(userDependency ->
                                NullSafe.getOrElse(
                                        userDependency.getDocRef(),
                                        docRef -> securityContext.hasDocumentPermission(
                                                docRef, DocumentPermission.VIEW),
                                        true))
                        .map(UserDependency::getDetails)
                        .toList();

                if (detailLines.isEmpty()) {
                    // Deps exist, but we don't have perms to see what they are
                    throw new RuntimeException(
                            LogUtil.message(
                                    "Unable to delete user '{}' as {} {} have dependencies on the user. You do not " +
                                    "have permission to view these items.",
                                    userRef.toDisplayString(),
                                    allUserDependencies.size(),
                                    StringUtil.plural("item has", "items have", allUserDependencies)));
                } else {
                    final String detail = String.join("\n", detailLines);
                    throw new RuntimeException(
                            LogUtil.message(
                                    "Unable to delete user '{}' as the following {} have dependencies on " +
                                    "the user.\n{}",
                                    userRef.toDisplayString(),
                                    StringUtil.plural("item has", "items have", allUserDependencies),
                                    detail));
                }
            }
        });
        return true;
    }

    private boolean doDelete(final UserRef userRef) {
        Objects.requireNonNull(userRef);
        final boolean didDelete = userDao.deleteUser(userRef);
        if (didDelete) {
            // We can't delete these things inside the user deletion txn as they are in different
            // db modules. Best we can do is log any failures and delete as much as we can. Not the
            // end of the world if there are orphaned records hanging around.
            int storedQueryCount = 0;
            try {
                storedQueryCount = storedQueryService.deleteByOwner(userRef);
            } catch (final Exception e) {
                LOGGER.error("Error deleting stored queries for user {}", userRef.toInfoString(), e);
                // Swallow and carry on
            }
            int userPrefCount = 0;
            try {
                userPrefCount = userPreferencesService.delete(userRef)
                        ? 1
                        : 0;
            } catch (final Exception e) {
                LOGGER.error("Error deleting user preferences for user {}", userRef.toInfoString(), e);
                // Swallow and carry on
            }
            int activityCount = 0;
            try {
                activityCount = activityService.deleteAllByOwner(userRef);
            } catch (final Exception e) {
                LOGGER.error("Error deleting activities for user {}", userRef.toInfoString(), e);
                // Swallow and carry on
            }

            LOGGER.info("Deleted the following associated records for deleted user {}, stored queries: {}, " +
                        "user preferences: {}, activities: {}",
                    userRef.toInfoString(),
                    storedQueryCount,
                    userPrefCount,
                    activityCount);

            // Search result stores will get cleaned up by
            // stroom.query.common.v2.ResultStoreManager.evictExpiredElements

            fireUserChangeEvent(userRef);
        }
        return didDelete;
    }

    @Override
    public ResultPage<UserDependency> fetchUserDependencies(final FindUserDependenciesCriteria criteria) {
        Objects.requireNonNull(criteria);
        final UserRef userRef = Objects.requireNonNull(criteria.getUserRef());
        final String userUuid = userRef.getUuid();

        final boolean hasPermission = securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION)
                                      || securityContext.isCurrentUser(userRef);

        if (!hasPermission) {
            final UserRef decoratedUserRef = userCache.getByUuid(userUuid)
                    .map(User::asRef)
                    .orElse(userRef);
            throw new PermissionException(
                    userRef,
                    "You do not have permission to view the dependencies on user "
                    + decoratedUserRef.toInfoString());
        }

        final List<UserDependency> allUserDependencies = NullSafe.valuesOf(hasUserDependenciesProviderMap)
                .stream()
                .map(Provider::get)
                .flatMap(hasUserDependencies ->
                        hasUserDependencies.getUserDependencies(UserRef.forUserUuid(userUuid)).stream())
                .map(userDependency -> {
                    try {
                        final DocRef docRef = NullSafe.get(userDependency.getDocRef(), docRefInfoService::decorate);
                        return new UserDependency(
                                userDependency.getUserRef(),
                                userDependency.getDetails(),
                                docRef);
                    } catch (final RuntimeException e) {
                        LOGGER.debug(e::getMessage, e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();

        LOGGER.debug(() -> LogUtil.message("Found {} userDependencies", allUserDependencies.size()));

        // TODO add in the criteria filtering
        final Comparator<UserDependency> defaultComparator = CompareUtil.getNullSafeCaseInsensitiveComparator(
                dep -> NullSafe.get(dep.getDocRef(), DocRef::getName));

        final CriteriaFieldSort fieldSort = NullSafe.first(criteria.getSortList());
        Comparator<UserDependency> comparator;
        if (fieldSort != null) {
            if (FindUserDependenciesCriteria.FIELD_DETAILS.equals(fieldSort.getId())) {
                comparator = CompareUtil.getNullSafeCaseInsensitiveComparator(UserDependency::getDetails);
                if (fieldSort.isDesc()) {
                    comparator = comparator.reversed();
                }
                comparator = comparator.thenComparing(defaultComparator);
            } else {
                comparator = defaultComparator;
                if (fieldSort.isDesc()) {
                    comparator = comparator.reversed();
                }
            }
        } else {
            comparator = defaultComparator;
        }

        final List<UserDependency> filteredUserDependencies = allUserDependencies.stream()
                .filter(userDependency ->
                        NullSafe.getOrElse(
                                userDependency.getDocRef(),
                                docRef -> securityContext.hasDocumentPermission(docRef, DocumentPermission.VIEW),
                                true))
                .sorted(comparator)
                .toList();

        LOGGER.debug(() -> LogUtil.message("Returning {} filtered userDependencies", filteredUserDependencies.size()));
        return ResultPage.createCriterialBasedList(filteredUserDependencies, criteria);
    }

    private void fireUserChangeEvent(final UserRef userRef) {
        Objects.requireNonNull(userRef);
        PermissionChangeEvent.fire(permissionChangeEventBus, userRef, null);
    }

    @Deprecated
    @Override
    public UserRef getUserRef(final String subjectId, final boolean isGroup) {
        final Optional<User> optUser = isGroup
                ? userDao.getGroupByName(subjectId)
                : userDao.getUserBySubjectId(subjectId);
        return optUser.map(User::asRef)
                .orElse(null);
    }
}
