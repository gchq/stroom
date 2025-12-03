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

package stroom.app.commands;

import stroom.config.app.Config;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.api.StroomEventLoggingUtil;
import stroom.security.api.AppPermissionService;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserService;
import stroom.security.shared.AppPermission;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserDesc;
import stroom.util.shared.UserRef;
import stroom.util.user.UserDescUtil;

import com.google.inject.Injector;
import event.logging.AddGroups;
import event.logging.AuthoriseEventAction;
import event.logging.CreateEventAction;
import event.logging.CreateEventAction.Builder;
import event.logging.Group;
import event.logging.Outcome;
import event.logging.RemoveGroups;
import event.logging.User;
import io.dropwizard.core.setup.Bootstrap;
import jakarta.inject.Inject;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Creates an account in the internal identity provider
 * <p>
 * e.g manage_users ../local.yml
 * --createUser admin
 * --createGroup Administrators
 * --addToGroup admin Administrators
 * --grantPermission Administrators Administrator
 */
public class ManageUsersCommand extends AbstractStroomAppCommand {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ManageUsersCommand.class);

    private static final String COMMAND_NAME = "manage_users";
    private static final String COMMAND_DESCRIPTION = "Create users/groups and manage application permissions";

    private static final String CREATE_USER_ARG_NAME = "createUser";
    private static final String CREATE_GROUP_ARG_NAME = "createGroup";
    private static final String ADD_TO_GROUP_ARG_NAME = "addToGroup";
    private static final String REMOVE_FROM_GROUP_ARG_NAME = "removeFromGroup";
    private static final String GRANT_PERMISSION_ARG_NAME = "grantPermission";
    private static final String REVOKE_PERMISSION_ARG_NAME = "revokePermission";
    private static final String LIST_PERMISSIONS_ARG_NAME = "listPermissions";

    private static final Set<String> ARGUMENT_NAMES = Set.of(
            CREATE_USER_ARG_NAME,
            CREATE_GROUP_ARG_NAME,
            ADD_TO_GROUP_ARG_NAME,
            REMOVE_FROM_GROUP_ARG_NAME,
            GRANT_PERMISSION_ARG_NAME,
            REVOKE_PERMISSION_ARG_NAME,
            LIST_PERMISSIONS_ARG_NAME);

    private static final String USER_META_VAR = "USER_ID";
    private static final String GROUP_META_VAR = "GROUP_ID";
    private static final String USER_OR_GROUP_META_VAR = "USER_OR_GROUP_ID";
    private static final String TARGET_GROUP_META_VAR = "TARGET_GROUP_ID";
    private static final String PERMISSION_NAME_META_VAR = "PERMISSION_NAME";

    @Inject
    private SecurityContext securityContext;
    @Inject
    private UserService userService;
    @Inject
    private AppPermissionService userAppPermissionService;
    @Inject
    private StroomEventLoggingService stroomEventLoggingService;

    public ManageUsersCommand(final Path configFile) {
        super(configFile, COMMAND_NAME, COMMAND_DESCRIPTION);
    }

    @Override
    public void configure(final Subparser subparser) {
        super.configure(subparser);

        subparser.addArgument(asArg(CREATE_USER_ARG_NAME))
                .dest(CREATE_USER_ARG_NAME)
                .action(Arguments.append())
                .type(String.class)
                .nargs(1)
                .metavar(USER_META_VAR)
                .help("The id of the user to create, e.g. 'admin'");

        subparser.addArgument(asArg(CREATE_GROUP_ARG_NAME))
                .dest(CREATE_GROUP_ARG_NAME)
                .action(Arguments.append())
                .type(String.class)
                .nargs(1)
                .metavar(GROUP_META_VAR)
                .help("The id of the group to create, e.g. 'Administrators'");

        subparser.addArgument(asArg(ADD_TO_GROUP_ARG_NAME))
                .dest(ADD_TO_GROUP_ARG_NAME)
                .action(Arguments.append())
                .type(String.class)
                .nargs(2)
                .required(false)
                .metavar(USER_OR_GROUP_META_VAR, TARGET_GROUP_META_VAR)
                .help("The name of the user/group being added and the group to add it to " +
                      "e.g. 'admin Administrators'");

        subparser.addArgument(asArg(REMOVE_FROM_GROUP_ARG_NAME))
                .dest(REMOVE_FROM_GROUP_ARG_NAME)
                .action(Arguments.append())
                .type(String.class)
                .nargs(2)
                .required(false)
                .metavar(USER_OR_GROUP_META_VAR, TARGET_GROUP_META_VAR)
                .help("The name of the user/group being removed and the group it is being removed from, " +
                      "e.g. 'admin Administrators'");

        subparser.addArgument(asArg(GRANT_PERMISSION_ARG_NAME))
                .dest(GRANT_PERMISSION_ARG_NAME)
                .action(Arguments.append())
                .type(String.class)
                .nargs(2)
                .metavar(USER_OR_GROUP_META_VAR, PERMISSION_NAME_META_VAR)
                .help("The name of the user/group and the name of the application permission to " +
                      "grant to it, e.g. 'Administrators Administrator'.");

        subparser.addArgument(asArg(REVOKE_PERMISSION_ARG_NAME))
                .dest(REVOKE_PERMISSION_ARG_NAME)
                .action(Arguments.append())
                .type(String.class)
                .nargs(2)
                .metavar(USER_OR_GROUP_META_VAR, PERMISSION_NAME_META_VAR)
                .help("The name of the user/group and the name of the application permission to " +
                      "revoke from it, e.g. 'Administrators Administrator'.");

        subparser.addArgument(asArg(LIST_PERMISSIONS_ARG_NAME))
                .dest(LIST_PERMISSIONS_ARG_NAME)
                .action(Arguments.storeTrue())
                .help("List the valid permission names.");
    }

    @Override
    public Set<String> getArgumentNames() {
        return ARGUMENT_NAMES;
    }

    @Override
    protected void runSecuredCommand(final Bootstrap<Config> bootstrap,
                                     final Namespace namespace,
                                     final Config config,
                                     final Injector injector) {

        LOGGER.debug("Namespace {}", namespace);
        injector.injectMembers(this);

        // Order is important here
        createUsers(namespace);
        createGroups(namespace);
        addToGroups(namespace);
        removeFromGroups(namespace);
        grantPermissions(namespace);
        revokePermissions(namespace);
        listPermissions(namespace);
    }

    private void listPermissions(final Namespace namespace) {
        if (namespace.getBoolean(LIST_PERMISSIONS_ARG_NAME)) {
            final String perms = Arrays.stream(AppPermission.values())
                    .map(AppPermission::getDisplayValue)
                    .sorted()
                    .collect(Collectors.joining("\n"));
            LOGGER.info("Valid application permission names:\n" +
                        "--------------------\n" +
                        "  {}\n" +
                        "--------------------",
                    perms);
        }
    }

    private void createUsers(final Namespace namespace) {
        final List<String> users = extractStrings(namespace, CREATE_USER_ARG_NAME);
        users.stream()
                .map(userCsvData -> {
                    try {
                        return UserDescUtil.parseSingleCSVUser(userCsvData)
                                .orElseThrow(() ->
                                        new RuntimeException(LogUtil.message(
                                                "No username supplied for {}. Argument value: '{}'",
                                                CREATE_USER_ARG_NAME, userCsvData)));
                    } catch (final RuntimeException e) {
                        throw new RuntimeException(LogUtil.message(
                                "Error parsing value for {}. Argument value: '{}'",
                                CREATE_USER_ARG_NAME, userCsvData));
                    }
                })
                .forEach(this::createUser);
    }


    private void createGroups(final Namespace namespace) {
        final List<String> groups = extractStrings(namespace, CREATE_GROUP_ARG_NAME);
        groups.forEach(this::createGroup);
    }

    private void createUser(final UserDesc userName) {
        final String msg = LogUtil.message("Creating user '{}'", userName);
        info(LOGGER, msg);

        try {
            userService.getUserBySubjectId(userName.getSubjectId())
                    .ifPresentOrElse(
                            user -> {
                                if (!user.isEnabled()) {
                                    final String outcomeMsg = LogUtil
                                            .message("User '{}' already exists but is disabled, enabling",
                                                    user.asRef().toInfoString());
                                    user.setEnabled(true);
                                    userService.update(user);
                                    indentedWarn(LOGGER, outcomeMsg, "  ");
                                    logCreateUserEvent(userName, false, outcomeMsg);
                                } else {
                                    final String outcomeMsg = LogUtil.message("User '{}' already exists",
                                            user.asRef().toInfoString());
                                    indentedWarn(LOGGER, outcomeMsg, "  ");
                                    logCreateUserEvent(userName, false, outcomeMsg);
                                }
                            },
                            () -> {
                                userService.getOrCreateUser(userName);
                                logCreateUserEvent(userName, true, null);
                                indentedInfo(LOGGER,
                                        "Created user '" + userName + "'",
                                        "  ");
                            });
        } catch (final Exception e) {
            LOGGER.debug("Error", e);
            throw new RuntimeException("Error " + msg + ":" + e.getMessage(), e);
        }
    }

    private void createGroup(final String groupName) {
        final String msg = LogUtil.message("Creating group '{}'", groupName);
        info(LOGGER, msg);

        try {
            userService.getGroupByName(groupName)
                    .ifPresentOrElse(
                            user -> {
                                if (!user.isEnabled()) {
                                    final String outcomeMsg = LogUtil
                                            .message("Group '{}' already exists but is disabled, enabling", groupName);
                                    user.setEnabled(true);
                                    userService.update(user);
                                    indentedWarn(LOGGER, outcomeMsg, "  ");
                                    logCreateGroupEvent(groupName, true, outcomeMsg);
                                } else {
                                    final String outcomeMsg = LogUtil.message("Group '{}' already exists", groupName);
                                    indentedWarn(LOGGER, outcomeMsg, "  ");
                                    logCreateGroupEvent(groupName, false, outcomeMsg);
                                }
                            },
                            () -> {
                                userService.getOrCreateUserGroup(groupName);
                                logCreateGroupEvent(groupName, true, null);
                                indentedInfo(LOGGER, "Created group '" + groupName + "'", "  ");
                            });
        } catch (final Exception e) {
            LOGGER.debug("Error", e);
            throw new RuntimeException("Error " + msg + ":" + e.getMessage(), e);
        }
    }

    private void addToGroups(final Namespace namespace) {
        final List<GroupArgs> groupArgsList = extractGroupArgs(namespace, ADD_TO_GROUP_ARG_NAME);
        groupArgsList.forEach(groupArgs -> {
            final String userOrGroupId = groupArgs.userOrGroupId;
            final String targetGroupId = groupArgs.targetGroupId;
            final String msg = LogUtil.message("Adding '{}' to group '{}'",
                    userOrGroupId, targetGroupId);
            info(LOGGER, msg);

            try {
                final UserRef userOrGroup = getUserObject(
                        userOrGroupId, UserType.EITHER, ADD_TO_GROUP_ARG_NAME);
                final UserRef targetGroup = getUserObject(
                        targetGroupId, UserType.GROUP, ADD_TO_GROUP_ARG_NAME);

                userService.addUserToGroup(userOrGroup, targetGroup);
                logAddOrRemoveFromGroupEvent(
                        userOrGroup,
                        targetGroup,
                        true,
                        null,
                        true);
                indentedInfo(LOGGER,
                        "Added '" + userOrGroup.toDisplayString() + "' to group '"
                        + targetGroup.toDisplayString() + "'",
                        "  ");

            } catch (final Exception e) {
                LOGGER.debug("Error", e);
                logAddOrRemoveFromGroupEvent(
                        userOrGroupId,
                        targetGroupId,
                        false,
                        e.getMessage(),
                        true);
                throw new RuntimeException("Error " + msg + ":" + e.getMessage(), e);
            }
        });
    }

    private void removeFromGroups(final Namespace namespace) {
        final List<GroupArgs> groupArgsList = extractGroupArgs(namespace, REMOVE_FROM_GROUP_ARG_NAME);
        groupArgsList.forEach(groupArgs -> {
            final String userOrGroupId = groupArgs.userOrGroupId;
            final String targetGroupId = groupArgs.targetGroupId;
            final String msg = LogUtil.message("Removing '{}' from group '{}'",
                    userOrGroupId, targetGroupId);
            info(LOGGER, msg);

            try {
                final UserRef userOrGroup = getUserObject(
                        userOrGroupId, UserType.EITHER, REMOVE_FROM_GROUP_ARG_NAME);
                final UserRef targetGroup = getUserObject(
                        targetGroupId, UserType.GROUP, REMOVE_FROM_GROUP_ARG_NAME);

                userService.removeUserFromGroup(userOrGroup, targetGroup);

                logAddOrRemoveFromGroupEvent(
                        userOrGroup,
                        targetGroup,
                        true,
                        null,
                        false);
                indentedInfo(LOGGER,
                        "Removed " + userOrGroup.toDisplayString()
                        + " from group " + targetGroup.toDisplayString(),
                        "  ");

            } catch (final Exception e) {
                LOGGER.debug("Error", e);
                logAddOrRemoveFromGroupEvent(
                        userOrGroupId,
                        targetGroupId,
                        false,
                        null,
                        false);
                throw new RuntimeException("Error " + msg + ":" + e.getMessage(), e);
            }
        });
    }

    private void grantPermissions(final Namespace namespace) {
        final List<PermissionArgs> permissionArgsList = extractPermissionArgs(namespace, GRANT_PERMISSION_ARG_NAME);
        permissionArgsList.forEach(permissionArgs -> {
            final AppPermission permission = AppPermission.getPermissionForName(permissionArgs.permissionName);
            final String userOrGroupId = permissionArgs.userOrGroupId;
            final String msg = LogUtil.message("Granting application permission '{}' to '{}'",
                    permission, userOrGroupId);
            info(LOGGER, msg);

            try {
                final UserRef userOrGroup = getUserObject(
                        userOrGroupId, UserType.EITHER, GRANT_PERMISSION_ARG_NAME);

                if (hasPermission(userOrGroup, permission)) {
                    final String failMsg = LogUtil.message("{} '{}' already has permission '{}'",
                            userOrGroup.getType(),
                            userOrGroup.toDisplayString(),
                            permission);
                    indentedWarn(LOGGER, failMsg, "  ");

                    logAddOrRemovePermissionEvent(
                            userOrGroup,
                            permission,
                            false,
                            failMsg,
                            true);
                } else {
                    userAppPermissionService.addPermission(userOrGroup, permission);

                    logAddOrRemovePermissionEvent(
                            userOrGroupId,
                            permission.getDisplayValue(),
                            true,
                            null,
                            true);
                    indentedInfo(LOGGER, LogUtil.message("Granted application permission '{}' to '{}'",
                            permission, userOrGroup.toInfoString()), "  ");
                }
            } catch (final Exception e) {
                LOGGER.debug("Error", e);
                logAddOrRemoveFromGroupEvent(
                        userOrGroupId,
                        permission.getDisplayValue(),
                        false,
                        e.getMessage(),
                        true);
                throw new RuntimeException("Error " + msg + ":" + e.getMessage(), e);
            }
        });
    }

    private void revokePermissions(final Namespace namespace) {
        final List<PermissionArgs> permissionArgsList = extractPermissionArgs(
                namespace,
                REVOKE_PERMISSION_ARG_NAME);

        permissionArgsList.forEach(permissionArgs -> {
            final AppPermission permission = AppPermission.getPermissionForName(permissionArgs.permissionName);
            final String userOrGroupId = permissionArgs.userOrGroupId;
            final String msg = LogUtil.message("Revoking application permission '{}' from '{}'",
                    permission, userOrGroupId);

            info(LOGGER, msg);

            final UserRef userOrGroup = getUserObject(
                    userOrGroupId, UserType.EITHER, REVOKE_PERMISSION_ARG_NAME);

            try {
                if (hasPermission(userOrGroup, permission)) {
                    userAppPermissionService.removePermission(
                            userOrGroup,
                            permission);

                    indentedInfo(LOGGER, LogUtil.message("Revoked application permission '{}' from '{}'",
                            permission, userOrGroup.toDisplayString()), "  ");

                    logAddOrRemovePermissionEvent(
                            userOrGroup,
                            permission,
                            true,
                            null,
                            false);
                } else {
                    final String failMsg = LogUtil.message("{} '{}' does not have permission '{}'",
                            userOrGroup.getType(),
                            userOrGroup.toDisplayString(),
                            permission);
                    indentedWarn(LOGGER, failMsg, "  ");

                    logAddOrRemovePermissionEvent(
                            userOrGroupId,
                            permission.getDisplayValue(),
                            false,
                            failMsg,
                            false);
                }
            } catch (final Exception e) {
                LOGGER.debug("Error", e);
                logAddOrRemoveFromGroupEvent(
                        userOrGroupId,
                        permission.getDisplayValue(),
                        false,
                        e.getMessage(),
                        false);
                throw new RuntimeException("Error " + msg + ":" + e.getMessage(), e);
            }
        });
    }

    private boolean hasPermission(final UserRef userOrGroup,
                                  final AppPermission permission) {
        return userAppPermissionService.getDirectAppUserPermissions(userOrGroup)
                .stream()
                .anyMatch(perm -> perm.equals(permission));
    }

    private UserRef getUserObject(final String subjectId,
                                  final UserType expectedUserType,
                                  final String argGroupName) {
        Objects.requireNonNull(expectedUserType);
        if (NullSafe.isBlankString(subjectId)) {
            throw new RuntimeException(LogUtil.message("No {} identifier supplied for arg group '{}'",
                    expectedUserType.getDisplayName(), argGroupName));
        }

        final Optional<stroom.security.shared.User> optUserOrGroup = switch (expectedUserType) {
            case USER -> userService.getUserBySubjectId(subjectId);
            case GROUP -> userService.getGroupByName(subjectId);
            case EITHER -> userService.getUserBySubjectId(subjectId).or(() -> userService.getGroupByName(subjectId));
        };

        if (optUserOrGroup.isEmpty()) {
            info(LOGGER, LogUtil.message("{} not found when treating '{}' in arg group '{}' as a unique subjectId. " +
                                         "Falling back to treating it as a displayName",
                    expectedUserType.displayName, subjectId, argGroupName));
        }
        final stroom.security.shared.User userOrGroup = optUserOrGroup
                .orElseThrow(() -> new RuntimeException(LogUtil.message(
                        "A {} cannot be found with a subjectId or displayName matching identifier '{}' " +
                        "in arg group '{}'",
                        expectedUserType.displayName,
                        subjectId,
                        argGroupName)));

        LOGGER.debug(() -> LogUtil.message("Found user object {}, treating identifier '{}' in arg group '{}' as a {}",
                userOrGroup,
                subjectId,
                argGroupName,
                "subjectId"));

        if (UserType.USER.equals(expectedUserType) && userOrGroup.isGroup()) {
            throw new RuntimeException(LogUtil.message("Expecting identifier '{}' in arg group '{}' to be a User: {}",
                    subjectId, argGroupName, userOrGroup));
        } else if (UserType.GROUP.equals(expectedUserType) && !userOrGroup.isGroup()) {
            throw new RuntimeException(LogUtil.message("Expecting identifier '{}' in arg group '{}' to be a Group: {}",
                    subjectId, argGroupName, userOrGroup));
        }

        return userOrGroup.asRef();
    }

    private List<String> extractStrings(final Namespace namespace, final String dest) {
        return extractArgs(namespace, dest, List::getFirst);
    }

    private List<GroupArgs> extractGroupArgs(final Namespace namespace, final String dest) {
        return extractArgs(namespace, dest, list -> new GroupArgs(list.getFirst(), list.get(1)));
    }

    private List<PermissionArgs> extractPermissionArgs(final Namespace namespace, final String dest) {
        return extractArgs(namespace, dest, list -> new PermissionArgs(list.getFirst(), list.get(1)));
    }

    private void logCreateUserEvent(final UserDesc username,
                                    final boolean wasSuccessful,
                                    final String description) {

        final Builder<Void> createEventActionBuilder = CreateEventAction.builder();

        // For some reason IJ doesn't like event.logging.User.builder()
        createEventActionBuilder.addUser(User.builder()
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
                buildTypeId(CREATE_USER_ARG_NAME),
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
                buildTypeId(CREATE_GROUP_ARG_NAME),
                LogUtil.message(
                        "Create group '{}'", groupName),
                createEventAction);
    }

    private String buildTypeId(final String subCommand) {
        return String.join(".",
                COMMAND_NAME,
                Objects.requireNonNull(subCommand));
    }

    private void logAddOrRemoveFromGroupEvent(final UserRef userOrGroup,
                                              final UserRef group,
                                              final boolean wasSuccessful,
                                              final String outcomeDescription,
                                              final boolean isAddingGroup) {

        final AuthoriseEventAction.Builder<Void> authoriseBuilder = AuthoriseEventAction.builder();

        if (userOrGroup.isGroup()) {
            authoriseBuilder.addGroup(StroomEventLoggingUtil.createGroup(userOrGroup));
        } else {
            authoriseBuilder.addUser(StroomEventLoggingUtil.createUser(userOrGroup));
        }

        if (isAddingGroup) {
            authoriseBuilder.withAddGroups(AddGroups.builder()
                    .addGroups(StroomEventLoggingUtil.createGroup(group))
                    .build());
        } else {
            authoriseBuilder.withRemoveGroups(RemoveGroups.builder()
                    .addGroups(StroomEventLoggingUtil.createGroup(group))
                    .build());
        }

        final String description = LogUtil.message("{} {} '{}' {} group '{}'",
                (isAddingGroup
                        ? "Add"
                        : "Remove"),
                (userOrGroup.isGroup()
                        ? "group"
                        : "user"),
                userOrGroup.toInfoString(),
                (isAddingGroup
                        ? "to"
                        : "from"),
                group.toInfoString());

        stroomEventLoggingService.log(
                "CliAddToGroup",
                description,
                authoriseBuilder
                        .withOutcome(Outcome.builder()
                                .withSuccess(wasSuccessful)
                                .withDescription(outcomeDescription)
                                .build())
                        .build());
    }


    private void logAddOrRemoveFromGroupEvent(final String userOrGroupName,
                                              final String groupName,
                                              final boolean wasSuccessful,
                                              final String outcomeDescription,
                                              final boolean isAddingGroup) {

        final AuthoriseEventAction.Builder<Void> authoriseBuilder = AuthoriseEventAction.builder();

        // Don't know if userOrGroupName is a user or group, so treat as a user

        authoriseBuilder.addUser(User.builder()
                .withId(userOrGroupName)
                .withName(userOrGroupName)
                .build());

        final Group groupBlock = Group.builder()
                .withId(groupName)
                .withName(groupName)
                .build();
        if (isAddingGroup) {
            authoriseBuilder.withAddGroups(AddGroups.builder()
                    .addGroups(groupBlock)
                    .build());
        } else {
            authoriseBuilder.withRemoveGroups(RemoveGroups.builder()
                    .addGroups(groupBlock)
                    .build());
        }

        final String description = LogUtil.message("{} user/group '{}' {} group '{}'",
                (isAddingGroup
                        ? "Add"
                        : "Remove"),
                userOrGroupName,
                (isAddingGroup
                        ? "to"
                        : "from"),
                groupName);

        stroomEventLoggingService.log(
                "CliAddToGroup",
                description,
                authoriseBuilder
                        .withOutcome(Outcome.builder()
                                .withSuccess(wasSuccessful)
                                .withDescription(outcomeDescription)
                                .build())
                        .build());
    }

    private void logAddOrRemovePermissionEvent(final UserRef userOrGroup,
                                               final AppPermission permission,
                                               final boolean wasSuccessful,
                                               final String outcomeDescription,
                                               final boolean isAddingPermission) {

        final AuthoriseEventAction.Builder<Void> authoriseBuilder = AuthoriseEventAction.builder();

        if (userOrGroup.isGroup()) {
            authoriseBuilder.addGroup(StroomEventLoggingUtil.createGroup(userOrGroup));
        } else {
            authoriseBuilder.addUser(StroomEventLoggingUtil.createUser(userOrGroup));
        }

        final Group groupBlock = Group.builder()
                .withId(permission.name())
                .withName(permission.getDisplayValue())
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

        final String description = LogUtil.message("{} permission '{}' {} {} '{}'",
                (isAddingPermission
                        ? "Grant"
                        : "Revoke"),
                permission,
                (isAddingPermission
                        ? "to"
                        : "from"),
                (userOrGroup.isGroup()
                        ? "group"
                        : "user"),
                userOrGroup.toInfoString());

        stroomEventLoggingService.log(
                "CliAddToGroup",
                description,
                authoriseBuilder
                        .withOutcome(Outcome.builder()
                                .withSuccess(wasSuccessful)
                                .withDescription(outcomeDescription)
                                .build())
                        .build());
    }

    private void logAddOrRemovePermissionEvent(final String userOrGroupName,
                                               final String permission,
                                               final boolean wasSuccessful,
                                               final String outcomeDescription,
                                               final boolean isAddingPermission) {

        final AuthoriseEventAction.Builder<Void> authoriseBuilder = AuthoriseEventAction.builder();

        // Don't know if userOrGroupName is a user or group, so treat as a user

        authoriseBuilder.addUser(User.builder()
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
                "CliAddToGroup",
                description,
                authoriseBuilder
                        .withOutcome(Outcome.builder()
                                .withSuccess(wasSuccessful)
                                .withDescription(outcomeDescription)
                                .build())
                        .build());
    }

    // --------------------------------------------------------------------------------


    private static class GroupArgs {

        private final String userOrGroupId;
        private final String targetGroupId;

        private GroupArgs(final String userOrGroupId, final String targetGroupId) {
            this.userOrGroupId = userOrGroupId;
            this.targetGroupId = targetGroupId;
        }

        @Override
        public String toString() {
            return "GroupArgs{" +
                   "userOrGroupId='" + userOrGroupId + '\'' +
                   ", targetGroupId='" + targetGroupId + '\'' +
                   '}';
        }
    }


    // --------------------------------------------------------------------------------


    private static class PermissionArgs {

        private final String userOrGroupId;
        private final String permissionName;

        private PermissionArgs(final String userOrGroupId, final String permissionName) {
            this.userOrGroupId = userOrGroupId;
            this.permissionName = permissionName;
        }

        @Override
        public String toString() {
            return "PermissionArgs{" +
                   "userOrGroupId='" + userOrGroupId + '\'' +
                   ", permissionName='" + permissionName + '\'' +
                   '}';
        }
    }


    // --------------------------------------------------------------------------------


    private enum UserType {
        USER("User"),
        GROUP("Group"),
        EITHER("User/Group");

        private final String displayName;

        UserType(final String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
