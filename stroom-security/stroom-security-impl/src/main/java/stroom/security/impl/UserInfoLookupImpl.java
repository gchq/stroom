package stroom.security.impl;

import stroom.security.user.api.UserInfoLookup;
import stroom.util.shared.UserInfo;

import jakarta.inject.Inject;

import java.util.Optional;

public class UserInfoLookupImpl implements UserInfoLookup {

    private final UserInfoCache userInfoCache;

    @Inject
    public UserInfoLookupImpl(final UserInfoCache userInfoCache) {
        this.userInfoCache = userInfoCache;
    }

    @Override
    public Optional<UserInfo> getByUuid(final String userUuid) {
        return userInfoCache.getByUuid(userUuid);
    }
}
