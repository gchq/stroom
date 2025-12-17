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

package stroom.app;

import stroom.cluster.lock.api.ClusterLockService;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.security.api.AppPermissionService;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserService;
import stroom.security.identity.account.AccountService;
import stroom.security.identity.config.IdentityConfig;
import stroom.security.identity.config.PasswordPolicyConfig;
import stroom.security.identity.shared.Account;
import stroom.security.identity.shared.CreateAccountRequest;
import stroom.security.impl.StroomOpenIdConfig;
import stroom.security.openid.api.IdpType;
import stroom.security.shared.AppPermission;
import stroom.security.shared.User;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserDesc;
import stroom.util.shared.UserRef;

import event.logging.AddGroups;
import event.logging.AuthoriseEventAction;
import event.logging.CreateEventAction;
import event.logging.CreateEventAction.Builder;
import event.logging.Group;
import event.logging.Outcome;
import event.logging.RemoveGroups;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Class for setting up the default admin user account if isAutoCreateAdminAccountOnBoot is true and
 * stroom is configured with idpType INTERNAL_IDP or TEST_CREDENTIALS.
 * It is intended to run once at boot time on a single node.
 */
public class AdminAccountBootstrap {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AdminAccountBootstrap.class);

    public static final String ADMIN_ACCOUNT_NAME = User.ADMIN_USER_SUBJECT_ID;
    public static final String ADMIN_ACCOUNT_PASSWORD = ADMIN_ACCOUNT_NAME;
    public static final String ADMINISTRATION_GROUP_NAME = User.ADMINISTRATORS_GROUP_SUBJECT_ID;
    public static final Set<AppPermission> ADMINISTRATION_GROUP_PERMS = EnumSet.of(AppPermission.ADMINISTRATOR);

    private static final String LOCK_NAME = "AdminAccountBootstrap";
    private static final String BASE_TYPE = AdminAccountBootstrap.class.getSimpleName();

    private final AccountService accountService;
    private final AppPermissionService appPermissionService;
    private final AppPermissionService userAppPermissionService;
    private final ClusterLockService clusterLockService;
    private final Provider<IdentityConfig> identityConfigProvider;
    private final SecurityContext securityContext;
    private final StroomEventLoggingService stroomEventLoggingService;
    private final Provider<StroomOpenIdConfig> stroomOpenIdConfigProvider;
    private final UserService userService;

    @Inject
    public AdminAccountBootstrap(final AccountService accountService,
                                 final AppPermissionService appPermissionService,
                                 final AppPermissionService userAppPermissionService,
                                 final ClusterLockService clusterLockService,
                                 final Provider<IdentityConfig> identityConfigProvider,
                                 final SecurityContext securityContext,
                                 final StroomEventLoggingService stroomEventLoggingService,
                                 final Provider<StroomOpenIdConfig> stroomOpenIdConfigProvider,
                                 final UserService userService) {
        this.accountService = accountService;
        this.appPermissionService = appPermissionService;
        this.userAppPermissionService = userAppPermissionService;
        this.clusterLockService = clusterLockService;
        this.identityConfigProvider = identityConfigProvider;
        this.securityContext = securityContext;
        this.stroomEventLoggingService = stroomEventLoggingService;
        this.stroomOpenIdConfigProvider = stroomOpenIdConfigProvider;
        this.userService = userService;
    }

    public void startup() {
        LOGGER.debug("startup() - Called");
        securityContext.asProcessingUser(() -> {
            if (isEnabled()) {
                final Set<Item> allItems = Item.allItems();
                if (!checkItemsPresent().containsAll(allItems)) {
                    // We will likely only come in here once per node, so future reboots will not
                    // be impacted.

                    // TODO We ought to be using tryLock, but on 7.10 that is using ClusterLockClusterHandler
                    //  rather than DB record locking. I got errors maybe due to trying to lock
                    //  before the cluster is fully established. tryLock() has changed in 7.11 so switch to
                    //  that in 7.11+.
                    clusterLockService.lock(LOCK_NAME, () -> {
                        LOGGER.debug("startup() - acquired lock");
                        // Re-check under lock
                        final Set<Item> itemsPresent = checkItemsPresent();
                        if (!itemsPresent.containsAll(allItems)) {
                            ensureItemsArePresent(itemsPresent);
                        }
                        LOGGER.debug("startup() - releasing lock");
                    });
                } else {
                    LOGGER.debug("startup() - All items present");
                }
            } else {
                LOGGER.debug("startup() - Disabled");
            }
        });
    }

    private void ensureItemsArePresent(final Set<Item> itemsPresent) {
        LOGGER.info(
                "Bootstrapping the default administrator account under lock. Login with admin/admin.");
        if (!itemsPresent.contains(Item.ACCOUNT)) {
            createAccount();
        }
        User user = null;
        User group = null;
        if (!itemsPresent.contains(Item.USER)) {
            user = createUser();
        }
        if (!itemsPresent.contains(Item.GROUP)) {
            group = createGroup(user);
        }
        if (!itemsPresent.contains(Item.PERMISSIONS)) {
            ensurePermissions(group);
        }
        LOGGER.info("Completed bootstrapping the default administrator account. " +
                    "Login with admin/admin.");
    }

    private void createAccount() {
        final boolean forcePasswordChange = NullSafe.get(identityConfigProvider.get(),
                IdentityConfig::getPasswordPolicyConfig,
                PasswordPolicyConfig::isForcePasswordChangeOnFirstLogin);

        final CreateAccountRequest createAccountRequest = new CreateAccountRequest(
                null,
                null,
                ADMIN_ACCOUNT_NAME,
                null,
                "Auto created by Stroom",
                ADMIN_ACCOUNT_PASSWORD,
                ADMIN_ACCOUNT_PASSWORD,
                forcePasswordChange,
                true);
        // This will also create the stroom user for the account
        accountService.create(createAccountRequest);
        final String msg = LogUtil.message("Created Stroom user account '{}'", ADMIN_ACCOUNT_NAME);
        logAccountCreationEvent(ADMIN_ACCOUNT_NAME, true, msg);
        LOGGER.info(msg);
    }

    private User createUser() {
        final User user = userService.getOrCreateUser(ADMIN_ACCOUNT_NAME);
        LOGGER.info("Created Stroom user '{}'", ADMIN_ACCOUNT_NAME);
        logCreateUserEvent(UserDesc.forSubjectId(ADMIN_ACCOUNT_NAME), true, null);
        return user;
    }

    private User createGroup(final User user) {
        final User group = userService.getOrCreateUserGroup(ADMINISTRATION_GROUP_NAME);

        final User user2;
        if (user == null) {
            user2 = userService.getUserBySubjectId(ADMIN_ACCOUNT_NAME)
                    .orElseThrow(() -> new RuntimeException("User not found with subjectId " + ADMIN_ACCOUNT_NAME));

        } else {
            user2 = user;
        }

        userService.addUserToGroup(user2.asRef(), group.asRef());
        LOGGER.info("Created Stroom user group '{}'", ADMINISTRATION_GROUP_NAME);
        logCreateGroupEvent(ADMINISTRATION_GROUP_NAME, true, null);
        return group;
    }

    private void ensurePermissions(final User group) {
        final String groupName = ADMINISTRATION_GROUP_NAME;
        final User group2;
        if (group == null) {
            group2 = userService.getGroupByName(groupName)
                    .orElseThrow(() -> new RuntimeException("Group not found with name " + groupName));
        } else {
            group2 = group;
        }
        for (final AppPermission perm : ADMINISTRATION_GROUP_PERMS) {
            userAppPermissionService.addPermission(group2.asRef(), perm);
            logAddOrRemovePermissionEvent(
                    groupName,
                    perm.getDisplayValue(),
                    true,
                    null,
                    true);
        }
        LOGGER.info("Added permissions '{}' to user group '{}'", ADMINISTRATION_GROUP_PERMS, groupName);
    }

    private boolean isEnabled() {
        final IdpType idpType = stroomOpenIdConfigProvider.get().getIdentityProviderType();
        final boolean isAutoCreateAdminAccountOnBoot = identityConfigProvider.get().isAutoCreateAdminAccountOnBoot();
        final boolean isEnabled = isAutoCreateAdminAccountOnBoot
                                  && (idpType == IdpType.INTERNAL_IDP || idpType == IdpType.TEST_CREDENTIALS);
        LOGGER.debug("isEnabled() - isAutoCreateAdminAccountOnBoot: {}, idpType: {}, returning: {}",
                isAutoCreateAdminAccountOnBoot, idpType, isEnabled);
        return isEnabled;
    }

    private Set<Item> checkItemsPresent() {
        final EnumSet<Item> items = EnumSet.noneOf(Item.class);

        if (isAdminAccountPresent()) {
            items.add(Item.ACCOUNT);
        }

        if (isUserPresent()) {
            items.add(Item.USER);
        }

        final Optional<User> administratorGroup = getAdministratorGroup();
        if (administratorGroup.isPresent()) {
            items.add(Item.GROUP);
            final User group = administratorGroup.get();
            if (arePermissionsSet(group.asRef())) {
                items.add(Item.PERMISSIONS);
            }
        }
        LOGGER.debug("checkItemsPresent() - items: {}", items);
        return items;
    }

    private boolean isAdminAccountPresent() {
        final Optional<Account> optAccount = accountService.read(ADMIN_ACCOUNT_NAME);
        LOGGER.debug("isAdminAccountPresent() - account: {}", optAccount);
        return optAccount.isPresent();
    }

    private boolean isUserPresent() {
        final Optional<User> optUser = userService.getUserBySubjectId(ADMIN_ACCOUNT_NAME);
        LOGGER.debug("isUserPresent() - user: {}", optUser);
        return optUser.isPresent();
    }

