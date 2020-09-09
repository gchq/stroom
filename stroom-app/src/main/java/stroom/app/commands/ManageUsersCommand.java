package stroom.app.commands;

import stroom.config.app.Config;
import stroom.security.api.SecurityContext;
import stroom.security.impl.UserAppPermissionService;
import stroom.security.impl.UserService;
import stroom.security.shared.User;
import stroom.util.logging.LogUtil;

import com.google.inject.Injector;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Creates an account in the internal identity provider
 */
public class ManageUsersCommand extends AbstractStroomAccountConfiguredCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(ManageUsersCommand.class);

    private static final String COMMAND_NAME = "manage_users";
    private static final String COMMAND_DESCRIPTION = "Create users/groups and manage application permissions";

    private static final String CREATE_USER_ARG_NAME = "createUser";
    private static final String CREATE_GROUP_ARG_NAME = "createGroup";
    private static final String ADD_TO_GROUP_ARG_NAME = "addToGroup";
    private static final String REMOVE_FROM_GROUP_ARG_NAME = "removeFromGroup";
    private static final String GRANT_PERMISSION_ARG_NAME = "grantPermission";
    private static final String REVOKE_PERMISSION_ARG_NAME = "revokePermission";
    private static final String LIST_PERMISSIONS_ARG_NAME = "listPermissions";

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
                .help("The id of the user to create");

        subparser.addArgument(asArg(CREATE_GROUP_ARG_NAME))
                .dest(CREATE_GROUP_ARG_NAME)
                .action(Arguments.append())
                .type(String.class)
                .nargs(1)
                .metavar(GROUP_META_VAR)
                .help("The id of the group to create");

        subparser.addArgument(asArg(ADD_TO_GROUP_ARG_NAME))
                .dest(ADD_TO_GROUP_ARG_NAME)
                .action(Arguments.append())
                .type(String.class)
                .nargs(2)
                .required(false)
                .metavar(USER_OR_GROUP_META_VAR, TARGET_GROUP_META_VAR)
                .help("The name of the user/group being added and the group to add it to");

        subparser.addArgument(asArg(REMOVE_FROM_GROUP_ARG_NAME))
                .dest(REMOVE_FROM_GROUP_ARG_NAME)
                .action(Arguments.append())
                .type(String.class)
                .nargs(2)
                .required(false)
                .metavar(USER_OR_GROUP_META_VAR, TARGET_GROUP_META_VAR)
                .help("The name of the user/group being removed and the group it is being removed from");

        subparser.addArgument(asArg(GRANT_PERMISSION_ARG_NAME))
                .dest(GRANT_PERMISSION_ARG_NAME)
                .action(Arguments.append())
                .type(String.class)
                .nargs(2)
                .metavar(USER_OR_GROUP_META_VAR, PERMISSION_NAME_META_VAR)
                .help("The name of the user/group and the name of the application permission to " +
                        "grant to it.");

        subparser.addArgument(asArg(REVOKE_PERMISSION_ARG_NAME))
                .dest(REVOKE_PERMISSION_ARG_NAME)
                .action(Arguments.append())
                .type(String.class)
                .nargs(2)
                .metavar(USER_OR_GROUP_META_VAR, PERMISSION_NAME_META_VAR)
                .help("The name of the user/group and the name of the application permission to " +
                        "revoke from it.");

        subparser.addArgument(asArg(LIST_PERMISSIONS_ARG_NAME))
                .dest(LIST_PERMISSIONS_ARG_NAME)
                .action(Arguments.storeTrue())
                .help("List the valid permission names");
    }

    @Override
    protected void runCommand(final Bootstrap<Config> bootstrap,
                              final Namespace namespace,
                              final Config config,
                              final Injector injector) {

        LOGGER.debug("Namespace {}", namespace);

        injector.injectMembers(this);

        try {
            securityContext.asProcessingUser(() -> {
                // Order is important here
                createUsers(namespace);
                createGroups(namespace);
                addToGroups(namespace);
                removeFromGroups(namespace);
                grantPermissions(namespace);
                revokePermissions(namespace);
                listPermissions(namespace);
            });
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
            System.exit(1);
        }

        LOGGER.info("Completed successfully");
        System.exit(0);
    }

    private void listPermissions(final Namespace namespace) {
        if (namespace.getBoolean(LIST_PERMISSIONS_ARG_NAME)) {
            String perms = userAppPermissionService.getAllPermissionNames()
                    .stream()
                    .sorted()
                    .collect(Collectors.joining("\n"));
            LOGGER.info("Valid application permission names:{}", perms);
        }
    }

    private void createUsers(final Namespace namespace) {
        final List<String> users = extractStrings(namespace, CREATE_USER_ARG_NAME);
        users.forEach(userId ->
                createUserOrGroup(userId, false));
    }


    private void createGroups(final Namespace namespace) {
        final List<String> groups = extractStrings(namespace, CREATE_GROUP_ARG_NAME);
        groups.forEach(groupId ->
                createUserOrGroup(groupId, true));
    }

    private void createUserOrGroup(final String name, final boolean isGroup) {
        final String msg = LogUtil.message("Creating {} {}", (isGroup ? "group" : "user"), name);
        try {
            LOGGER.info(msg);

            if (isGroup) {
                userService.createUserGroup(name);
            } else {
                userService.createUser(name);
            }
        } catch (Exception e) {
            LOGGER.debug("Error", e);
            throw new RuntimeException("Error " + msg + ":" + e.getMessage());
        }
    }

    private void addToGroups(final Namespace namespace) {
        try {
            final List<GroupArgs> groupArgsList = extractGroupArgs(namespace, ADD_TO_GROUP_ARG_NAME);
            groupArgsList.forEach(groupArgs -> {
                LOGGER.info("Adding {} to group {}", groupArgs.userOrGroupId, groupArgs.targetGroupId);

                userService.getUserByName(groupArgs.userOrGroupId)
                        .ifPresentOrElse(
                                userOrGroup -> {
                                    User targetGroup = userService.getUserByName(groupArgs.targetGroupId)
                                            .orElseThrow(() ->
                                                    new RuntimeException("Target group " +
                                                            groupArgs.targetGroupId +
                                                            " doesn't exist"));
                                    userService.addUserToGroup(userOrGroup.getUuid(), targetGroup.getUuid());
                                },
                                () -> {
                                    throw new RuntimeException("User/Group " +
                                            groupArgs.userOrGroupId +
                                            " does not exist");
                                });
            });
        } catch (Exception e) {
            LOGGER.debug("Error", e);
            throw new RuntimeException(LogUtil.message("Error adding user/group to group: ", e.getMessage()));
        }
    }

    private void removeFromGroups(final Namespace namespace) {
        try {
            final List<GroupArgs> groupArgsList = extractGroupArgs(namespace, REMOVE_FROM_GROUP_ARG_NAME);
            groupArgsList.forEach(groupArgs -> {
                LOGGER.info("Removing {} from group {}", groupArgs.userOrGroupId, groupArgs.targetGroupId);
                userService.getUserByName(groupArgs.userOrGroupId)
                        .ifPresentOrElse(
                                userOrGroup -> {
                                    User targetGroup = userService.getUserByName(groupArgs.targetGroupId)
                                            .orElseThrow(() ->
                                                    new RuntimeException("Target group " +
                                                            groupArgs.targetGroupId +
                                                            " doesn't exist"));
                                    userService.removeUserFromGroup(userOrGroup.getUuid(), targetGroup.getUuid());
                                },
                                () -> {
                                    throw new RuntimeException("User/Group " +
                                            groupArgs.userOrGroupId +
                                            " does not exist");
                                });
            });
        } catch (Exception e) {
            LOGGER.debug("Error", e);
            throw new RuntimeException(LogUtil.message("Error removing user/group from group: ", e.getMessage()));
        }
    }

    private void grantPermissions(final Namespace namespace) {
        try {
            final List<PermissionArgs> permissionArgsList = extractPermissionArgs(namespace, GRANT_PERMISSION_ARG_NAME);
            permissionArgsList.forEach(permissionArgs-> {
                LOGGER.info("Granting application permission '{}' to {}",
                        permissionArgs.permissionName, permissionArgs.userOrGroupId);

                final User userOrGroup = userService.getUserByName(permissionArgs.userOrGroupId)
                        .orElseThrow(() -> new RuntimeException(LogUtil.message("User/group {} does not exist",
                                permissionArgs.userOrGroupId)));

                userAppPermissionService.addPermission(userOrGroup.getUuid(), permissionArgs.permissionName);
            });
        } catch (Exception e) {
            LOGGER.debug("Error", e);
            throw new RuntimeException(LogUtil.message("Error creating user {}: ", e.getMessage()));
        }
    }

    private void revokePermissions(final Namespace namespace) {
        final List<PermissionArgs> permissionArgsList = extractPermissionArgs(namespace, REVOKE_PERMISSION_ARG_NAME);
        permissionArgsList.forEach(permissionArgs-> {
            LOGGER.info("Revoking application permission from '{}' to {}",
                    permissionArgs.permissionName, permissionArgs.userOrGroupId);

            final User userOrGroup = userService.getUserByName(permissionArgs.userOrGroupId)
                    .orElseThrow(() -> new RuntimeException(LogUtil.message("User/group {} does not exist",
                            permissionArgs.userOrGroupId)));

            userAppPermissionService.removePermission(userOrGroup.getUuid(), permissionArgs.permissionName);

        });
    }

    private List<String> extractStrings(final Namespace namespace, final String dest) {
        final String[][] values = namespace.get(dest);
        return Arrays.stream(values)
                .map(arr -> arr[0])
                .collect(Collectors.toList());
    }

    private List<GroupArgs> extractGroupArgs(final Namespace namespace, final String dest) {
        final String[][] values = namespace.get(dest);
        return Arrays.stream(values)
                .map(arr -> new GroupArgs(arr[0], arr[1]))
                .collect(Collectors.toList());
    }

    private List<PermissionArgs> extractPermissionArgs(final Namespace namespace, final String dest) {
        final String[][] values = namespace.get(dest);
        return Arrays.stream(values)
                .map(arr -> new PermissionArgs(arr[0], arr[1]))
                .collect(Collectors.toList());
    }

    private String asArg(final String name) {
        return "--" + name;
    }

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
}
