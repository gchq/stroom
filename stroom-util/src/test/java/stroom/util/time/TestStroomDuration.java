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

package stroom.util.time;

import stroom.util.json.JsonUtil;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TestStroomDuration {

    @Test
    void testInstantiate() {
        doInstantiateTest(StroomDuration.ZERO, Duration.ZERO, "PT0S");
        doInstantiateTest(StroomDuration.ofNanos(100), Duration.ofNanos(100), "PT0.0000001S");
        doInstantiateTest(StroomDuration.ofMillis(100), Duration.ofMillis(100), "PT0.1S");
        doInstantiateTest(StroomDuration.ofSeconds(50), Duration.ofSeconds(50), "PT50S");
        doInstantiateTest(StroomDuration.ofMinutes(10), Duration.ofMinutes(10), "PT10M");
        doInstantiateTest(StroomDuration.ofHours(10), Duration.ofHours(10), "PT10H");

        doInstantiateTest(StroomDuration.ofHours(23), Duration.ofHours(23), "PT23H");
        doInstantiateTest(StroomDuration.ofHours(24), Duration.ofHours(24), "P1D");
        // Not a whole number of days so same string form as duration
        doInstantiateTest(StroomDuration.ofHours(47), Duration.ofHours(47), "P1DT23H");
        // Whole number of days so we render it as days
        doInstantiateTest(StroomDuration.ofHours(48), Duration.ofHours(48), "P2D");
        // Whole number of days so we render it as days
        doInstantiateTest(StroomDuration.ofDays(1), Duration.ofDays(1), "P1D");
        doInstantiateTest(StroomDuration.ofDays(30), Duration.ofDays(30), "P30D");
        doInstantiateTest(StroomDuration.ofDays(90), Duration.ofDays(90), "P90D");
    }

    @Test
    void testMaxDuration() {
        // Approx max duration
        final StroomDuration stroomDuration = StroomDuration.ofDays(99_999_999_999_999L);
    }

    @Test
    void testParse() {
        System.out.println(Duration.ZERO.toString());
        doParseTest("30d", "P30D", Duration.ofDays(30));
        doParseTest("12h", "PT12H", Duration.ofHours(12));
        doParseTest("5m", "PT5M", Duration.ofMinutes(5));
        doParseTest("10s", "PT10S", Duration.ofSeconds(10));
        doParseTest("100ms", "PT0.1S", Duration.ofMillis(100));

        // raw millis form
        doParseTest("100", "PT0.1S", Duration.ofMillis(100));
    }

    @Test
    void getValueAsStr() {
        final String input = "P30D";
        final StroomDuration stroomDuration = StroomDuration.parse(input);
        assertThat(stroomDuration.getValueAsStr()).isEqualTo(input);
    }

    @Test
    void getDuration() {
        final String input = "P30D";
        final StroomDuration stroomDuration = StroomDuration.parse(input);

        assertThat(stroomDuration.getDuration())
                .isEqualTo(Duration.ofDays(30));
    }

    @Test
    void testSerde() {
        final StroomDuration stroomDuration = StroomDuration.parse("30d");
        final String json = JsonUtil.writeValueAsString(stroomDuration);
        System.out.println(json);
        final StroomDuration stroomDuration2 = JsonUtil.readValue(json, StroomDuration.class);
        assertThat(stroomDuration)
                .isEqualTo(stroomDuration2);
    }

    @Test
    void testPlus() {
        final Instant now = Instant.now();
        final StroomDuration duration = StroomDuration.ofMinutes(5);

        final Instant nowPlus = now.plus(duration);

        Assertions.assertThat(Duration.between(now, nowPlus))
                .isEqualTo(duration.getDuration());
    }

    void doParseTest(final String modelStringUtilInput, final String isoInput, final Duration expectedDuration) {
        final StroomDuration stroomDuration = StroomDuration.parse(modelStringUtilInput);
        assertThat(stroomDuration.getDuration()).isEqualTo(expectedDuration);
        assertThat(stroomDuration.getValueAsStr()).isEqualTo(modelStringUtilInput);

        final StroomDuration stroomDuration2 = StroomDuration.parse(isoInput);
        assertThat(stroomDuration2.getDuration()).isEqualTo(expectedDuration);
        assertThat(stroomDuration2.getValueAsStr()).isEqualTo(isoInput);

        assertThat(stroomDuration).isEqualTo(stroomDuration2);
    }

    private void doInstantiateTest(final StroomDuration stroomDuration,
                                   final Duration expectedDuration,
                                   final String expectedToString) {

        assertThat(stroomDuration.getDuration()).isEqualTo(expectedDuration);
        assertThat(stroomDuration.toString()).isEqualTo(expectedToString);
        assertThat(stroomDuration.toMillis()).isEqualTo(expectedDuration.toMillis());
        assertThat(stroomDuration.toNanos()).isEqualTo(expectedDuration.toNanos());
    }
}
