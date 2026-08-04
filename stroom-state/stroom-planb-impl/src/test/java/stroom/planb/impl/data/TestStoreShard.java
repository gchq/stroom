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

package stroom.planb.impl.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class TestStoreShard {

    private static final Duration LIFESPAN = Duration.ofMinutes(10);

    /**
     * Snapshot creation re-zips the whole shard while holding the write lock, so a shard that always fails must
     * not be retried on every run of the snapshot creation job. See gh-5689.
     */
    @ParameterizedTest
    @CsvSource({
            "1, 10",
            "2, 20",
            "3, 30",
            "5, 50",
            "6, 60",
    })
    void snapshotRetryDelayGrowsLinearlyWithFailures(final int failureCount, final int expectedMinutes) {
        assertThat(StoreShard.getSnapshotRetryDelay(LIFESPAN, failureCount))
                .isEqualTo(Duration.ofMinutes(expectedMinutes));
    }

    @ParameterizedTest
    @CsvSource({
            "7",
            "100",
            "1000",
    })
    void snapshotRetryDelayIsCapped(final int failureCount) {
        assertThat(StoreShard.getSnapshotRetryDelay(LIFESPAN, failureCount))
                .isEqualTo(Duration.ofMinutes(60));
    }

    /**
     * The delay is read before the failure count has been incremented in some paths, so a count of zero must
     * still yield a usable delay rather than zero, which would mean no back off at all.
     */
    @Test
    void snapshotRetryDelayIsAtLeastOneLifespan() {
        assertThat(StoreShard.getSnapshotRetryDelay(LIFESPAN, 0)).isEqualTo(LIFESPAN);
        assertThat(StoreShard.getSnapshotRetryDelay(LIFESPAN, -1)).isEqualTo(LIFESPAN);
    }

    @Test
    void snapshotRetryDelayScalesWithConfiguredLifespan() {
        final Duration lifespan = Duration.ofMinutes(2);
        assertThat(StoreShard.getSnapshotRetryDelay(lifespan, 3)).isEqualTo(Duration.ofMinutes(6));
        assertThat(StoreShard.getSnapshotRetryDelay(lifespan, 99)).isEqualTo(Duration.ofMinutes(12));
    }
}
