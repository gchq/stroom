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

import stroom.util.io.FsyncMode;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestFsyncConfig {

    @Test
    void testDefaults_allPhasesEnabled() {
        final FsyncConfig fsyncConfig = new FsyncConfig();

        assertThat(fsyncConfig.getReceivingMode()).isEqualTo(FsyncMode.ENABLED);
        assertThat(fsyncConfig.getZipSplittingInputQueueMode()).isEqualTo(FsyncMode.ENABLED);
        assertThat(fsyncConfig.getPreAggregateInputQueueMode()).isEqualTo(FsyncMode.ENABLED);
        assertThat(fsyncConfig.getAggregateInputQueueMode()).isEqualTo(FsyncMode.ENABLED);
        assertThat(fsyncConfig.getForwardingInputQueueMode()).isEqualTo(FsyncMode.ENABLED);
    }

    @Test
    void testAbsentPropertiesFallBackToDefaults() {
        // An operator who has not set the section at all, or has set only part of it, must still
        // get the safe behaviour for the properties they omitted.
        final FsyncConfig fsyncConfig = JsonUtil.readValue("{\"receivingMode\":\"DISABLED\"}", FsyncConfig.class);

        assertThat(fsyncConfig.getReceivingMode()).isEqualTo(FsyncMode.DISABLED);
        assertThat(fsyncConfig.getZipSplittingInputQueueMode()).isEqualTo(FsyncMode.ENABLED);
        assertThat(fsyncConfig.getPreAggregateInputQueueMode()).isEqualTo(FsyncMode.ENABLED);
        assertThat(fsyncConfig.getAggregateInputQueueMode()).isEqualTo(FsyncMode.ENABLED);
        assertThat(fsyncConfig.getForwardingInputQueueMode()).isEqualTo(FsyncMode.ENABLED);
    }

    @Test
    void testEachPhaseCanBeDisabledIndependently() {
        final FsyncConfig fsyncConfig = new FsyncConfig(
                FsyncMode.DISABLED,
                FsyncMode.FILE_ONLY,
                FsyncMode.DIR_ONLY,
                FsyncMode.ENABLED,
                FsyncMode.DISABLED);

        assertThat(fsyncConfig.getReceivingMode()).isEqualTo(FsyncMode.DISABLED);
        assertThat(fsyncConfig.getZipSplittingInputQueueMode()).isEqualTo(FsyncMode.FILE_ONLY);
        assertThat(fsyncConfig.getPreAggregateInputQueueMode()).isEqualTo(FsyncMode.DIR_ONLY);
        assertThat(fsyncConfig.getAggregateInputQueueMode()).isEqualTo(FsyncMode.ENABLED);
        assertThat(fsyncConfig.getForwardingInputQueueMode()).isEqualTo(FsyncMode.DISABLED);
    }

    @Test
    void testSerialisationRoundTrip() {
        final FsyncConfig original = new FsyncConfig(
                FsyncMode.DISABLED, FsyncMode.FILE_ONLY, FsyncMode.DIR_ONLY,
                FsyncMode.ENABLED, FsyncMode.DISABLED);

        final String json = JsonUtil.writeValueAsString(original);
        final FsyncConfig restored = JsonUtil.readValue(json, FsyncConfig.class);

        assertThat(restored.getReceivingMode()).isEqualTo(original.getReceivingMode());
        assertThat(restored.getZipSplittingInputQueueMode()).isEqualTo(original.getZipSplittingInputQueueMode());
        assertThat(restored.getPreAggregateInputQueueMode()).isEqualTo(original.getPreAggregateInputQueueMode());
        assertThat(restored.getAggregateInputQueueMode()).isEqualTo(original.getAggregateInputQueueMode());
        assertThat(restored.getForwardingInputQueueMode()).isEqualTo(original.getForwardingInputQueueMode());
    }
}
