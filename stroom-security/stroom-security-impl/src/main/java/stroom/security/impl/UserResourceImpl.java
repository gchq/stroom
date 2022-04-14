package stroom.security.impl;

import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.security.api.SecurityContext;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.User;
import stroom.security.shared.UserResource;
import stroom.util.shared.ResultPage;

import event.logging.CreateEventAction;
import event.logging.Outcome;

import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.NotFoundException;

@AutoLogged
public class UserResourceImpl implements UserResource {

    private final Provider<UserService> userServiceProvider;
    private final Provider<SecurityContext> securityContextProvider;
    private final Provider<AuthorisationEventLog> authorisationEventLogProvider;
    private final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider;

    @Inject
    public UserResourceImpl(final Provider<UserService> userServiceProvider,
                            final Provider<SecurityContext> securityContextProvider,
                            final Provider<AuthorisationEventLog> authorisationEventLogProvider,
                            final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider) {
        this.userServiceProvider = userServiceProvider;
        this.securityContextProvider = securityContextProvider;
        this.authorisationEventLogProvider = authorisationEventLogProvider;
        this.stroomEventLoggingServiceProvider = stroomEventLoggingServiceProvider;
    }

    @Override
    public ResultPage<User> find(final FindUserCriteria criteria) {
        // Apply default sort
        if (criteria.getSortList() == null || criteria.getSortList().isEmpty()) {
            criteria.setSort(FindUserCriteria.FIELD_NAME);
        }

        if (criteria.getRelatedUser() != null) {
            final User userRef = criteria.getRelatedUser();
            List<User> list;
            if (userRef.isGroup()) {
                list = userServiceProvider.get().findUsersInGroup(userRef.getUuid(), criteria.getQuickFilterInput());
            } else {
                list = userServiceProvider.get().findGroupsForUser(userRef.getUuid(), criteria.getQuickFilterInput());
            }

            // Create a result list limited by the page request.
            return ResultPage.createPageLimitedList(list, criteria.getPageRequest());
        }

        return ResultPage.createUnboundedList(userServiceProvider.get().find(criteria));
    }

    @Override
    public List<User> find(final String name,
                           final Boolean isGroup,
                           final String uuid) {

        // TODO @AT Doesn't appear to be used by java code, may be used by new react screens
        //   that are currently unused.
        // TODO @AT Not clear what this method is trying to do. uuid is not used, is name a filter
        //   input or an exact match.

        final FindUserCriteria criteria = new FindUserCriteria();
        criteria.setQuickFilterInput(name);
        criteria.setGroup(isGroup);
        return userServiceProvider.get().find(criteria);
    }

    @Override
    public User fetch(String userUuid) {
        return userServiceProvider.get().loadByUuid(userUuid)
                .orElseThrow(() -> new NotFoundException("User " + userUuid + " does not exist"));
    }

    @Override
    @AutoLogged(OperationType.MANUALLY_LOGGED)
    public User create(final String name,
                       final Boolean isGroup) {
        User user;

        if (isGroup) {
            user = createGroup(name);
        } else {
            user = createUser(name);
        }

        return user;
    }

    private User createUser(final String name) {
        final CreateEventAction.Builder<Void> builder = CreateEventAction.builder();

        try {
            // Need to do the logging as a lambda so we only log if the creation actually happens
            User newUser = userServiceProvider.get()
                    .getOrCreateUser(name, user -> {

                        builder.withObjects(
                                event.logging.User.builder()
                                        .withId(user.getName())
                                        .build());

                        stroomEventLoggingServiceProvider.get().log("UserResourceImpl.createUser",
                                "Creating new Stroom user " + name, builder.build());
                    });

            return newUser;
        } catch (Exception ex) {
            builder.withObjects(
                    event.logging.User.builder()
                            .withId(name)
                            .build());
            builder.withOutcome(Outcome.builder()
                    .withSuccess(false)
                    .withDescription(ex.getMessage())
                    .build());

            stroomEventLoggingServiceProvider.get().log("UserResourceImpl.createUser",
                    "Creating new Stroom user " + name, builder.build());

            throw ex;
        }
    }

    private User createGroup(final String name) {
        final CreateEventAction.Builder<Void> builder = CreateEventAction.builder();

        try {
            // Need to do the logging as a lambda so we only log if the creation actually happens
            final User newUserGroup = userServiceProvider.get()
                    .getOrCreateUserGroup(name, userGroup -> {
                        builder.withObjects(
                                event.logging.Group.builder()
                                        .withName(name)
                                        .withId(userGroup.getUuid())
                                        .build());
                        stroomEventLoggingServiceProvider.get().log("UserResourceImpl.createGroup",
                                "Creating new user group " + name, builder.build());
                    });

            return newUserGroup;
        } catch (Exception ex) {
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
    public Boolean delete(final String uuid) {
        return userServiceProvider.get().delete(uuid);
    }

    @Override
    @AutoLogged(OperationType.MANUALLY_LOGGED)
    public Boolean addUserToGroup(final String userUuid,
                                  final String groupUuid) {
        String userIdForLogging = getUserNameForLogging(userUuid);
        String groupIdForLogging = getGroupNameForLogging(groupUuid);

        boolean success = false;
        String errorMessage = null;

        try {
            Boolean result = userServiceProvider.get().addUserToGroup(userUuid, groupUuid);

            if (result != null) {
                success = result;
            }
        } catch (Exception e) {
            errorMessage = e.getMessage();
        }

        authorisationEventLogProvider.get().addUserToGroup(userIdForLogging, groupIdForLogging, success, errorMessage);

        return success;
    }

    @Override
    @AutoLogged(OperationType.MANUALLY_LOGGED)
    public Boolean removeUserFromGroup(final String userUuid,
                                       final String groupUuid) {
        String userIdForLogging = getUserNameForLogging(userUuid);
        String groupIdForLogging = getGroupNameForLogging(groupUuid);

        boolean success = false;
        String errorMessage = null;

        try {
            Boolean result = userServiceProvider.get().removeUserFromGroup(userUuid, groupUuid);

            if (result != null) {
                success = result;
            }
        } catch (Exception e) {
            errorMessage = e.getMessage();
        }

        authorisationEventLogProvider.get().removeUserFromGroup(userIdForLogging, groupIdForLogging,
                success, errorMessage);

        return success;
    }

    @Override
    public List<String> getAssociates(final String filter) {
        return userServiceProvider.get().getAssociates(filter);
    }

    private String getUserNameForLogging(final String uuid) {
        try {
            Optional<User> found = securityContextProvider.get()
                    .asProcessingUserResult(() -> userServiceProvider.get().loadByUuid(uuid));
            if (found.isPresent() && !found.get().isGroup()) {
                return found.get().getName();
            }
        } catch (Exception ex) {
            //Ignore at this time
        }
        return uuid;
    }

    private String getGroupNameForLogging(final String uuid) {
        try {
            Optional<User> found = securityContextProvider.get()
                    .asProcessingUserResult(() -> userServiceProvider.get().loadByUuid(uuid));
            if (found.isPresent() && found.get().isGroup()) {
                return found.get().getName();
            }
        } catch (Exception ex) {
            //Ignore at this time
        }
        return uuid;
    }
}
