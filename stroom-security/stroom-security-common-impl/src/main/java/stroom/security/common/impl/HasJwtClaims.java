/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
