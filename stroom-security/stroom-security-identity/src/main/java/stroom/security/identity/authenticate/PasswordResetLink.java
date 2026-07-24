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

package stroom.security.identity.authenticate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * The opaque token emailed to a user so they can reset a forgotten password. It is a random secret with
 * no internal structure and is never a JWT, so it can never be mistaken for, or used as, a credential to
 * authenticate API requests.
 * <p>
 * The token is {@code base64url(userId).secret}. Only a hash of the secret is stored against the account,
 * so the raw token cannot be recovered from the database, and possession of the token is proved by
 * presenting a secret that hashes to the stored value. The account also identifies the user, so the reset
 * needs nothing but the token itself.
 * </p>
 */
final class PasswordResetLink {

    private static final String SEPARATOR = ".";
    private static final int SECRET_BYTE_LENGTH = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private PasswordResetLink() {
    }

    /**
     * The token to email and the value to persist against the account.
     *
     * @param token     the opaque string to put in the reset link.
     * @param tokenHash the hash of the secret, to store against the account and match on redemption.
     */
    record Issued(String token, String tokenHash) {

    }

    /**
     * The account and secret hash recovered from a presented token.
     *
     * @param userId    the account the token was issued for.
     * @param tokenHash the hash of the presented secret, to compare with the stored value.
     */
    record Parsed(String userId, String tokenHash) {

    }

    static Issued issue(final String userId) {
        final byte[] bytes = new byte[SECRET_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(bytes);
        final String secret = encode(bytes);
        final String token = encode(userId.getBytes(StandardCharsets.UTF_8)) + SEPARATOR + secret;
        return new Issued(token, hash(secret));
    }

    static Optional<Parsed> parse(final String token) {
        if (token == null) {
            return Optional.empty();
        }
        final int separatorIndex = token.indexOf(SEPARATOR);
        if (separatorIndex <= 0 || separatorIndex == token.length() - 1) {
            return Optional.empty();
        }
        final String encodedUserId = token.substring(0, separatorIndex);
        final String secret = token.substring(separatorIndex + 1);
        try {
            final String userId = new String(
                    Base64.getUrlDecoder().decode(encodedUserId), StandardCharsets.UTF_8);
            return Optional.of(new Parsed(userId, hash(secret)));
        } catch (final IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String encode(final byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(final String secret) {
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            return encode(digest);
        } catch (final NoSuchAlgorithmException e) {
            // SHA-256 is required to be present on every JVM.
            throw new IllegalStateException(e);
        }
    }
}
