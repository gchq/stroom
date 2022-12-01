package stroom.proxy.app.security;

import stroom.security.api.UserIdentity;
import stroom.security.api.exception.AuthenticationException;
import stroom.security.common.impl.JwtUtil;
import stroom.security.openid.api.OpenId;

import org.jose4j.jwt.consumer.JwtContext;

import java.util.Optional;

/**
 * Identity for a client sending data to stroom-proxy
 */
public class ProxyClientUserIdentity implements UserIdentity {

    private final String id;
    private final String preferredUsername;
    private final String fullName;
    // debatable whether it is worth holding this or not
    private final JwtContext jwtContext;

    public ProxyClientUserIdentity(final JwtContext jwtContext) {
        this.id = JwtUtil.getClaimValue(jwtContext, OpenId.CLAIM__SUBJECT)
                .orElseThrow(() -> new AuthenticationException(
                        "Missing " + OpenId.CLAIM__SUBJECT));
        this.preferredUsername = JwtUtil.getClaimValue(jwtContext, OpenId.CLAIM__PREFERRED_USERNAME)
                .orElse(id);
        this.fullName = JwtUtil.getClaimValue(jwtContext, OpenId.CLAIM__NAME)
                .orElse(null);
        this.jwtContext = jwtContext;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getPreferredUsername() {
        return preferredUsername;
    }

    @Override
    public Optional<String> getFullName() {
        return Optional.ofNullable(fullName);
    }

    @Override
    public String toString() {
        return "ProxyUserIdentity{" +
                "id='" + id + '\'' +
                ", preferredUsername='" + preferredUsername + '\'' +
                ", fullName='" + fullName + '\'' +
                '}';
    }
}
