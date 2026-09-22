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

package stroom.proxy.app.handler;

import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestFsyncConfig {

    @Test
    void testDefaults_allPhasesEnabled() {
        final FsyncConfig fsyncConfig = new FsyncConfig();

        assertThat(fsyncConfig.isReceiving()).isTrue();
        assertThat(fsyncConfig.isZipSplittingInputQueue()).isTrue();
        assertThat(fsyncConfig.isPreAggregateInputQueue()).isTrue();
        assertThat(fsyncConfig.isAggregateInputQueue()).isTrue();
        assertThat(fsyncConfig.isForwardingInputQueue()).isTrue();
    }

    @Test
    void testAbsentPropertiesFallBackToDefaults() {
        // An operator who has not set the section at all, or has set only part of it, must still
        // get the safe behaviour for the properties they omitted.
        final FsyncConfig fsyncConfig = JsonUtil.readValue("{\"receiving\":false}", FsyncConfig.class);

        assertThat(fsyncConfig.isReceiving()).isFalse();
        assertThat(fsyncConfig.isZipSplittingInputQueue()).isTrue();
        assertThat(fsyncConfig.isPreAggregateInputQueue()).isTrue();
        assertThat(fsyncConfig.isAggregateInputQueue()).isTrue();
        assertThat(fsyncConfig.isForwardingInputQueue()).isTrue();
    }

    @Test
    void testEachPhaseCanBeDisabledIndependently() {
        final FsyncConfig fsyncConfig = new FsyncConfig(
                false,
                true,
                false,
                true,
                false);

        assertThat(fsyncConfig.isReceiving()).isFalse();
        assertThat(fsyncConfig.isZipSplittingInputQueue()).isTrue();
        assertThat(fsyncConfig.isPreAggregateInputQueue()).isFalse();
        assertThat(fsyncConfig.isAggregateInputQueue()).isTrue();
        assertThat(fsyncConfig.isForwardingInputQueue()).isFalse();
    }

    @Test
    void testSerialisationRoundTrip() {
        final FsyncConfig original = new FsyncConfig(false, false, true, false, true);

        final String json = JsonUtil.writeValueAsString(original);
        final FsyncConfig restored = JsonUtil.readValue(json, FsyncConfig.class);

        assertThat(restored.isReceiving()).isEqualTo(original.isReceiving());
        assertThat(restored.isZipSplittingInputQueue()).isEqualTo(original.isZipSplittingInputQueue());
        assertThat(restored.isPreAggregateInputQueue()).isEqualTo(original.isPreAggregateInputQueue());
        assertThat(restored.isAggregateInputQueue()).isEqualTo(original.isAggregateInputQueue());
        assertThat(restored.isForwardingInputQueue()).isEqualTo(original.isForwardingInputQueue());
    }
}
