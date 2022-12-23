package stroom.security.common.impl;

import stroom.security.api.HasJwt;
import stroom.security.api.UserIdentity;

import java.util.Objects;

public class DefaultOpenIdCredsUserIdentity implements UserIdentity, HasJwt {

    private final String id;
    private final String jwt;

    public DefaultOpenIdCredsUserIdentity(final String id, final String jwt) {
        this.id = id;
        this.jwt = jwt;
    }

    @Override
    public String getJwt() {
        return jwt;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultOpenIdCredsUserIdentity that = (DefaultOpenIdCredsUserIdentity) o;
        return Objects.equals(id, that.id) && Objects.equals(jwt, that.jwt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, jwt);
    }

    @Override
    public String toString() {
        return "DefaultOpenIdCredsUserIdentity{" +
                "id='" + id + '\'' +
                ", jwt='" + jwt + '\'' +
                '}';
    }
}
