package stroom.security.impl;

import stroom.security.api.TokenException;
import stroom.security.api.TokenVerifier;

import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.NumericDate;
import org.jose4j.jwt.consumer.JwtContext;

import javax.inject.Inject;
import java.time.Instant;
import java.util.List;

class TokenVerifierImpl implements TokenVerifier {
    private final JwtContextFactory jwtContextFactory;

    @Inject
    TokenVerifierImpl(final JwtContextFactory jwtContextFactory) {
        this.jwtContextFactory = jwtContextFactory;
    }

    @Override
    public void verifyToken(final String token, final String clientId) throws TokenException {
        // Will throw if invalid, e.g. if it doesn't match our public key
        final JwtContext jwtContext = jwtContextFactory.getJwtContext(token)
                .orElseThrow(() -> new TokenException("Could not extract claims from token"));

        // TODO : Check against blacklist to see if token has been revoked. Blacklist
        //  is a list of JWI (JWT IDs) on auth service. Only tokens with `jwi` claims are API
        //  keys so only those tokens need checking against the blacklist cache.

        try {
            final JwtClaims jwtClaims = jwtContext.getJwtClaims();
            if (jwtClaims.getExpirationTime() != null
                    && jwtClaims.getExpirationTime().isBefore(NumericDate.now())) {
                throw new TokenException("Token expired on: " +
                        Instant.ofEpochSecond(jwtClaims.getExpirationTime().getValueInMillis()).toString());
            }

            final List<String> audience = jwtClaims.getAudience();
            if (!audience.contains(clientId)) {
                throw new TokenException("Token audience does not contain clientId: " + clientId);
            }
        } catch (MalformedClaimException e) {
            throw new TokenException("Invalid token claims: " + e.getMessage(), e);
        }
    }
}
