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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestRefreshDelay {

    private static final SimpleDuration FIVE_MINUTES = new SimpleDuration(5L, TimeUnit.MINUTES);

    @Test
    void testUsesChosenIntervalWithoutDataSourceMinimum() {
        assertThat(RefreshDelay.millis("30s", null)).isEqualTo(30_000L);
    }

    @Test
    void testNeverSoonerThanTenSeconds() {
        assertThat(RefreshDelay.millis("1s", null)).isEqualTo(RefreshDelay.DASHBOARD_MIN_MILLIS);
    }

    @Test
    void testDataSourceMinimumRaisesShorterInterval() {
        // As for an Amazon Athena source refreshed every 30 seconds.
        assertThat(RefreshDelay.millis("30s", FIVE_MINUTES)).isEqualTo(300_000L);
    }

    @Test
    void testLongerChosenIntervalWinsOverDataSourceMinimum() {
        assertThat(RefreshDelay.millis("1h", FIVE_MINUTES)).isEqualTo(3_600_000L);
    }

    @Test
    void testDataSourceMinimumBelowTenSecondsStillWaitsTenSeconds() {
        assertThat(RefreshDelay.millis("1s", new SimpleDuration(2L, TimeUnit.SECONDS)))
                .isEqualTo(RefreshDelay.DASHBOARD_MIN_MILLIS);
    }

    @Test
    void testEmptyIntervalIsRefused() {
        assertThatThrownBy(() -> RefreshDelay.millis("", FIVE_MINUTES))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testUnreadableIntervalIsRefused() {
        assertThatThrownBy(() -> RefreshDelay.millis("soon", FIVE_MINUTES))
                .isInstanceOf(RuntimeException.class);
    }
}
