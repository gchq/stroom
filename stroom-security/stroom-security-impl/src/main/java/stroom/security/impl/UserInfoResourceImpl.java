package stroom.security.impl;

import stroom.event.logging.rs.api.AutoLogged;
import stroom.security.shared.UserInfoResource;
import stroom.security.user.api.UserInfoLookup;
import stroom.util.shared.UserInfo;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

@AutoLogged
public class UserInfoResourceImpl implements UserInfoResource {

    private final Provider<UserInfoLookup> userInfoLookupProvider;

    @SuppressWarnings("unused")
    @Inject
    public UserInfoResourceImpl(final Provider<UserInfoLookup> userInfoLookupProvider) {
        this.userInfoLookupProvider = userInfoLookupProvider;
    }

    @Override
    public UserInfo fetch(final String userUuid) {
        return userInfoLookupProvider.get().getByUuid(userUuid)
                .orElse(null);
    }
}
