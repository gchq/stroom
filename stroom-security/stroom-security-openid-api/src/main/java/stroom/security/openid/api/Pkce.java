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

package stroom.security.openid.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * The PKCE (RFC 7636) S256 transform, shared so that the party creating a {@code code_verifier} and the
 * party verifying a {@code code_challenge} compute it identically.
 */
public final class Pkce {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    // RFC 7636 section 7.1 recommends a 32-byte random value; base64url-encoded that is a 43-character
    // verifier (256 bits of entropy, which also happens to be the spec's minimum length).
    private static final int VERIFIER_BYTE_LENGTH = 32;

    private Pkce() {
    }

    /**
     * A fresh {@code code_verifier}: a high-entropy string of unreserved characters, per RFC 7636.
     */
    public static String createCodeVerifier() {
        final byte[] bytes = new byte[VERIFIER_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(bytes);
        return encode(bytes);
    }

    /**
     * The S256 {@code code_challenge} for a verifier: {@code base64url(sha256(verifier))}, no padding.
     */
    public static String createS256Challenge(final String codeVerifier) {
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return encode(digest);
        } catch (final NoSuchAlgorithmException e) {
            // SHA-256 is required to be present on every JVM.
            throw new IllegalStateException(e);
        }
    }

    private static String encode(final byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
