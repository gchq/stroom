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

package stroom.index.impl;

import stroom.util.time.StroomDuration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestIndexWriterConfig {

    @Test
    void testValidShardLease_defaults() {
        assertThat(new IndexWriterConfig().isValidShardLease()).isTrue();
    }

    @Test
    void testValidShardLease_leaseTooShort() {
        assertThat(create(StroomDuration.ofSeconds(9), StroomDuration.ofSeconds(3)).isValidShardLease()).isFalse();
        assertThat(create(StroomDuration.ZERO, StroomDuration.ofSeconds(3)).isValidShardLease()).isFalse();
        assertThat(create(StroomDuration.ofSeconds(10), StroomDuration.ofSeconds(3)).isValidShardLease()).isTrue();
    }

    @Test
    void testValidShardLease_renewIntervalTooShort() {
        assertThat(create(StroomDuration.ofMinutes(1), StroomDuration.ZERO).isValidShardLease()).isFalse();
        assertThat(create(StroomDuration.ofMinutes(1), StroomDuration.ofSeconds(1)).isValidShardLease()).isTrue();
    }

    private static IndexWriterConfig create(final StroomDuration leaseDuration,
                                            final StroomDuration renewInterval) {
        return new IndexWriterConfig(
                null,
                null,
                null,
                null,
                null,
                leaseDuration,
                renewInterval,
                null);
    }
}
