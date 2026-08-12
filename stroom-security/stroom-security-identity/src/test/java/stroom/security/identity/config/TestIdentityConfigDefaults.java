/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.security.identity.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A YAML block that mentions the section at all but omits a field leaves that field null in the
 * constructor. Every field has to default, or a partial block silently changes behaviour rather than being
 * merged with what is not stated.
 */
class TestIdentityConfigDefaults {

    /**
     * Built as deserialising a block that names none of these fields would.
     */
    private static IdentityConfig fromEmptyBlock() {
        return new IdentityConfig(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    void omittedLockThresholdDoesNotTurnLockoutOff() {
        // The worst of these to leave null, because it does not fail: a null threshold means failures are
        // counted and nothing ever locks, so the brute force protection is simply absent and nothing says
        // so. Silence is the problem, not the exception.
        assertThat(fromEmptyBlock().getFailedLoginLockThreshold())
                .isNotNull()
                .isEqualTo(new IdentityConfig().getFailedLoginLockThreshold());
    }

    @Test
    void omittedCertificatePatternDoesNotBreakCertificateSignIn() {
        // Left null this compiles into Pattern.compile(null), which throws on every certificate sign in.
        assertThat(fromEmptyBlock().getCertificateCnPattern())
                .isNotNull()
                .isEqualTo(new IdentityConfig().getCertificateCnPattern());
    }

    @Test
    void omittedBlockMatchesTheDefaultsThroughout() {
        final IdentityConfig fromBlock = fromEmptyBlock();
        final IdentityConfig defaults = new IdentityConfig();

        assertThat(fromBlock.getFailedLoginLockDuration()).isEqualTo(defaults.getFailedLoginLockDuration());
        assertThat(fromBlock.isAllowCertificateAuthentication())
                .isEqualTo(defaults.isAllowCertificateAuthentication());
        assertThat(fromBlock.getCertificateCnCaptureGroupIndex())
                .isEqualTo(defaults.getCertificateCnCaptureGroupIndex());
        assertThat(fromBlock.isReactivateInactiveAccountsOnLogin())
                .isEqualTo(defaults.isReactivateInactiveAccountsOnLogin());
        assertThat(fromBlock.isAllowLockedAccountPasswordReset())
                .isEqualTo(defaults.isAllowLockedAccountPasswordReset());
    }

    @Test
    void omittedNestedBlocksDoNotDisappear() {
        // The nested blocks were left raw while the scalars were defaulted, so an identity block with no
        // passwordPolicy sub-block made getPasswordPolicyConfig() null - which does not fail on boot, it
        // fails on the first successful sign in, where the mandatory-change check reads it.
        final IdentityConfig fromBlock = fromEmptyBlock();

        assertThat(fromBlock.getPasswordPolicyConfig()).isNotNull();
        assertThat(fromBlock.getTokenConfig()).isNotNull();
        assertThat(fromBlock.getEmailConfig()).isNotNull();
        assertThat(fromBlock.getOpenIdConfig()).isNotNull();
        assertThat(fromBlock.getDbConfig()).isNotNull();
    }

    @Test
    void omittedSmtpTransportDoesNotKillTheResetEmail() {
        // Read inside the executor that sends the mail, long after the user has been told it is on its way,
        // so a null here fails somewhere nobody is looking.
        final SmtpConfig fromBlock = new SmtpConfig(null, null, null, null, null);

        assertThat(fromBlock.getTransport()).isNotNull();
        assertThat(fromBlock.getTransportStrategy()).isNotNull();
    }
}
