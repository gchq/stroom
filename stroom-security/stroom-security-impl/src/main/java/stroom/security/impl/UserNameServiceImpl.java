package stroom.security.impl;

import stroom.security.shared.FindUserNameCriteria;
import stroom.security.shared.UserNameProvider;
import stroom.security.user.api.UserNameService;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserName;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;

public class UserNameServiceImpl implements UserNameService {

    private final Set<UserNameProvider> userNameProviders;

    @Inject
    public UserNameServiceImpl(final Set<UserNameProvider> userNameProviders) {
        this.userNameProviders = userNameProviders;
    }

    @Override
    public ResultPage<UserName> find(final FindUserNameCriteria criteria) {
        final List<UserName> userNames = userNameProviders.stream()
                .flatMap(provider ->
                        provider.findUserNames(criteria).stream())
                .toList();
        return new ResultPage<>(userNames);
    }

    @Override
    public Optional<UserName> getUserName(final String userId) {
        return userNameProviders.stream()
                .map(provider ->
                        provider.getUserName(userId))
                .filter(Optional::isPresent)
                .findAny()
                .flatMap(optOptUserName -> optOptUserName);
    }
}
