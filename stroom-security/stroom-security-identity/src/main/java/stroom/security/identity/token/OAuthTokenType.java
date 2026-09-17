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
 * The kind of token an {@link OAuthToken} row records.
 * <p>
 * This is the discriminator held in <code>oauth_token.token_type</code>. It is deliberately unrelated to
 * the legacy <code>token_type</code> table (and its generated {@code TokenType} class), which is a dead
 * lookup table left over from the pre-7.2 token implementation.
 * </p>
 */
public enum OAuthTokenType {

    /**
     * A JWT access token. Identified by its {@code jti}; carries no {@code token_hash}.
     */
    ACCESS,
    /**
     * A JWT id token. Identified by its {@code jti}; carries no {@code token_hash}.
     */
    ID,
    /**
     * An opaque refresh token. These are random strings rather than JWTs, so they have no {@code jti} and
     * are identified by the SHA-256 {@code token_hash} of the presented value.
     */
    REFRESH;

    /**
     * @return true if tokens of this type are JWTs, and so are keyed on {@code jti} rather than
     * {@code token_hash}.
     */
    public boolean isJwt() {
        return this != REFRESH;
    }
}
