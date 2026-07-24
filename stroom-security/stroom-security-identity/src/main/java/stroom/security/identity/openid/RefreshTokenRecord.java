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

package stroom.security.identity.openid;

/**
 * The server-side state behind an opaque refresh token. The refresh token itself is a random string that
 * carries no claims; everything needed to mint the next set of tokens is held here and looked up when the
 * token is redeemed.
 *
 * @param clientId            the client the tokens were issued to.
 * @param subject             the end-user the tokens represent.
 * @param scope               the scope granted at authentication, carried forward on each refresh.
 * @param authTimeEpochSecond when the end-user actually authenticated, so a refreshed id token reports the
 *                            original login time rather than the time of the refresh.
 * @param familyId            identifies the rotation lineage. Every refresh token descended from a single
 *                            login shares it, so that redeeming an already-redeemed token can revoke the
 *                            whole family.
 * @param expiryTimeEpochMs   when this refresh token stops being redeemable.
 */
record RefreshTokenRecord(
        String clientId,
        String subject,
        String scope,
        long authTimeEpochSecond,
        String familyId,
        long expiryTimeEpochMs) {

    boolean isExpired(final long nowEpochMs) {
        return nowEpochMs > expiryTimeEpochMs;
    }
}
