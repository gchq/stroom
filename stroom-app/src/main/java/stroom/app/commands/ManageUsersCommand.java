package stroom.app.commands;

import stroom.config.app.Config;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.security.api.SecurityContext;
import stroom.security.impl.UserAppPermissionService;
import stroom.security.impl.UserService;
import stroom.security.shared.User;
import stroom.util.logging.LogUtil;

import com.google.inject.Injector;
import event.logging.Event;
import event.logging.Event.EventDetail.Authorise;
import event.logging.Event.EventDetail.Authorise.AddGroups;
import event.logging.Event.EventDetail.Authorise.RemoveGroups;
import event.logging.Group;
import event.logging.ObjectFactory;
import event.logging.ObjectOutcome;
import event.logging.Outcome;
import event.logging.UserDetails;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Creates an account in the internal identity provider
 *
 * e.g manage_users ../local.yml --createUser admin --createGroup Administrators --addToGroup admin Administrators --grantPermission Administrators Administrator
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

        LOGGER.info("Manage Users completed successfully");
        System.exit(0);
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
        users.forEach(userId ->
                createUserOrGroup(userId, false));
    }


    private void createGroups(final Namespace namespace) {
        final List<String> groups = extractStrings(namespace, CREATE_GROUP_ARG_NAME);
        groups.forEach(groupId ->
                createUserOrGroup(groupId, true));
    }

    private void createUserOrGroup(final String name, final boolean isGroup) {
        final String msg = LogUtil.message("Creating {} '{}'", (isGroup ? "group" : "user"), name);
        LOGGER.info(msg);

        try {
            userService.getUserByName(name)
                    .ifPresentOrElse(
                            user -> {
                                final String outcomeMsg = LogUtil.message("{} '{}' already exists",
                                        (isGroup ? "Group" : "User"), name);
                                LOGGER.warn(outcomeMsg);
                                logCreateUserOrGroupEvent(name, false, outcomeMsg, isGroup);
                            },
                            () -> {
                                if (isGroup) {
                                    userService.createUserGroup(name);
                                } else {
                                    userService.createUser(name);
                                }
                                logCreateUserOrGroupEvent(name, true, null, isGroup);
                            });
        } catch (Exception e) {
            LOGGER.debug("Error", e);
            throw new RuntimeException("Error " + msg + ":" + e.getMessage());
        }
    }

    private void addToGroups(final Namespace namespace) {
        final List<GroupArgs> groupArgsList = extractGroupArgs(namespace, ADD_TO_GROUP_ARG_NAME);
        groupArgsList.forEach(groupArgs -> {
            final String msg = LogUtil.message("Adding '{}' to group '{}'",
                    groupArgs.userOrGroupId, groupArgs.targetGroupId);

            LOGGER.info(msg);

            try {
                userService.getUserByName(groupArgs.userOrGroupId)
                        .ifPresentOrElse(
                                userOrGroup -> {
                                    User targetGroup = userService.getUserByName(groupArgs.targetGroupId)
                                            .orElseThrow(() ->
                                                    new RuntimeException("Target group " +
                                                            groupArgs.targetGroupId +
                                                            " doesn't exist"));
                                    userService.addUserToGroup(userOrGroup.getUuid(), targetGroup.getUuid());
                                    logAddOrRemoveFromGroupEvent(
                                            groupArgs.userOrGroupId,
                                            groupArgs.targetGroupId,
                                            true,
                                            null,
                                            true);
                                },
                                () -> {
                                    throw new RuntimeException("User/Group " +
                                            groupArgs.userOrGroupId +
                                            " does not exist");
                                });
            } catch (Exception e) {
                LOGGER.debug("Error", e);
                logAddOrRemoveFromGroupEvent(
                        groupArgs.userOrGroupId,
                        groupArgs.targetGroupId,
                        false,
                        e.getMessage(),
                        true);
                throw new RuntimeException("Error " + msg + ":" + e.getMessage());
            }
        });
    }

    private void removeFromGroups(final Namespace namespace) {
        final List<GroupArgs> groupArgsList = extractGroupArgs(namespace, REMOVE_FROM_GROUP_ARG_NAME);
        groupArgsList.forEach(groupArgs -> {
            final String msg = LogUtil.message("Removing '{}' from group '{}'",
                    groupArgs.userOrGroupId, groupArgs.targetGroupId);

            LOGGER.info(msg);

            try {
                userService.getUserByName(groupArgs.userOrGroupId)
                        .ifPresentOrElse(
                                userOrGroup -> {
                                    final User targetGroup = userService.getUserByName(groupArgs.targetGroupId)
                                            .orElseThrow(() ->
                                                    new RuntimeException("Target group '" +
                                                            groupArgs.targetGroupId +
                                                            "' doesn't exist"));
                                    userService.removeUserFromGroup(userOrGroup.getUuid(), targetGroup.getUuid());
                                    logAddOrRemoveFromGroupEvent(
                                            groupArgs.userOrGroupId,
                                            groupArgs.targetGroupId,
                                            true,
                                            null,
                                            false);
                                },
                                () -> {
                                    throw new RuntimeException("User/Group '" +
                                            groupArgs.userOrGroupId +
                                            "' does not exist");
                                });
            } catch (Exception e) {
                LOGGER.debug("Error", e);
                logAddOrRemoveFromGroupEvent(
                        groupArgs.userOrGroupId,
                        groupArgs.targetGroupId,
                        false,
                        null,
                        false);
                throw new RuntimeException("Error " + msg + ":" + e.getMessage());
            }
        });
    }

    private void grantPermissions(final Namespace namespace) {
        final List<PermissionArgs> permissionArgsList = extractPermissionArgs(namespace, GRANT_PERMISSION_ARG_NAME);
        permissionArgsList.forEach(permissionArgs-> {
            final String msg = LogUtil.message("Granting application permission '{}' to '{}'",
                    permissionArgs.permissionName, permissionArgs.userOrGroupId);

            LOGGER.info(msg);

            try {

                final User userOrGroup = userService.getUserByName(permissionArgs.userOrGroupId)
                        .orElseThrow(() -> new RuntimeException(LogUtil.message("User/group '{}' does not exist",
                                permissionArgs.userOrGroupId)));

                if (hasPermission(permissionArgs)) {
                    final String failMsg = LogUtil.message("User/Group '{}' already has permission '{}'",
                            permissionArgs.userOrGroupId, permissionArgs.permissionName);
                    LOGGER.warn(failMsg);

                    logAddOrRemoveFromGroupEvent(
                            permissionArgs.userOrGroupId,
                            permissionArgs.permissionName,
                            false,
                            failMsg,
                            true);
                } else {
                    userAppPermissionService.addPermission(
                            userOrGroup.getUuid(),
                            permissionArgs.permissionName);

                    logAddOrRemoveFromGroupEvent(
                            permissionArgs.userOrGroupId,
                            permissionArgs.permissionName,
                            true,
                            null,
                            true);
                }
            } catch (Exception e) {
                LOGGER.debug("Error", e);
                logAddOrRemoveFromGroupEvent(
                        permissionArgs.userOrGroupId,
                        permissionArgs.permissionName,
                        false,
                        e.getMessage(),
                        true);
                throw new RuntimeException("Error " + msg + ":" + e.getMessage());
            }
        });
    }

    private void revokePermissions(final Namespace namespace) {
        final List<PermissionArgs> permissionArgsList = extractPermissionArgs(
                namespace,
                REVOKE_PERMISSION_ARG_NAME);

        permissionArgsList.forEach(permissionArgs-> {
            final String msg = LogUtil.message("Revoking application permission from '{}' to '{}'",
                    permissionArgs.permissionName, permissionArgs.userOrGroupId);

            LOGGER.info(msg);

            final User userOrGroup = userService.getUserByName(permissionArgs.userOrGroupId)
                    .orElseThrow(() -> new RuntimeException(LogUtil.message("User/group '{}' does not exist",
                            permissionArgs.userOrGroupId)));

            try {
                if (hasPermission(permissionArgs)) {
                    userAppPermissionService.removePermission(
                            userOrGroup.getUuid(),
                            permissionArgs.permissionName);

                    logAddOrRemoveFromGroupEvent(
                            permissionArgs.userOrGroupId,
                            permissionArgs.permissionName,
                            true,
                            null,
                            false);
                } else {
                    final String failMsg = LogUtil.message("User/Group '{}' does not have permission '{}'",
                            permissionArgs.userOrGroupId, permissionArgs.permissionName);
                    LOGGER.warn(failMsg);

                    logAddOrRemoveFromGroupEvent(
                            permissionArgs.userOrGroupId,
                            permissionArgs.permissionName,
                            false,
                            failMsg,
                            false);
                }
            } catch (Exception e) {
                LOGGER.debug("Error", e);
                logAddOrRemoveFromGroupEvent(
                        permissionArgs.userOrGroupId,
                        permissionArgs.permissionName,
                        false,
                        e.getMessage(),
                        false);
                throw new RuntimeException("Error " + msg + ":" + e.getMessage());
            }
        });
    }

    private boolean hasPermission(final PermissionArgs permissionArgs) {
        String userOrGroupUuid = userService.getUserByName(permissionArgs.userOrGroupId)
                .map(User::getUuid)
                .orElseThrow(() -> new RuntimeException(LogUtil.message(
                        "User/Group '{}' not found", permissionArgs.userOrGroupId)));
        return userAppPermissionService.getPermissionNamesForUser(userOrGroupUuid)
                .stream()
                .anyMatch(perm -> perm.equals(permissionArgs.permissionName));
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

    private void logCreateUserOrGroupEvent(final String username,
                                           final boolean wasSuccessful,
                                           final String description,
                                           final boolean isGroup) {

        final Event event = buildBasicEvent(
                "CliCreateStroom" + (isGroup ? "Group" : "User"),
                LogUtil.message("A Stroom user account for {} {} was created",
                        (isGroup ? "group" : "user"), username));

        final ObjectFactory objectFactory = new ObjectFactory();
        final ObjectOutcome createAction = objectFactory.createObjectOutcome();
        event.getEventDetail().setCreate(createAction);

        if (isGroup) {
            final event.logging.Group group = objectFactory.createGroup();
            createAction.getObjects().add(group);
            group.setName(username);
        } else {
            final event.logging.User user = objectFactory.createUser();
            createAction.getObjects().add(user);
            user.setName(username);

            // TODO @AT UserDetails appears to be empty for some reason so can't set the user's name on it
            final UserDetails userDetails = objectFactory.createUserDetails();
            user.setUserDetails(userDetails);
        }

        final Outcome outcome = objectFactory.createOutcome();
        createAction.setOutcome(outcome);
        outcome.setSuccess(wasSuccessful);
        outcome.setDescription(description);

        stroomEventLoggingService.log(event);
    }

    private void logAddOrRemoveFromGroupEvent(final String username,
                                              final String groupName,
                                              final boolean wasSuccessful,
                                              final String description,
                                              final boolean isAddingGroup) {

        final Event event = buildBasicEvent(
                "CliAddToGroup",
                LogUtil.message("User/Group {} was {} to group {}",
                        username, (isAddingGroup ? "added to" : "removed from"), groupName));

        final ObjectFactory objectFactory = new ObjectFactory();
        final Authorise authorise = objectFactory.createEventEventDetailAuthorise();
        event.getEventDetail().setAuthorise(authorise);

        Group group = objectFactory.createGroup();
        group.setId(groupName);
        group.setName(groupName);

        if (isAddingGroup) {
            final AddGroups addGroups = objectFactory.createEventEventDetailAuthoriseAddGroups();
            authorise.setAddGroups(addGroups);
            addGroups.getGroup().add(group);
        } else {
            final RemoveGroups removeGroups = objectFactory.createEventEventDetailAuthoriseRemoveGroups();
            authorise.setRemoveGroups(removeGroups);
            removeGroups.getGroup().add(group);
        }

        final Outcome outcome = objectFactory.createOutcome();
        authorise.setOutcome(outcome);
        outcome.setSuccess(wasSuccessful);
        outcome.setDescription(description);

        stroomEventLoggingService.log(event);
    }

    private Event buildBasicEvent(final String typeId, final String description) {
        final Event event = stroomEventLoggingService.createAction(typeId, description);

        // We are running as proc user so try and get the OS user, though that may just be a shared account
        event.getEventSource().getUser().setId(System.getProperty("user.name"));
        return event;
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
