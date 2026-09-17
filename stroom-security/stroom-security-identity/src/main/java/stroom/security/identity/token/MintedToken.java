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
 * A freshly minted JWT, together with the parts of it a caller needs without re-parsing the token.
 *
 * @param token     The serialised JWT to hand to the client.
 * @param jti       The token's unique id ({@code jti} claim). Needed to record the token in the inventory so
 *                  it can later be revoked - the whole reason {@link TokenBuilder#build()} returns a record
 *                  rather than a bare string.
 * @param expiresMs The {@code exp} claim as epoch millis. Note that {@code exp} has **second** granularity,
 *                  so this is always a whole number of seconds.
 */
public record MintedToken(String token, String jti, long expiresMs) {

    /**
     * The expiry to record on an inventory row for this token.
     * <p>
     * This is deliberately one second later than {@link #expiresMs}. JWT {@code exp} is compared in whole
     * seconds, so a token is still accepted throughout the second in which it expires - it dies at
     * {@code exp + 1s}, not at {@code exp}. Since the revoked-jti denylist only retains entries for
     * <em>unexpired</em> tokens, a row expiring at exactly {@code exp} would drop out of the denylist up to a
     * second before the token stopped verifying, leaving a revoked token briefly honoured again.
     * </p>
     */
    public long inventoryExpiresMs() {
        return expiresMs + 1000L;
    }
}
