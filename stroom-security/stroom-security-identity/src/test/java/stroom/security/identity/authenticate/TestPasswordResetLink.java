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

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TestPasswordResetLink {

    private static final String USER_ID = "jbloggs@example.com";

    @Test
    void issueThenParseRecoversTheUserAndMatchesTheStoredHash() {
        final PasswordResetLink.Issued issued = PasswordResetLink.issue(USER_ID);

        final PasswordResetLink.Parsed parsed = PasswordResetLink.parse(issued.token()).orElseThrow();
        assertThat(parsed.userId()).isEqualTo(USER_ID);
        // The hash parsed from the presented token equals the hash stored at issue, so the link verifies.
        assertThat(parsed.tokenHash()).isEqualTo(issued.tokenHash());
    }

    @Test
    void theStoredValueIsAHashNotTheTokenItself() {
        final PasswordResetLink.Issued issued = PasswordResetLink.issue(USER_ID);

        assertThat(issued.tokenHash()).isNotEqualTo(issued.token());
        // The token contains the secret; the stored hash must not, or a database read would leak it.
        assertThat(issued.token()).doesNotContain(issued.tokenHash());
    }

    @Test
    void everyLinkIsUnique() {
        final PasswordResetLink.Issued a = PasswordResetLink.issue(USER_ID);
        final PasswordResetLink.Issued b = PasswordResetLink.issue(USER_ID);

        assertThat(a.token()).isNotEqualTo(b.token());
        assertThat(a.tokenHash()).isNotEqualTo(b.tokenHash());
    }

    @Test
    void tamperingWithTheSecretIsNotDetectedAsTheSameLink() {
        final PasswordResetLink.Issued issued = PasswordResetLink.issue(USER_ID);
        final String token = issued.token();

        // Flip the last character of the secret.
        final char last = token.charAt(token.length() - 1);
        final char swapped = last == 'A'
                ? 'B'
                : 'A';
        final String tampered = token.substring(0, token.length() - 1) + swapped;

        final PasswordResetLink.Parsed parsed = PasswordResetLink.parse(tampered).orElseThrow();
        assertThat(parsed.userId()).isEqualTo(USER_ID);
        assertThat(parsed.tokenHash()).isNotEqualTo(issued.tokenHash());
    }

    @Test
    void malformedTokensAreRejected() {
        assertThat(PasswordResetLink.parse(null)).isEmpty();
        assertThat(PasswordResetLink.parse("")).isEmpty();
        assertThat(PasswordResetLink.parse("no-separator")).isEmpty();
        assertThat(PasswordResetLink.parse(".secret-only")).isEmpty();
        assertThat(PasswordResetLink.parse("user-only.")).isEmpty();
    }

    @Test
    void anInvalidBase64UserPartIsRejected() {
        // A '*' is outside the base64url alphabet, so the user id cannot be decoded.
        final Optional<PasswordResetLink.Parsed> parsed = PasswordResetLink.parse("****.somesecret");
        assertThat(parsed).isEmpty();
    }
}
