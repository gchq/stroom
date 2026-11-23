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

package stroom.credentials.impl;

import stroom.credentials.shared.Credential;
import stroom.credentials.shared.CredentialType;
import stroom.util.time.StroomDuration;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestCredentialExpiryChecker {

    private static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");

    private CredentialsConfig credentialsConfig = new CredentialsConfig();
    private Instant now = NOW;
    private final CredentialExpiryChecker checker = new CredentialExpiryChecker(
            () -> credentialsConfig,
            () -> now);

    @Test
    void testNullCredentialIsIgnored() {
        assertThat(checker.check(null))
                .isFalse();
    }

    @Test
    void testCredentialWithNoExpiryIsUsedWithoutWarning() {
        credentialsConfig = createConfig(true, StroomDuration.ofDays(14));

        assertThat(checker.check(createCredential("a", null)))
                .isFalse();
    }

    @Test
    void testCredentialExpiringLaterIsUsedWithoutWarning() {
        assertThat(checker.check(createCredential("a", NOW.plus(Duration.ofDays(15)))))
                .isFalse();
    }

    @Test
    void testCredentialExpiringSoonIsUsedWithWarning() {
        assertThat(checker.check(createCredential("a", NOW.plus(Duration.ofDays(13)))))
                .isTrue();
    }

    @Test
    void testCredentialExpiringSoonIsNotRefused() {
        credentialsConfig = createConfig(true, null);

        assertThat(checker.check(createCredential("a", NOW.plus(Duration.ofMinutes(1)))))
                .isTrue();
    }

    @Test
    void testZeroWarningPeriodOnlyWarnsOnceExpired() {
        credentialsConfig = createConfig(false, StroomDuration.ZERO);

        assertThat(checker.check(createCredential("a", NOW.plus(Duration.ofMinutes(1)))))
                .isFalse();
        assertThat(checker.check(createCredential("b", NOW.minus(Duration.ofMinutes(1)))))
                .isTrue();
    }

    @Test
    void testExpiredCredentialIsUsedWithWarningByDefault() {
        assertThat(checker.check(createCredential("a", NOW.minus(Duration.ofDays(1)))))
                .isTrue();
    }

    @Test
    void testExpiredCredentialIsRefusedWhenConfigured() {
        credentialsConfig = createConfig(true, null);

        assertThatThrownBy(() -> checker.check(createCredential("a", NOW.minus(Duration.ofDays(1)))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Credential 'a' expired at 2026-11-18T12:00:00Z");
    }

    @Test
    void testCredentialExpiringNowIsExpired() {
        credentialsConfig = createConfig(true, null);

        assertThatThrownBy(() -> checker.check(createCredential("a", NOW)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testWarningIsLoggedAtMostOnceAnHourForEachCredential() {
        final Credential credentialA = createCredential("a", NOW.minus(Duration.ofDays(1)));
        final Credential credentialB = createCredential("b", NOW.minus(Duration.ofDays(1)));

        assertThat(checker.check(credentialA))
                .isTrue();
        assertThat(checker.check(credentialA))
                .isFalse();
        // Each credential has its own interval
        assertThat(checker.check(credentialB))
                .isTrue();

        now = NOW.plus(CredentialExpiryChecker.WARNING_INTERVAL).minusMillis(1);
        assertThat(checker.check(credentialA))
                .isFalse();

        now = NOW.plus(CredentialExpiryChecker.WARNING_INTERVAL);
        assertThat(checker.check(credentialA))
                .isTrue();
    }

    @Test
    void testFirstExpiredWarningIsNotHeldBackByAnExpiringOne() {
        final Credential credential = createCredential("a", NOW.plus(Duration.ofMinutes(5)));

        assertThat(checker.check(credential))
                .isTrue();
        // Expired a minute later, well within the hour since it was warned about as expiring
        now = NOW.plus(Duration.ofMinutes(6));
        assertThat(checker.check(credential))
                .isTrue();
        assertThat(checker.check(credential))
                .isFalse();
    }

    @Test
    void testForgottenCredentialIsWarnedAboutAgain() {
        final Credential credential = createCredential("a", NOW.minus(Duration.ofDays(1)));

        assertThat(checker.check(credential))
                .isTrue();
        checker.forget(credential.getUuid());
        assertThat(checker.check(credential))
                .isTrue();
    }

    @Test
    void testCertificateExpiringFirstIsRefused() {
        credentialsConfig = createConfig(true, null);
        final Credential credential = createCredential("a", NOW.plus(Duration.ofDays(100)));

        assertThatThrownBy(() -> checker.check(credential, NOW.minus(Duration.ofDays(1))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("The certificate in credential 'a'")
                .hasMessageContaining("Replace its key store");
    }

    @Test
    void testExpiredCertificateOfAKeyStoreFileSaysToReplaceTheFile() {
        credentialsConfig = createConfig(true, null);
        final Credential credential = createCredential("a", CredentialType.KEY_STORE_FILE, null);

        assertThatThrownBy(() -> checker.check(credential, NOW.minus(Duration.ofDays(1))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Replace its key store file.");
    }

    @Test
    void testCertificateExpiryIsUsedWhenThereIsNoDate() {
        assertThat(checker.check(createCredential("a", null), NOW.plus(Duration.ofDays(1))))
                .isTrue();
    }

    @Test
    void testDateExpiringFirstIsUsed() {
        credentialsConfig = createConfig(true, null);

        assertThatThrownBy(() -> checker.check(
                createCredential("a", NOW.minus(Duration.ofDays(1))),
                NOW.plus(Duration.ofDays(100))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Credential 'a'");
    }

    @Test
    void testLaterCertificateDoesNotWarn() {
        assertThat(checker.check(createCredential("a", null), NOW.plus(Duration.ofDays(100))))
                .isFalse();
    }

    @Test
    void testWarningPeriodDefaultsToZeroWhenNotSet() {
        credentialsConfig = null;

        assertThat(checker.getWarningPeriod())
                .isEqualTo(Duration.ZERO);
    }

    @Test
    void testNegativeWarningPeriodGivesZero() {
        credentialsConfig = createConfig(false, StroomDuration.of(Duration.ofDays(-1)));

        assertThat(checker.getWarningPeriod())
                .isEqualTo(Duration.ZERO);
        assertThat(checker.check(createCredential("a", NOW.plus(Duration.ofMinutes(1)))))
                .isFalse();
    }

    @Test
    void testHugeWarningPeriodIsCapped() {
        credentialsConfig = createConfig(false, StroomDuration.of(Duration.ofSeconds(Long.MAX_VALUE)));

        assertThat(checker.getWarningPeriod())
                .isEqualTo(CredentialExpiryChecker.MAX_WARNING_PERIOD);
        // Rather than the time overflowing
        assertThat(checker.check(createCredential("a", NOW.plus(Duration.ofDays(365)))))
                .isTrue();
    }

    private static CredentialsConfig createConfig(final boolean refuseExpiredCredentials,
                                                  final StroomDuration expiryWarningPeriod) {
        return new CredentialsConfig(null,
                null,
                null,
                null,
                null,
                null,
                refuseExpiredCredentials,
                expiryWarningPeriod);
    }

    private static Credential createCredential(final String name, final Instant expiryTime) {
        return createCredential(name, CredentialType.USERNAME_PASSWORD, expiryTime);
    }

    private static Credential createCredential(final String name,
                                               final CredentialType credentialType,
                                               final Instant expiryTime) {
        return new Credential("uuid-" + name,
                name,
                1_000L,
                1_000L,
                "creator",
                "creator",
                credentialType,
                null,
                expiryTime == null
                        ? null
                        : expiryTime.toEpochMilli());
    }
}
