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

package stroom.security.identity.token;

import java.util.Objects;

/**
 * One token minted by the internal IdP, as recorded in <code>oauth_token</code>.
 * <p>
 * This is a <em>reference</em>, never bearer material. JWTs are recorded by their {@code jti}, which is all
 * that is needed to enumerate and revoke them. Refresh tokens are recorded by the SHA-256 hash of the opaque
 * value, because the redeemable credential is looked up by presentation and must not be stored in the clear.
 * </p>
 *
 * @param id         Surrogate key. Null until the row has been created.
 * @param jti        The JWT id for {@link OAuthTokenType#isJwt()} types, otherwise null.
 * @param tokenHash  SHA-256 of the opaque value for {@link OAuthTokenType#REFRESH}, otherwise null.
 * @param subjectId  The subject the token was issued to. A subject string rather than an account id, since
 *                   service and external subjects have no <code>account</code> row.
 * @param familyId   Rotation lineage for refresh tokens, grant id for JWTs. Lets reuse of one refresh token
 *                   revoke every descendant in the same lineage.
 * @param scope      The scope granted at authentication, carried forward onto each successor so a refresh
 *                   can mint equivalent tokens without re-consulting the original grant.
 * @param authTimeMs When the end user actually authenticated, so a refreshed id token reports the original
 *                   login time rather than the time of the refresh. Null for tokens with no authentication
 *                   behind them.
 * @param consumedMs When a refresh token was redeemed, or null if it has not been. A consumed-but-unexpired
 *                   row is what makes a replay detectable, so these rows are kept until expiry rather than
 *                   deleted on redemption.
 * @param revokedMs  When it was revoked, or null if it has not been.
 * @param revokedBy  Who revoked it, or null if it has not been revoked.
 */
public record OAuthToken(Integer id,
                         OAuthTokenType tokenType,
                         String jti,
                         String tokenHash,
                         String subjectId,
                         String clientId,
                         String familyId,
                         String scope,
                         Long authTimeMs,
                         long issuedMs,
                         long expiresMs,
                         Long consumedMs,
                         boolean revoked,
                         Long revokedMs,
                         String revokedBy) {

    public OAuthToken {
        Objects.requireNonNull(tokenType, "tokenType required");
        Objects.requireNonNull(subjectId, "subjectId required");
        // Exactly one natural key per type - the table has a unique index on each, and the redemption and
        // revocation lookups depend on the right one being present. Enforced here so a bad row cannot reach
        // the DAO and fail later with an opaque constraint violation.
        if (tokenType.isJwt()) {
            if (jti == null || jti.isBlank()) {
                throw new IllegalArgumentException("jti required for token type " + tokenType);
            }
            if (tokenHash != null) {
                throw new IllegalArgumentException("tokenHash must be null for token type " + tokenType);
            }
        } else {
            if (tokenHash == null || tokenHash.isBlank()) {
                throw new IllegalArgumentException("tokenHash required for token type " + tokenType);
            }
            if (jti != null) {
                throw new IllegalArgumentException("jti must be null for token type " + tokenType);
            }
        }
    }

    /**
     * A newly minted JWT (access or id token), not yet persisted.
     */
    public static OAuthToken newJwt(final OAuthTokenType tokenType,
                                    final String jti,
                                    final String subjectId,
                                    final String clientId,
                                    final String familyId,
                                    final String scope,
                                    final Long authTimeMs,
                                    final long issuedMs,
                                    final long expiresMs) {
        if (!tokenType.isJwt()) {
            throw new IllegalArgumentException("Not a JWT token type: " + tokenType);
        }
        return new OAuthToken(
                null, tokenType, jti, null, subjectId, clientId, familyId, scope, authTimeMs,
                issuedMs, expiresMs, null, false, null, null);
    }

    /**
     * A newly issued refresh token, not yet persisted.
     *
     * @param tokenHash SHA-256 of the opaque token value, never the value itself.
     */
    public static OAuthToken newRefresh(final String tokenHash,
                                        final String subjectId,
                                        final String clientId,
                                        final String familyId,
                                        final String scope,
                                        final Long authTimeMs,
                                        final long issuedMs,
                                        final long expiresMs) {
        return new OAuthToken(
                null, OAuthTokenType.REFRESH, null, tokenHash, subjectId, clientId, familyId, scope,
                authTimeMs, issuedMs, expiresMs, null, false, null, null);
    }

    /**
     * @return true if this refresh token has already been redeemed. Always false for JWTs, which are not
     * redeemed.
     */
    public boolean isConsumed() {
        return consumedMs != null;
    }

    /**
     * @return true if this token is still within its lifetime at the given time. Callers should not rely on
     * the purge job having run; every read filters on expiry so an unswept row is never honoured.
     */
    public boolean isLiveAt(final long nowMs) {
        return expiresMs > nowMs;
    }

    /**
     * @return true if this token should still be honoured at the given time. A consumed refresh token is
     * not usable - presenting one again is a replay, not a redemption.
     */
    public boolean isUsableAt(final long nowMs) {
        return !revoked && !isConsumed() && isLiveAt(nowMs);
    }
}
