/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.security.openid.api;

/**
 * Tests whether a token minted by the internal IdP has been revoked before its natural expiry.
 * <p>
 * This is the seam between the two halves of token management, which live in Gradle modules with no dependency
 * on each other: tokens are minted and revoked by {@code stroom-security-identity} (the OP), but verified by
 * {@code stroom-security-impl} (the RP). Declaring the interface here - in a module with no project
 * dependencies of its own - lets the verify path consult revocation state without knowing anything about the
 * IdP's schema, exactly as {@link PublicJsonWebKeyProvider} already does for signing keys.
 * </p>
 * <p>
 * Implementations <b>must not</b> read the database per call. This sits on the hot path of every authenticated
 * request, so the answer has to come from memory; durability lives in the table behind the cache, and staleness
 * is bounded by the cache's own expiry rather than by consulting the database.
 * </p>
 */
public interface RevokedTokenChecker {

    /**
     * @param jti The {@code jti} claim of a token whose signature has <b>already</b> been verified. Callers
     *            must not use this as a substitute for verification - an unverified id proves nothing.
     * @return true if the token has been revoked and must be refused. A null or unknown {@code jti} is not
     * revoked: revocation is a denylist, so anything absent from it is honoured.
     */
    boolean isRevoked(String jti);
}
