package stroom.security.common.impl;

import stroom.util.authentication.HasExpiry;
import stroom.util.exception.ThrowingFunction;
import stroom.util.shared.NullSafe;

import org.jose4j.jwt.JwtClaims;

import java.time.Instant;
import java.util.Optional;

public interface HasJwtClaims extends HasExpiry {

    JwtClaims getJwtClaims();

    default Optional<String> getClaimValue(final String claim) {
        return NullSafe.getAsOptional(
                getJwtClaims(),
                ThrowingFunction.unchecked(jwtClaims ->
                        jwtClaims.getClaimValue(claim, String.class)));
    }

    default <T> Optional<T> getClaimValue(final String claim, final Class<T> clazz) {
        return NullSafe.getAsOptional(
                getJwtClaims(),
                ThrowingFunction.unchecked(jwtClaims ->
                        jwtClaims.getClaimValue(claim, clazz)));
    }

    default Instant getExpireTime() {
        return NullSafe.getOrElse(
                getJwtClaims(),
                ThrowingFunction.unchecked(JwtClaims::getExpirationTime),
                numericDate -> Instant.ofEpochMilli(numericDate.getValueInMillis()),
                Instant.MAX);
    }
}
