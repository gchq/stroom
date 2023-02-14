package stroom.security.impl;

import stroom.security.shared.FindUserNameCriteria;
import stroom.util.shared.UserName;
import stroom.security.shared.UserNameProvider;
import stroom.util.shared.ResultPage;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;

public class UserNameService {

    private final Set<UserNameProvider> userNameProviders;

    @Inject
    public UserNameService(final Set<UserNameProvider> userNameProviders) {
        this.userNameProviders = userNameProviders;
    }

    public ResultPage<UserName> find(final FindUserNameCriteria criteria) {
        final Set<UserName> names = new HashSet<>();
        for (final UserNameProvider userNameProvider : userNameProviders) {
            final ResultPage<UserName> resultPage = userNameProvider.findUserNames(criteria);
            names.addAll(resultPage.getValues());
        }
        final List<UserName> list = names.stream()
                .sorted()
                .collect(Collectors.toList());

        return new ResultPage<>(list);
    }
}
