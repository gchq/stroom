package stroom.security.common.impl;

import jakarta.servlet.http.HttpServletRequest;
import org.jose4j.jwt.consumer.JwtContext;

import java.util.Map;
import java.util.Optional;

public interface JwtContextFactory {

    boolean hasToken(HttpServletRequest request);

    void removeAuthorisationEntries(final Map<String, String> headers);

    Map<String, String> createAuthorisationEntries(final String accessToken);

    Optional<JwtContext> getJwtContext(final HttpServletRequest request);

    /**
     * Extract the {@link JwtContext} from the passed JSON web token.
     * Will verify the jwt using the public keys and also check claims
     * like audience and subject.
     */
    Optional<JwtContext> getJwtContext(final String jwt);

    /**
     * Extract the {@link JwtContext} from the passed JSON web token.
     * Will verify the jwt using the public keys and also check claims
     * like audience and subject only if doVerification is true.
     */
    Optional<JwtContext> getJwtContext(final String jwt, final boolean doVerification);

}
