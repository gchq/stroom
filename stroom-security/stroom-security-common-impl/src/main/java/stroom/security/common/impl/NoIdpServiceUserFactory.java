package stroom.security.common.impl;

import stroom.security.api.ServiceUserFactory;
import stroom.security.api.UserIdentity;

public class NoIdpServiceUserFactory implements ServiceUserFactory {

    private static final UserIdentity NO_IDP_SERVICE_USER_IDENTITY = new UserIdentity() {
        @Override
        public String subjectId() {
            return "NO_IDP SERVICE USER";
        }
    };

    @Override
    public UserIdentity createServiceUserIdentity() {
        return NO_IDP_SERVICE_USER_IDENTITY;
    }

    @Override
    public boolean isServiceUser(final UserIdentity userIdentity,
                                 final UserIdentity serviceUserIdentity) {
        if (userIdentity == null || serviceUserIdentity == null) {
            return false;
        } else {
            // Instance equality as there is only one service user identity
            return userIdentity == serviceUserIdentity;
        }
    }
}
