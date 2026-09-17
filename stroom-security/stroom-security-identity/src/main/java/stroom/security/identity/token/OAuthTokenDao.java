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

package stroom.security.identity.token;

import stroom.security.openid.api.TokenSummary;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The internal IdP's inventory of the tokens it has minted, so that they can be listed and revoked before
 * they expire.
 * <p>
 * <b>Every read filters on expiry.</b> Correctness never depends on the purge job having run: an expired row
 * is treated as absent even if it is still physically present. Callers pass {@code nowMs} rather than the DAO
 * reading the clock, so behaviour around expiry boundaries is testable.
 * </p>
 * <p>
 * This is OP-side (issuer) state only. Tokens minted by an external IdP never appear here - revoking those is
 * the external IdP's business, and Stroom-as-RP deals with them by dropping sessions instead.
 * </p>
 */
public interface OAuthTokenDao {

    /**
     * Record a newly minted token.
     *
     * @return the persisted token, with its generated id.
     */
    OAuthToken create(OAuthToken oAuthToken);

    /**
     * Redeem a refresh token and issue its successor, <b>in one transaction</b>.
     * <p>
     * This is the security-critical operation of the whole table. It replaces an in-memory
     * take-and-remove with a durable compare-and-set, which is what makes the following true:
     * </p>
     * <ul>
     *     <li><b>Exactly one winner.</b> Consuming is a conditional update on {@code consumed_ms IS NULL},
     *     so two concurrent redemptions of the same token cannot both issue a successor - the loser gets
     *     {@link RefreshRotationResult.Outcome#LOST_RACE}.</li>
     *     <li><b>Replay stays detectable for the token's whole life.</b> Consumed rows are kept until they
     *     expire, so presenting a spent token is recognised as a replay for the full refresh lifetime, and
     *     across restarts - not merely for as long as a cache entry happened to survive.</li>
     *     <li><b>No gap between consume and issue.</b> Both happen in the same transaction, so a failure
     *     cannot leave a caller with a spent token and no successor.</li>
     * </ul>
     *
     * @param presentedTokenHash SHA-256 of the token the client presented.
     * @param successorTokenHash SHA-256 of the successor to issue if redemption succeeds.
     * @param successorExpiresMs When the successor should expire.
     * @param nowMs              The current time, used for expiry and for stamping the consumption.
     * @param replayRevokedBy    Recorded as {@code revoked_by} on the family if this turns out to be a
     *                           replay.
     */
    RefreshRotationResult rotateRefreshToken(String presentedTokenHash,
                                             String successorTokenHash,
                                             long successorExpiresMs,
                                             long nowMs,
                                             String replayRevokedBy);

    /**
     * Look up a JWT by its {@code jti}, ignoring expired rows.
     */
    Optional<OAuthToken> fetchByJti(String jti, long nowMs);

    /**
     * Look up a refresh token by the SHA-256 hash of the presented value, ignoring expired rows.
     */
    Optional<OAuthToken> fetchByTokenHash(String tokenHash, long nowMs);

    /**
     * All unexpired tokens for one subject, newest first. Includes revoked-but-unexpired rows so an admin can
     * see that a revoke took effect.
     */
    List<OAuthToken> findBySubjectId(String subjectId, long nowMs);

    /**
     * The {@code jti}s of all revoked-but-unexpired JWTs. This is what seeds each node's revoked-jti cache,
     * which the verify path consults instead of reading the database per request.
     * <p>
     * Bounded by design: it holds only tokens that are both revoked <em>and</em> still live, so it empties
     * itself as they expire.
     * </p>
     */
    Set<String> fetchRevokedJtis(long nowMs);

    /**
     * Summarise usable tokens per subject, for the admin list. Revoked, expired and spent rows are excluded,
     * since the question is how much live access a subject currently has.
     *
     * @return subject id to summary, for every subject holding at least one usable token.
     */
    Map<String, TokenSummary> summariseUsableBySubject(long nowMs);

    /**
     * Revoke every unexpired token for a subject.
     *
     * @return the number of rows revoked. Already-revoked rows are not counted again.
     */
    int revokeBySubjectId(String subjectId, String revokedBy, long nowMs);

    /**
     * Revoke the whole grant that the token with this {@code jti} belongs to.
     * <p>
     * Resolves the token's family without filtering on expiry, because the access token naming the grant may
     * well have expired while the refresh token that matters is still live.
     * </p>
     *
     * @return the number of rows revoked, or zero if no token has that {@code jti}.
     */
    int revokeGrantByJti(String jti, String revokedBy, long nowMs);

    /**
     * Revoke a whole rotation lineage - used when a refresh token is replayed, where the safe response is to
     * kill every descendant of the compromised grant rather than just the token presented.
     *
     * @return the number of rows revoked.
     */
    int revokeByFamilyId(String familyId, String revokedBy, long nowMs);

    /**
     * Revoke one JWT.
     *
     * @return true if an unexpired, not-already-revoked row was revoked.
     */
    boolean revokeByJti(String jti, String revokedBy, long nowMs);

    /**
     * Delete rows that expired before the given time. Housekeeping only - reads already ignore expired rows,
     * so this reclaims space rather than enforcing anything.
     *
     * @return the number of rows deleted.
     */
    int deleteExpired(long nowMs);
}
