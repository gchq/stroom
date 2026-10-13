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

class TestForwardFileConfig {

    @Test
    void testDefaults_fsyncModeDefaultsToEnabled() {
        final ForwardFileConfig config = new ForwardFileConfig();
        assertThat(config.getFsyncMode()).isEqualTo(FsyncMode.ENABLED);
    }

    @Test
    void testAbsentPropertyFallsBackToDefault() {
        final ForwardFileConfig config = JsonUtil.readValue("{\"name\":\"test\"}", ForwardFileConfig.class);
        assertThat(config.getFsyncMode()).isEqualTo(FsyncMode.ENABLED);
    }

    @Test
    void testFsyncMode_canBeDisabled() {
        final ForwardFileConfig config = JsonUtil.readValue(
                "{\"fsyncMode\":\"DISABLED\"}",
                ForwardFileConfig.class);
        assertThat(config.getFsyncMode()).isEqualTo(FsyncMode.DISABLED);
    }

    @Test
    void testFsyncMode_survivesSerialisationRoundTrip() {
        final ForwardFileConfig original = ForwardFileConfig.builder()
                .withName("dest1")
                .withPath("/tmp/dest")
                .withFsyncMode(FsyncMode.FILE_ONLY)
                .build();
        final String json = JsonUtil.writeValueAsString(original);
        final ForwardFileConfig restored = JsonUtil.readValue(json, ForwardFileConfig.class);
        assertThat(restored.getFsyncMode()).isEqualTo(FsyncMode.FILE_ONLY);
    }

    @Test
    void testBuilder_copiesFsyncMode() {
        final ForwardFileConfig original = ForwardFileConfig.builder()
                .withFsyncMode(FsyncMode.DIR_ONLY)
                .build();
        final ForwardFileConfig copy = ForwardFileConfig.builder(original)
                .build();
        assertThat(copy.getFsyncMode()).isEqualTo(FsyncMode.DIR_ONLY);
    }
}
