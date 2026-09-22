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

package stroom.data.store.impl.fs;

import stroom.util.json.JsonUtil;
import stroom.util.time.StroomDuration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestDataStoreServiceConfig {

    @Test
    void testFsyncEnabled_defaultsToTrue() {
        assertThat(new DataStoreServiceConfig().isFsyncEnabled())
                .isTrue();
    }

    @Test
    void testFsyncEnabled_absentPropertyFallsBackToDefault() {
        // An operator upgrading will not have this property in their YAML, and must still get the
        // durable behaviour rather than a primitive false.
        final DataStoreServiceConfig config = JsonUtil.readValue(
                "{\"deleteBatchSize\":50}", DataStoreServiceConfig.class);

        assertThat(config.isFsyncEnabled()).isTrue();
        assertThat(config.getDeleteBatchSize()).isEqualTo(50);
    }

    @Test
    void testFsyncEnabled_canBeDisabled() {
        final DataStoreServiceConfig config = JsonUtil.readValue(
                "{\"fsyncEnabled\":false}", DataStoreServiceConfig.class);

        assertThat(config.isFsyncEnabled()).isFalse();
    }

    @Test
    void testFsyncEnabled_survivesSerialisationRoundTrip() {
        final DataStoreServiceConfig original = JsonUtil.readValue(
                "{\"fsyncEnabled\":false}", DataStoreServiceConfig.class);

        final String json = JsonUtil.writeValueAsString(original);
        final DataStoreServiceConfig restored = JsonUtil.readValue(json, DataStoreServiceConfig.class);

        assertThat(restored.isFsyncEnabled()).isFalse();
    }

    @Test
    void testFsyncEnabled_isCarriedByWithMethods() {
        // The with* copy methods must not silently reset the setting back to its default.
        final DataStoreServiceConfig config = JsonUtil.readValue(
                "{\"fsyncEnabled\":false}", DataStoreServiceConfig.class);

        assertThat(config.withDeleteBatchSize(10).isFsyncEnabled()).isFalse();
        assertThat(config.withDeletePurgeAge(StroomDuration.ofDays(2)).isFsyncEnabled()).isFalse();
        assertThat(config.withFileSystemCleanOldAge(StroomDuration.ofDays(3)).isFsyncEnabled()).isFalse();
    }
}
