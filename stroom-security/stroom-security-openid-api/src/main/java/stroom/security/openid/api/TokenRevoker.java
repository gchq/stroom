/*
 * Copyright 2026 Crown Copyright
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
 * Revokes tokens the internal IdP has minted, before their natural expiry.
 * <p>
 * The companion to {@link RevokedTokenChecker}: that answers the verify path, this is how a revocation gets
 * made in the first place. Declared here for the same reason - the admin action lives in
 * {@code stroom-security-impl} (which owns users, permissions and sessions) while the tokens belong to
 * {@code stroom-security-identity}, and the two modules have no dependency on each other.
 * </p>
 * <p>
 * Calls flow one way only, from the RP into the OP. Nothing here reaches back into sessions or user state -
 * the caller is responsible for its own half of a revocation.
 * </p>
 */
public interface TokenRevoker {

    /**
     * Revoke every unexpired token the internal IdP has issued to a subject, including their refresh token
     * families, and propagate the revocation across the cluster.
     * <p>
     * A no-op when the internal IdP is not in use: nothing has been minted, so there is nothing to revoke.
     * </p>
     *
     * @param subjectId The subject whose tokens should be revoked.
     * @return the number of tokens revoked.
     */
    int revokeForSubject(String subjectId);

    /**
     * Revoke every token issued by the same grant as the token with the given {@code jti} - the access, id and
     * refresh tokens that were minted together and rotate as one lineage.
     * <p>
     * Called when the sole holder of a grant's tokens goes away. Tokens issued to a browser session live only in
     * that session on the server, so once it is destroyed - by logout or by expiry - nobody can present them
     * again; leaving the rows usable would overstate how much live access a subject has, and would leave a
     * refresh token redeemable for weeks after the only legitimate copy of it ceased to exist.
     * </p>
     * <p>
     * A no-op when the {@code jti} matches nothing, which is the normal case for a token minted by an external
     * IdP.
     * </p>
     *
     * @return the number of tokens revoked.
     */
    int revokeGrant(String jti);
}
