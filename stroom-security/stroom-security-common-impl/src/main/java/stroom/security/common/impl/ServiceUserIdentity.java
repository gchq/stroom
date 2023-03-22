package stroom.security.common.impl;

import stroom.security.api.UserIdentity;
import stroom.security.openid.api.OpenId;

import java.util.Objects;
import java.util.Optional;

/**
 * User identity for a service user for this application to authenticate with other
 * applications on the same IDP realm. I.e. Stroom's processing user.
 * This user uses the client credentials flow.
 */
public class ServiceUserIdentity implements UserIdentity, HasJwtClaims, HasUpdatableToken {

    private final String id;
    private final UpdatableToken updatableToken;

    public ServiceUserIdentity(final String id,
                               final UpdatableToken updatableToken) {
        this.id = Objects.requireNonNull(id);
        this.updatableToken = Objects.requireNonNull(updatableToken);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getDisplayName() {
        return Optional.ofNullable(getJwtClaims())
                .flatMap(claims -> JwtUtil.getClaimValue(claims, OpenId.CLAIM__PREFERRED_USERNAME))
                .orElse(id);
    }

    @Override
    public UpdatableToken getUpdatableToken() {
        return updatableToken;
    }

    @Override
    public String toString() {
        return "ServiceUserIdentity{" +
                "id='" + id + '\'' +
                ", updatableToken=" + updatableToken +
                '}';
    }
}
