package stroom.security.impl;

import stroom.security.api.SecurityContext;
import stroom.security.shared.FindUserNameCriteria;
import stroom.security.shared.PermissionNames;
import stroom.security.shared.UserNameProvider;
import stroom.security.user.api.UserNameService;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserName;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;

public class UserNameServiceImpl implements UserNameService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(UserNameServiceImpl.class);

    private final Set<UserNameProvider> userNameProviders;
    private final SecurityContext securityContext;

    @Inject
    public UserNameServiceImpl(final Set<UserNameProvider> userNameProviders,
                               final SecurityContext securityContext) {
        // The reason we have multiple providers is that when using the internal IDP, users are created
        // as accounts first, so the user perms screens need to be able to see both stroom_users that have
        // been associated with an account and accounts yet to be associated.

        // With an external IDP we only have one provider giving us stroom_users that have been created by
        // a user logging in or that has been explicitly created in the user perms screens.
        this.userNameProviders = userNameProviders.stream()
                .filter(UserNameProvider::isEnabled)
                .collect(Collectors.toSet());
        LOGGER.debug("Initialising with userNameProviders: {}", this.userNameProviders);
        this.securityContext = securityContext;
    }

    @Override
    public ResultPage<UserName> find(final FindUserNameCriteria criteria) {
        // Can't allow any user to list all users on the system
        checkPermission();
        // usernames with a uuid take priority
        final List<UserName> userNames = userNameProviders.stream()
                .flatMap(provider ->
                        provider.findUserNames(criteria).stream()
                                .map(userName -> new PrioritisedUserName(
                                        provider.getPriority(),
                                        userName)))
                .collect(Collectors.groupingBy(
                        userName -> userName.userName.getSubjectId(),
                        Collectors.reducing((userName1, userName2) -> {
                            // Lower == higher priority
                            if (userName1.priority < userName2.priority
                                    || userName1.priority == userName2.priority) {
                                return userName1;
                            } else {
                                return userName2;
                            }
                        })))
                .values()
                .stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(PrioritisedUserName::userName)
                .collect(Collectors.toList());

        return new ResultPage<>(userNames);
    }

    @Override
    public Optional<UserName> getBySubjectId(final String subjectId) {
        return userNameProviders.stream()
                .map(provider ->
                        provider.getBySubjectId(subjectId))
                .filter(Optional::isPresent)
                .findAny()
                .flatMap(optOptUserName -> optOptUserName);
    }

    @Override
    public Optional<UserName> getByDisplayName(final String displayName) {
        return userNameProviders.stream()
                .map(provider ->
                        provider.getByDisplayName(displayName))
                .filter(Optional::isPresent)
                .findAny()
                .flatMap(optOptUserName -> optOptUserName);
    }

    @Override
    public Optional<UserName> getByUuid(final String userUuid) {
        return userNameProviders.stream()
                .map(provider ->
                        provider.getByUuid(userUuid))
                .filter(Optional::isPresent)
                .findAny()
                .flatMap(optOptUserName -> optOptUserName);
    }

    private void checkPermission() {
        if (!securityContext.hasAppPermission(PermissionNames.MANAGE_USERS_PERMISSION)) {
            throw new PermissionException(
                    securityContext.getUserIdentityForAudit(), "You do not have permission to manage users");
        }
    }


    // --------------------------------------------------------------------------------


    private record PrioritisedUserName(
            int priority,
            UserName userName) {

    }
}
