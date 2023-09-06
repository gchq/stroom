package stroom.app.commands;

import stroom.config.app.Config;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.api.StroomEventLoggingUtil;
import stroom.security.api.SecurityContext;
import stroom.security.impl.UserAppPermissionService;
import stroom.security.impl.UserService;
import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.UserName;
import stroom.util.user.UserNameUtil;

import com.google.inject.Injector;
import event.logging.AddGroups;
import event.logging.AuthoriseEventAction;
import event.logging.CreateEventAction;
import event.logging.CreateEventAction.Builder;
import event.logging.Group;
import event.logging.Outcome;
import event.logging.RemoveGroups;
import event.logging.User;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.inject.Inject;

/**
 * Creates an account in the internal identity provider
 * <p>
 * e.g manage_users ../local.yml
 * --createUser admin
 * --createGroup Administrators
 * --addToGroup admin Administrators
 * --grantPermission Administrators Administrator
 */
public class ManageUsersCommand extends AbstractStroomAccountConfiguredCommand {

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
    private UserAppPermissionService userAppPermissionService;
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
    protected void runCommand(final Bootstrap<Config> bootstrap,
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
            String perms = userAppPermissionService.getAllPermissionNames()
                    .stream()
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
                        return UserNameUtil.parseSingleCSVUser(userCsvData)
                                .orElseThrow(() ->
                                        new RuntimeException(LogUtil.message(
                                                "No username supplied for {}. Argument value: '{}'",
                                                CREATE_USER_ARG_NAME, userCsvData)));
                    } catch (RuntimeException e) {
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

    private void createUser(final UserName userName) {
        final String msg = LogUtil.message("Creating user '{}'", userName);
        info(LOGGER, msg);

        try {
            userService.getUserBySubjectId(userName.getSubjectId())
                    .ifPresentOrElse(
                            user -> {
                                final String outcomeMsg = LogUtil.message("User '{}' already exists",
                                        UserName.buildCombinedName(userName));
                                warn(LOGGER, outcomeMsg, "  ");
                                logCreateUserEvent(userName, false, outcomeMsg);
                            },
                            () -> {
                                userService.getOrCreateUser(userName);
                                logCreateUserEvent(userName, true, null);
                                info(LOGGER,
                                        "Created user " + UserName.buildCombinedName(userName),
                                        "  ");
                            });
        } catch (Exception e) {
            LOGGER.debug("Error", e);
            throw new RuntimeException("Error " + msg + ":" + e.getMessage(), e);
        }
    }

    private void createGroup(final String groupName) {
        final String msg = LogUtil.message("Creating group '{}'", groupName);
        info(LOGGER, msg);

        try {
            userService.getUserBySubjectId(groupName)
                    .ifPresentOrElse(
                            user -> {
                                final String outcomeMsg = LogUtil.message("Group '{}' already exists", groupName);
                                warn(LOGGER, outcomeMsg, "  ");
                                logCreateGroupEvent(groupName, false, outcomeMsg);
                            },
                            () -> {
                                userService.getOrCreateUserGroup(groupName);
                                logCreateGroupEvent(groupName, true, null);
                                info(LOGGER, "Created group " + groupName, "  ");
                            });
        } catch (Exception e) {
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
                final stroom.security.shared.User userOrGroup = getUserObject(
                        userOrGroupId, UserType.EITHER, ADD_TO_GROUP_ARG_NAME);
                final stroom.security.shared.User targetGroup = getUserObject(
                        targetGroupId, UserType.GROUP, ADD_TO_GROUP_ARG_NAME);

                userService.addUserToGroup(userOrGroup.getUuid(), targetGroup.getUuid());
                logAddOrRemoveFromGroupEvent(
                        userOrGroup,
                        targetGroup,
                        true,
                        null,
                        true);
                info(LOGGER,
                        "Added " + userOrGroup.asCombinedName() + " to group " + targetGroup.asCombinedName(),
                        "  ");

            } catch (Exception e) {
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
                final stroom.security.shared.User userOrGroup = getUserObject(
                        userOrGroupId, UserType.EITHER, REMOVE_FROM_GROUP_ARG_NAME);
                final stroom.security.shared.User targetGroup = getUserObject(
                        targetGroupId, UserType.GROUP, REMOVE_FROM_GROUP_ARG_NAME);

                userService.removeUserFromGroup(userOrGroup.getUuid(), targetGroup.getUuid());

                logAddOrRemoveFromGroupEvent(
                        groupArgs.userOrGroupId,
                        groupArgs.targetGroupId,
                        true,
                        null,
                        false);
                info(LOGGER,
                        "Removed " + userOrGroup.asCombinedName()
                                + " from group " + targetGroup.asCombinedName(),
                        "  ");

            } catch (Exception e) {
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
            final String permissionName = permissionArgs.permissionName;
            final String userOrGroupId = permissionArgs.userOrGroupId;
            final String msg = LogUtil.message("Granting application permission '{}' to '{}'",
                    permissionName, userOrGroupId);
            info(LOGGER, msg);

            try {
                final stroom.security.shared.User userOrGroup = getUserObject(
                        userOrGroupId, UserType.EITHER, GRANT_PERMISSION_ARG_NAME);

                if (hasPermission(userOrGroup, permissionName)) {
                    final String failMsg = LogUtil.message("{} '{}' already has permission '{}'",
                            getTypeName(userOrGroup), userOrGroup.asCombinedName(), permissionName);
                    warn(LOGGER, failMsg, "  ");

                    logAddOrRemoveFromGroupEvent(
                            userOrGroupId,
                            permissionName,
                            false,
                            failMsg,
                            true);
                } else {
                    userAppPermissionService.addPermission(
                            userOrGroup.getUuid(),
                            permissionName);

                    logAddOrRemoveFromGroupEvent(
                            userOrGroupId,
                            permissionName,
                            true,
                            null,
                            true);
                    info(LOGGER, LogUtil.message("Granted application permission '{}' to '{}'",
                            permissionName, userOrGroup.asCombinedName()), "  ");
                }
            } catch (Exception e) {
                LOGGER.debug("Error", e);
                logAddOrRemoveFromGroupEvent(
                        userOrGroupId,
                        permissionName,
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
            final String permissionName = permissionArgs.permissionName;
            final String userOrGroupId = permissionArgs.userOrGroupId;
            final String msg = LogUtil.message("Revoking application permission '{}' from '{}'",
                    permissionName, userOrGroupId);

            info(LOGGER, msg);

            final stroom.security.shared.User userOrGroup = getUserObject(
                    userOrGroupId, UserType.EITHER, REVOKE_PERMISSION_ARG_NAME);

            try {
                if (hasPermission(userOrGroup, permissionName)) {
                    userAppPermissionService.removePermission(
                            userOrGroup.getUuid(),
                            permissionName);

                    info(LOGGER, LogUtil.message("Revoked application permission '{}' from '{}'",
                            permissionName, userOrGroup.asCombinedName()), "  ");

                    logAddOrRemoveFromGroupEvent(
                            userOrGroupId,
                            permissionName,
                            true,
                            null,
                            false);
                } else {
                    final String failMsg = LogUtil.message("{} '{}' does not have permission '{}'",
                            getTypeName(userOrGroup), userOrGroup.asCombinedName(), permissionName);
                    warn(LOGGER, failMsg, "  ");

                    logAddOrRemoveFromGroupEvent(
                            userOrGroupId,
                            permissionName,
                            false,
                            failMsg,
                            false);
                }
            } catch (Exception e) {
                LOGGER.debug("Error", e);
                logAddOrRemoveFromGroupEvent(
                        userOrGroupId,
                        permissionName,
                        false,
                        e.getMessage(),
                        false);
                throw new RuntimeException("Error " + msg + ":" + e.getMessage(), e);
            }
        });
    }

    private String getTypeName(final stroom.security.shared.User user) {
        return NullSafe.get(user, stroom.security.shared.User::asCombinedName);
    }

    private boolean hasPermission(final stroom.security.shared.User userOrGroup, final String permissionName) {
        return userAppPermissionService.getPermissionNamesForUser(userOrGroup.getUuid())
                .stream()
                .anyMatch(perm -> perm.equals(permissionName));
    }

    private stroom.security.shared.User getUserObject(final String idOrName,
                                                      final UserType expectedUserType,
                                                      final String argGroupName) {
        Objects.requireNonNull(expectedUserType);
        if (NullSafe.isBlankString(idOrName)) {
            throw new RuntimeException(LogUtil.message("No {} identifier supplied for arg group '{}'",
                    expectedUserType.getDisplayName(), argGroupName));
        }
        // First treat idOrName as an ID
        Optional<stroom.security.shared.User> optUserOrGroup = userService.getUserBySubjectId(idOrName);

        final boolean isId = optUserOrGroup.isPresent();
        if (optUserOrGroup.isEmpty()) {
            info(LOGGER, LogUtil.message("{} not found when treating '{}' in arg group '{}' as a unique subjectId. " +
                            "Falling back to treating it as a displayName",
                    expectedUserType.displayName, idOrName, argGroupName));
        }

        // Fallback on treating idOrName as a displayName
        stroom.security.shared.User userOrGroup = optUserOrGroup.or(() -> userService.getUserByDisplayName(idOrName))
                .orElseThrow(() -> new RuntimeException(LogUtil.message(
                        "A {} cannot be found with a subjectId or displayName matching identifier '{}' " +
                                "in arg group '{}'",
                        expectedUserType.displayName,
                        idOrName,
                        argGroupName)));

        LOGGER.debug(() -> LogUtil.message("Found user object {}, treating identifier '{}' in arg group '{}' as a {}",
                userOrGroup,
                idOrName,
                argGroupName,
                (isId
                        ? "subjectId"
                        : "displayName")));

        if (UserType.USER.equals(expectedUserType) && userOrGroup.isGroup()) {
            throw new RuntimeException(LogUtil.message("Expecting identifier '{}' in arg group '{}' to be a User: {}",
                    idOrName, argGroupName, userOrGroup));
        } else if (UserType.GROUP.equals(expectedUserType) && !userOrGroup.isGroup()) {
            throw new RuntimeException(LogUtil.message("Expecting identifier '{}' in arg group '{}' to be a Group: {}",
                    idOrName, argGroupName, userOrGroup));
        }

        return userOrGroup;
    }

    private <T> List<T> extractArgs(final Namespace namespace,
                                    final String dest,
                                    final Function<List<String>, T> argsMapper) {
        final List<List<String>> values = namespace.get(dest);
        if (values != null) {
            return values.stream()
                    .map(argsMapper)
                    .collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }

    private List<String> extractStrings(final Namespace namespace, final String dest) {
        return extractArgs(namespace, dest, list -> list.get(0));
    }

    private List<GroupArgs> extractGroupArgs(final Namespace namespace, final String dest) {
        return extractArgs(namespace, dest, list -> new GroupArgs(list.get(0), list.get(1)));
    }

    private List<PermissionArgs> extractPermissionArgs(final Namespace namespace, final String dest) {
        return extractArgs(namespace, dest, list -> new PermissionArgs(list.get(0), list.get(1)));
    }

    private String asArg(final String name) {
        return "--" + name;
    }

    private void logCreateUserEvent(final UserName username,
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
                        "A Stroom user account for {} was created",
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
                        "A Stroom user account for {} {} was created", groupName),
                createEventAction);
    }

    private String buildTypeId(final String subCommand) {
        return String.join(".",
                COMMAND_NAME,
                Objects.requireNonNull(subCommand));
    }

    private void logCreateUserOrGroupEvent(final String username,
                                           final boolean wasSuccessful,
                                           final String description,
                                           final boolean isGroup) {

        final Builder<Void> createEventActionBuilder = CreateEventAction.builder();

        if (isGroup) {
            createEventActionBuilder.addGroup(Group.builder()
                    .withName(username)
                    .build());
        } else {
            // For some reason IJ doesn't like event.logging.User.builder()
            createEventActionBuilder.addUser(User.builder()
                    .withName(username)
                    .build());
        }

        final CreateEventAction createEventAction = createEventActionBuilder.withOutcome(
                        Outcome.builder()
                                .withSuccess(wasSuccessful)
                                .withDescription(description)
                                .build())
                .build();

        stroomEventLoggingService.log(
                "CliCreateStroom" + (isGroup
                        ? "Group"
                        : "User"),
                LogUtil.message(
                        "A Stroom user account for {} {} was created",
                        (isGroup
                                ? "group"
                                : "user"),
                        username),
                createEventAction);
    }

    private void logAddOrRemoveFromGroupEvent(final stroom.security.shared.User userOrGroup,
                                              final stroom.security.shared.User group,
                                              final boolean wasSuccessful,
                                              final String description,
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

        stroomEventLoggingService.log(
                "CliAddToGroup",
                LogUtil.message("User/Group {} was {} to group {}",
                        userOrGroup,
                        (isAddingGroup
                                ? "added to"
                                : "removed from"),
                        group),
                authoriseBuilder
                        .withOutcome(Outcome.builder()
                                .withSuccess(wasSuccessful)
                                .withDescription(description)
                                .build())
                        .build());
    }

    private void logAddOrRemoveFromGroupEvent(final String userOrGroupName,
                                              final String groupName,
                                              final boolean wasSuccessful,
                                              final String description,
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

        stroomEventLoggingService.log(
                "CliAddToGroup",
                LogUtil.message("User/Group {} was {} to group {}",
                        userOrGroupName,
                        (isAddingGroup
                                ? "added to"
                                : "removed from"),
                        groupName),
                authoriseBuilder
                        .withOutcome(Outcome.builder()
                                .withSuccess(wasSuccessful)
                                .withDescription(description)
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
