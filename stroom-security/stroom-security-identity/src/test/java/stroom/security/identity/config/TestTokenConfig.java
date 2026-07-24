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

package stroom.security.identity.config;

import stroom.util.time.StroomDuration;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class TestTokenConfig {

    @Test
    void retentionCoversTheLongestTokenLifetime_default() {
        final TokenConfig config = new TokenConfig();

        // With the defaults the refresh token, at 30 days, is the longest lived thing signed by the
        // key, so retention must be at least that.
        assertThat(config.getJwkRetention())
                .isGreaterThanOrEqualTo(config.getRefreshTokenExpiration().getDuration());
    }

    @Test
    void retentionFollowsAnIncreasedTokenLifetime() {
        // The whole point of deriving retention rather than configuring it: raising a token lifetime
        // must raise retention too, so a retired key is not deleted while its tokens still verify.
        final StroomDuration longRefresh = StroomDuration.ofDays(90);
        final TokenConfig config = new TokenConfig(
                longRefresh,
                StroomDuration.ofMinutes(60),
                StroomDuration.ofMinutes(60),
                StroomDuration.ofMinutes(10),
                StroomDuration.ofDays(365),
                StroomDuration.ofDays(30),
                "stroom",
                "RS256");

        assertThat(config.getJwkRetention())
                .isGreaterThanOrEqualTo(longRefresh.getDuration());
    }

    @Test
    void retentionIgnoresApiKeyExpiration() {
        // API keys are opaque database values, not JWTs signed by the key, so their long default
        // expiry must not drag retention out to a year.
        final TokenConfig config = new TokenConfig(
                StroomDuration.ofDays(30),
                StroomDuration.ofMinutes(60),
                StroomDuration.ofMinutes(60),
                StroomDuration.ofMinutes(10),
                StroomDuration.ofDays(365),
                StroomDuration.ofDays(30),
                "stroom",
                "RS256");

        assertThat(config.getJwkRetention())
                .isLessThan(Duration.ofDays(365));
    }
}
