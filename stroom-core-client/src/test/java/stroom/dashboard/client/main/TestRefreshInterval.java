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

package stroom.dashboard.client.main;

import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TestRefreshInterval {

    private static final SimpleDuration FIVE_MINUTES = new SimpleDuration(5L, TimeUnit.MINUTES);

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void validate_notSet(final String interval) {
        assertThat(RefreshInterval.validate(interval))
                .isEqualTo(RefreshInterval.NOT_SET_MESSAGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"soon", "10 parsecs"})
    void validate_unreadable(final String interval) {
        // Regression test: an interval that couldn't be read was reported as a NullPointerException
        assertThat(RefreshInterval.validate(interval))
                .isEqualTo(RefreshInterval.UNREADABLE_MESSAGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"5s", "9999"})
    void validate_tooShort(final String interval) {
        assertThat(RefreshInterval.validate(interval))
                .isEqualTo(RefreshInterval.TOO_SHORT_MESSAGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"25d", "30d", "365d"})
    void validate_tooLong(final String interval) {
        // Regression test: an interval of 25 days or more wrapped round to a negative number and
        // was refused as being under 10 seconds
        assertThat(RefreshInterval.validate(interval))
                .isEqualTo(RefreshInterval.TOO_LONG_MESSAGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"10s", "1m", "1h", "24d"})
    void validate_valid(final String interval) {
        assertThat(RefreshInterval.validate(interval))
                .isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void getMillis_notSet(final String interval) {
        // Regression test: a component saved before the interval had a default has none, which
        // threw (and was ignored), so with Auto Refresh ticked it never refreshed
        assertThat(RefreshInterval.getMillis(interval))
                .isEqualTo(10_000);
    }

    @ParameterizedTest
    @ValueSource(strings = {"soon", "10 parsecs"})
    void getMillis_unreadable(final String interval) {
        // An interval that can't be read is the default too, rather than never refreshing
        assertThat(RefreshInterval.getMillis(interval))
                .isEqualTo(10_000);
    }

    @Test
    void getMillis_set() {
        assertThat(RefreshInterval.getMillis("1m"))
                .isEqualTo(60_000);
    }

    @Test
    void getMillis_underTenSeconds() {
        assertThat(RefreshInterval.getMillis("2s"))
                .isEqualTo(RefreshInterval.MIN_MILLIS);
    }

    @Test
    void getMillis_tooLong() {
        // Regression test: 30 days is more milliseconds than an int holds, which once wrapped round
        // to a negative wait
        assertThat(RefreshInterval.getMillis("30d"))
                .isEqualTo(RefreshInterval.MAX_MILLIS);
    }

    @Test
    void getMillis_noDataSourceMinimum() {
        assertThat(RefreshInterval.getMillis("30s", null))
                .isEqualTo(30_000);
    }

    @Test
    void getMillis_dataSourceMinimumRaisesShorterInterval() {
        // As for an Amazon Athena Data Connection refreshed every 30 seconds
        assertThat(RefreshInterval.getMillis("30s", FIVE_MINUTES))
                .isEqualTo(300_000);
    }

    @Test
    void getMillis_longerIntervalWinsOverDataSourceMinimum() {
        assertThat(RefreshInterval.getMillis("1h", FIVE_MINUTES))
                .isEqualTo(3_600_000);
    }

    @Test
    void getMillis_dataSourceMinimumUnderTenSeconds() {
        assertThat(RefreshInterval.getMillis("1s", new SimpleDuration(2L, TimeUnit.SECONDS)))
                .isEqualTo(RefreshInterval.MIN_MILLIS);
    }

    @Test
    void getMillis_unreadableIntervalWithDataSourceMinimum() {
        // An unreadable interval is the default, then raised to the data source's minimum
        assertThat(RefreshInterval.getMillis("soon", FIVE_MINUTES))
                .isEqualTo(300_000);
    }

    @Test
    void getMillis_dataSourceMinimumOverMaximum() {
        assertThat(RefreshInterval.getMillis("1m", new SimpleDuration(30L, TimeUnit.DAYS)))
                .isEqualTo(RefreshInterval.MAX_MILLIS);
    }
}
