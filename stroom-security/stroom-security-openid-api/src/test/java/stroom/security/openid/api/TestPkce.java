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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestPkce {

    @Test
    void matchesTheRfc7636ExampleVector() {
        // From RFC 7636 Appendix B, so this proves interoperability with any conformant client or server.
        final String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
        final String expectedChallenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

        assertThat(Pkce.createS256Challenge(verifier)).isEqualTo(expectedChallenge);
    }

    @Test
    void freshVerifierMeetsTheLengthRequirementAndRoundTrips() {
        final String verifier = Pkce.createCodeVerifier();

        // RFC 7636 requires a verifier of 43 to 128 characters.
        assertThat(verifier.length()).isBetween(43, 128);
        // The challenge for a verifier is stable, so recomputing it at redemption matches.
        assertThat(Pkce.createS256Challenge(verifier)).isEqualTo(Pkce.createS256Challenge(verifier));
    }

    @Test
    void differentVerifiersGiveDifferentChallenges() {
        assertThat(Pkce.createS256Challenge(Pkce.createCodeVerifier()))
                .isNotEqualTo(Pkce.createS256Challenge(Pkce.createCodeVerifier()));
    }
}
