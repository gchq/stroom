/*
 * Copyright 2020 Crown Copyright
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

import stroom.security.identity.shared.SigningKeyRow;

import org.jose4j.jwk.PublicJsonWebKey;

import java.time.Duration;
import java.util.List;

/**
 * The signing keys of the internal identity provider, held in the {@code json_web_key} table.
 * <p>
 * A key is in exactly one of four states, expressed in the {@code enabled} and
 * {@code expires_on_ms} columns:
 * </p>
 * <table>
 *     <caption>Key states</caption>
 *     <tr><th>State</th><th>enabled</th><th>expires_on_ms</th><th>Sign with</th><th>Publish</th></tr>
 *     <tr><td>Active</td><td>true</td><td>null</td><td>yes, exactly one</td><td>yes</td></tr>
 *     <tr><td>Retired</td><td>true</td><td>future</td><td>no</td><td>yes</td></tr>
 *     <tr><td>Expired</td><td>true</td><td>past</td><td>no</td><td>no, deletable</td></tr>
 *     <tr><td>Revoked</td><td>false</td><td>any</td><td>no</td><td>no</td></tr>
 * </table>
 * <p>
 * A key acquires its expiry when it is <em>retired</em>, not when it is created. Rotation can
 * drift -- a node may be down, a job may not run -- and an expiry fixed at creation would either
 * delete a key that is still the only one, or keep one long after it stopped being useful.
 * </p>
 * <p>
 * {@code enabled = false} is a revocation switch for taking a key out of both signing and
 * publication immediately, should one ever need to be withdrawn.
 * </p>
 */
public interface JwkDao {

    /**
     * Every key that must be published, i.e. the active key plus any retired key that has not yet
     * expired. Revoked and expired keys are excluded.
     * <p>
     * Creates an active key if there is nothing publishable, so callers can rely on this being
     * non-empty.
     * </p>
     *
     * @return The publishable keys, newest first.
     */
    List<PublicJsonWebKey> listPublishable();

    /**
     * The single key that new tokens must be signed with, creating one if there is no active key.
     * <p>
     * Creation is lockless: if two nodes both find no active key and each create one, the surplus is
     * harmless and {@link #rotate} reconciles it. This always returns the newest active key, so all
     * nodes sign consistently even in that transient state.
     * </p>
     *
     * @return The active key, never null.
     */
    PublicJsonWebKey getActiveKey();

    /**
     * Rotate the signing key if the active one has reached {@code rotationInterval}, and delete any
     * key that has passed its expiry. Also retires any surplus active key left by a lockless
     * creation race.
     * <p>
     * Retirement is a compare-and-swap, so this is safe to run concurrently on more than one node
     * without a lock: only the caller that moves a key out of the active state creates its
     * replacement.
     * </p>
     *
     * @param rotationInterval How old the active key must be before it is replaced.
     * @param retention        How long a retired key stays published. Must be at least the longest
     *                         lifetime of any token that could have been signed with it, or tokens
     *                         will stop verifying before they expire.
     * @return What changed, for logging.
     */
    JwkRotationSummary rotate(Duration rotationInterval, Duration retention);

    /**
     * Every key the store holds, newest first, described only by what it is doing and when - never by what
     * it is. Unlike {@link #listPublishable} this includes keys already revoked or expired, because the
     * point of listing them is to decide about them.
     * <p>
     * The stored key is written with its private material included, so this deliberately returns rows rather
     * than keys: nothing on this path reads the key material out of the database at all.
     * </p>
     */
    List<SigningKeyRow> list();

    /**
     * Withdraw one key, so that anything signed with it stops being accepted.
     * <p>
     * If the key was the one signing new tokens, a replacement is created in the same transaction. Leaving
     * that to the next caller would let the store sit with nothing to sign with, and would invite two nodes
     * to each create one.
     * </p>
     *
     * @param id        The row to withdraw.
     * @param auditUser Who is withdrawing it, for the row's audit columns. Passed in because the data
     *                  access layer has no notion of who is asking.
     * @return 1 if the key was withdrawn, 0 if it was already revoked or is not there.
     */
    int revoke(int id, String auditUser);

    /**
     * Withdraw every key still trusted, and create a replacement. For the usual compromise, where which key
     * was exposed is not known.
     *
     * @param auditUser Who is withdrawing them, for the rows' audit columns.
     * @return how many keys were withdrawn.
     */
    int revokeAll(String auditUser);
}
