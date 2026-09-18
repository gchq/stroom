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

package stroom.security.client.presenter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestApiKeyExpiryFormatter {

    private static final long NOW = 1_000_000_000_000L;
    private static final long THIRTY_DAYS = 30L * 24 * 60 * 60 * 1_000;
    private static final String DATE = "2026-09-10 (2 days)";

    @Test
    void testExpired() {
        assertThat(ApiKeyExpiryFormatter.format(NOW - 1, NOW, DATE).asString())
                .isEqualTo("<em>Expired: " + DATE + "</em>");
    }

    @Test
    void testExpiryAtCurrentTime() {
        assertThat(ApiKeyExpiryFormatter.format(NOW, NOW, DATE).asString())
                .isEqualTo("<em>Expired: " + DATE + "</em>");
    }

    @Test
    void testSoon() {
        assertThat(ApiKeyExpiryFormatter.format(NOW + 1, NOW, DATE).asString())
                .isEqualTo("<strong>Expires soon: " + DATE + "</strong>");
        assertThat(ApiKeyExpiryFormatter.format(NOW + THIRTY_DAYS, NOW, DATE).asString())
                .isEqualTo("<strong>Expires soon: " + DATE + "</strong>");
    }

    @Test
    void testBeyondThirtyDays() {
        assertThat(ApiKeyExpiryFormatter.format(NOW + THIRTY_DAYS + 1, NOW, DATE).asString())
                .isEqualTo(DATE);
    }

    @Test
    void testNoExpiry() {
        assertThat(ApiKeyExpiryFormatter.format(null, NOW, DATE).asString()).isEqualTo(DATE);
        assertThat(ApiKeyExpiryFormatter.format(null, NOW, null).asString()).isEmpty();
    }

    @Test
    void testEscapingForEveryStatus() {
        for (final Long expiry : new Long[]{NOW, NOW + 1, NOW + THIRTY_DAYS + 1, null}) {
            assertThat(ApiKeyExpiryFormatter.format(expiry, NOW, "<script>&").asString())
                    .contains("&lt;script&gt;&amp;")
                    .doesNotContain("<script>");
        }
    }

    @Test
    void testNearMaximumTimestamp() {
        assertThat(ApiKeyExpiryFormatter.format(Long.MAX_VALUE, Long.MAX_VALUE - 1, DATE).asString())
                .startsWith("<strong>Expires soon: ");
    }

    @Test
    void testDistantTimestampDoesNotOverflow() {
        assertThat(ApiKeyExpiryFormatter.format(Long.MAX_VALUE, Long.MIN_VALUE, DATE).asString())
                .isEqualTo(DATE);
    }
}