//    private Optional<User> getUser() {
//        final Optional<User> optUser = userService.getUserBySubjectId(ADMIN_ACCOUNT_NAME);
//        LOGGER.debug("isUserPresent() - user: {}", optUser);
//        return optUser;
//    }
//
//    private boolean isAdministratorsGroupPresent() {
//        final Optional<User> optGroup = userService.getGroupByName(ADMINISTRATION_GROUP_NAME);
//        LOGGER.debug("isAdministratorsGroupPresent() - group: {}", optGroup);
//        return optGroup.isPresent();
//    }

    private Optional<User> getAdministratorGroup() {
        final Optional<User> optGroup = userService.getGroupByName(ADMINISTRATION_GROUP_NAME);
        LOGGER.debug("isAdministratorsGroupPresent() - group: {}", optGroup);
        return optGroup;
    }

    private boolean arePermissionsSet(final UserRef groupRef) {
        final Set<AppPermission> perms = appPermissionService.getDirectAppUserPermissions(groupRef);
        LOGGER.debug("arePermissionsSet() - groupRef: {}, perms: {}", groupRef, perms);
        return perms.containsAll(ADMINISTRATION_GROUP_PERMS);
    }

    private void logAccountCreationEvent(final String username,
                                         final boolean wasSuccessful,
                                         final String description) {

        stroomEventLoggingService.log(
                buildTypeId("createAccount"),
                LogUtil.message("An account for user {} was created in the internal identity provider", username),
                CreateEventAction.builder()
                        .addUser(event.logging.User.builder()
                                .withName(username)
                                .build())
                        .withOutcome(Outcome.builder()
                                .withSuccess(wasSuccessful)
                                .withDescription(description)
                                .build())
                        .build());
    }

    private void logCreateUserEvent(final UserDesc username,
                                    final boolean wasSuccessful,
                                    final String description) {

        final Builder<Void> createEventActionBuilder = CreateEventAction.builder();

        // For some reason IJ doesn't like event.logging.User.builder()
        createEventActionBuilder.addUser(event.logging.User.builder()
                .withId(username.getSubjectId())
                .withName(username.getDisplayName())
                .build());

        final CreateEventAction createEventAction = createEventActionBuilder.withOutcome(
                        Outcome.builder()
                                .withSuccess(wasSuccessful)
                                .withDescription(description)
                                .build())
                .build();

        stroomEventLoggingService.log(
                buildTypeId("createUser"),
                LogUtil.message(
                        "Create user '{}'",
                        username),
                createEventAction);
    }

    private void logCreateGroupEvent(final String groupName,
                                     final boolean wasSuccessful,
                                     final String description) {

        final Builder<Void> createEventActionBuilder = CreateEventAction.builder();

        createEventActionBuilder.addGroup(Group.builder()
                .withName(groupName)
                .build());

        final CreateEventAction createEventAction = createEventActionBuilder.withOutcome(
                        Outcome.builder()
                                .withSuccess(wasSuccessful)
                                .withDescription(description)
                                .build())
                .build();

        stroomEventLoggingService.log(
                buildTypeId("createGroup"),
                LogUtil.message(
                        "Create group '{}'", groupName),
                createEventAction);
    }

    private void logAddOrRemovePermissionEvent(final String userOrGroupName,
                                               final String permission,
                                               final boolean wasSuccessful,
                                               final String outcomeDescription,
                                               final boolean isAddingPermission) {

        final AuthoriseEventAction.Builder<Void> authoriseBuilder = AuthoriseEventAction.builder();

        // Don't know if userOrGroupName is a user or group, so treat as a user

        authoriseBuilder.addUser(event.logging.User.builder()
                .withId(userOrGroupName)
                .withName(userOrGroupName)
                .build());

        final Group groupBlock = Group.builder()
                .withId(permission)
                .withName(permission)
                .build();
        if (isAddingPermission) {
            authoriseBuilder.withAddGroups(AddGroups.builder()
                    .addGroups(groupBlock)
                    .build());
        } else {
            authoriseBuilder.withRemoveGroups(RemoveGroups.builder()
                    .addGroups(groupBlock)
                    .build());
        }

        final String description = LogUtil.message("{} permission '{}' {} user/group '{}'",
                (isAddingPermission
                        ? "Grant"
                        : "Revoke"),
                permission,
                (isAddingPermission
                        ? "to"
                        : "from"),
                userOrGroupName);

        stroomEventLoggingService.log(
                buildTypeId("addPermissionToGroup"),
                description,
                authoriseBuilder
                        .withOutcome(Outcome.builder()
                                .withSuccess(wasSuccessful)
                                .withDescription(outcomeDescription)
                                .build())
                        .build());
    }

    private String buildTypeId(final String subCommand) {
        return String.join(".",
                BASE_TYPE,
                Objects.requireNonNull(subCommand));
    }


    // --------------------------------------------------------------------------------


    private enum Item {
        ACCOUNT,
        USER,
        GROUP,
        PERMISSIONS,
        ;

        private static Set<Item> allItems() {
            return EnumSet.allOf(Item.class);
        }
    }
}
