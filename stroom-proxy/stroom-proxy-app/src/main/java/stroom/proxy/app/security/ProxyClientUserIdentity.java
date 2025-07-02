package stroom.proxy.app.security;

import stroom.security.api.UserIdentity;

import org.jose4j.jwt.consumer.JwtContext;

import java.util.Objects;
import java.util.Optional;

/**
 * Identity for a client sending data to stroom-proxy
 */
public class ProxyClientUserIdentity implements UserIdentity {

    private final String id;
    private final String displayName;
    private final String fullName;
    // debatable whether it is worth holding this or not
    private final JwtContext jwtContext;

    public ProxyClientUserIdentity(final String id,
                                   final String displayName,
                                   final String fullName,
                                   final JwtContext jwtContext) {
        this.id = id;
        this.displayName = Objects.requireNonNullElse(displayName, id);
        this.fullName = fullName;
        this.jwtContext = jwtContext;
    }

    @Override
    public String subjectId() {
        return id;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public Optional<String> getFullName() {
        return Optional.ofNullable(fullName);
    }

    public JwtContext getJwtContext() {
        return jwtContext;
    }

    @Override
    public String toString() {
        return "ProxyUserIdentity{" +
               "id='" + id + '\'' +
               ", displayName='" + displayName + '\'' +
               ", fullName='" + fullName + '\'' +
               '}';
    }
}
