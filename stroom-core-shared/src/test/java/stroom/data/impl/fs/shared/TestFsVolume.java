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

package stroom.data.impl.fs.shared;

import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.data.store.impl.fs.shared.FsVolumeState;
import stroom.util.json.JsonUtil;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestFsVolume {

    private static final String TEST_JSON = """
            {
                "id":11,
                "version":1,
                "createTimeMs":1572596376500,
                "createUser":"admin",
                "updateTimeMs":1572596376500,
                "updateUser":"admin",
                "path":"sdfg",
                "status":"ACTIVE",
                "byteLimit":233887098470,
                "volumeState":{
                    "id":12,
                    "version":6,
                    "bytesUsed":20566679552,
                    "bytesFree":226035576832,
                    "bytesTotal":259874553856,
                    "updateTimeMs":1572597679531,
                    "percentUsed":7
                }
            }""";

    /**
     * Use this method to diagnose why your JSON-to-POJO bindings are failing.
     */
    @Test
    public void testJsonBindings() {
        final var fsVolume = JsonUtil.readValue(TEST_JSON, FsVolume.class);
        Assertions.assertThat(fsVolume)
                .isNotNull();
        Assertions.assertThat(fsVolume.getVolumeState())
                .isNotNull();
    }

    @Test
    void testIsFull_belowUserLimit() {
        doIsFullTest(800L, 500L, false);
    }

    @Test
    void testIsFull_aboveUserLimit() {
        doIsFullTest(800L, 850L, true);
    }

    @Test
    void testIsFull_belowHighUserLimit() {
        // User setting a limit == total to effectively prevent limit
        doIsFullTest(1_000L, 500L, false);
    }

    @Test
    void testIsFull_belowSillyUserLimit() {
        // User setting a limit > total so total is limit
        doIsFullTest(1_100L, 500L, false);
    }

    @Test
    void testIsFull_aboveSillyUserLimit() {
        // User setting a limit > total so total is limit
        doIsFullTest(1_100L, 1001L, true);
    }

    @Test
    void testIsFull_belowHardLimit() {
        // Hard coded limit is 99%
        doIsFullTest(null, 850L, false);
    }

    @Test
    void testIsFull_aboveHardLimit() {
        // Hard coded limit is 99%
        doIsFullTest(null, 995L, true);
    }

    private void doIsFullTest(final Long limit,
                              final Long used,
                              final boolean expectedIsFull) {
        final FsVolume fsVolume = new FsVolume();
        fsVolume.setByteLimit(limit);
        final long total = 1000;
        final long free = total - used;
        final FsVolumeState fsVolumeState = new FsVolumeState(
                1,
                1,
                used,
                free,
                total,
                System.currentTimeMillis());
        fsVolume.setVolumeState(fsVolumeState);

        Assertions.assertThat(fsVolume.getCapacityInfo().isFull())
                .isEqualTo(expectedIsFull);
        Assertions.assertThat(fsVolume.getCapacityInfo().getTotalCapacityBytes())
                .hasValue(total);
        Assertions.assertThat(fsVolume.getCapacityInfo().getCapacityUsedBytes())
                .hasValue(used);
        Assertions.assertThat(fsVolume.getCapacityInfo().getFreeCapacityBytes())
                .hasValue(free);
    }
}
