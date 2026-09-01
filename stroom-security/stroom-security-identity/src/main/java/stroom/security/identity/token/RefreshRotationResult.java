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

/**
 * The outcome of attempting to redeem (rotate) a refresh token.
 *
 * @param outcome            What happened.
 * @param consumed           The row that was consumed, present only for {@link Outcome#ROTATED}. Carries the
 *                           subject, client, scope and auth time needed to mint the successor's JWTs.
 * @param familyRevokedCount How many tokens were revoked as a consequence. Non-zero only for
 *                           {@link Outcome#REPLAYED}, where the whole rotation family is killed.
 */
public record RefreshRotationResult(Outcome outcome,
                                    OAuthToken consumed,
                                    int familyRevokedCount) {

    public boolean isRotated() {
        return outcome == Outcome.ROTATED;
    }

    public static RefreshRotationResult rotated(final OAuthToken consumed) {
        return new RefreshRotationResult(Outcome.ROTATED, consumed, 0);
    }

    public static RefreshRotationResult replayed(final int familyRevokedCount) {
        return new RefreshRotationResult(Outcome.REPLAYED, null, familyRevokedCount);
    }

    public static RefreshRotationResult of(final Outcome outcome) {
        return new RefreshRotationResult(outcome, null, 0);
    }


// --------------------------------------------------------------------------------


    public enum Outcome {
        /**
         * The token was active and has been consumed; a successor has been issued in the same transaction.
         */
        ROTATED,
        /**
         * No row matched the presented token. Either it was never issued, or it expired and has since been
         * purged. Indistinguishable by design, and treated the same way.
         */
        UNKNOWN,
        /**
         * The token was issued but its lifetime has passed.
         */
        EXPIRED,
        /**
         * The token was explicitly revoked - by an admin, or as collateral of an earlier replay in its
         * family.
         */
        REVOKED,
        /**
         * The token had already been redeemed. This is the signature of a stolen token being replayed, so
         * the entire rotation family is revoked and re-authentication is forced.
         */
        REPLAYED,
        /**
         * Two concurrent redemptions raced and this one lost. The other issued the successor; this caller
         * must not get one too.
         */
        LOST_RACE
    }
}
