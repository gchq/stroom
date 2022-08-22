/*
 * Copyright 2016 Crown Copyright
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


import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.data.store.impl.fs.shared.FsVolumeState;
import stroom.test.common.util.test.StroomUnitTest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestVolumeSelector extends StroomUnitTest {

    private static final String PATH_1 = "path1";
    private static final String PATH_2 = "path2";
    private static final String PATH_3 = "path3";
    private static final String PATH_4 = "path4";

    private List<FsVolume> createVolumeList() {
        // 4k free, 80% free
        final FsVolume v1 = FsVolume.create(PATH_1, FsVolumeState.create(1_000, 5_000));
        // 4k free, 40% free
        final FsVolume v2 = FsVolume.create(PATH_2, FsVolumeState.create(6_000, 10_000));
        // 2k free, 20% free
        final FsVolume v3 = FsVolume.create(PATH_3, FsVolumeState.create(8_000, 10_000));
        // 0k free, 0% free
        final FsVolume v4 = FsVolume.create(PATH_4, FsVolumeState.create(10_000, 10_000));

        return List.of(v1, v2, v3, v4);
    }

    @Test
    void testMostFree() {
        test(new MostFreeVolumeSelector(), PATH_1, PATH_2);
    }

    @Test
    void testMostFreePercent() {
        test(new MostFreePercentVolumeSelector(), PATH_1);
    }

    @Test
    void testRandom() {
        test(new RandomVolumeSelector());
    }

    @Test
    void testWeightedFreeRandom() {
        test(new WeightedFreeRandomVolumeSelector());
    }

    @Test
    void testWeightedFreePercentRandom() {
        test(new WeightedFreePercentRandomVolumeSelector());
    }

    @Test
    void testRoundRobin() {
        test(new RoundRobinVolumeSelector());
    }

    @Test
    void testRoundRobinIgnoreLeastFree() {
        test(new RoundRobinIgnoreLeastFreeVolumeSelector(), PATH_1, PATH_2, PATH_3);
    }

    @Test
    void testRoundRobinIgnoreLeastFreePercent() {
        test(new RoundRobinIgnoreLeastFreePercentVolumeSelector(), PATH_1, PATH_2, PATH_3);
    }

//    private void test(final FsVolumeSelector volumeSelector) {
//        test(volumeSelector, );
//    }

    private void test(final FsVolumeSelector volumeSelector, final String... validExpectedVolPaths) {
        final List<FsVolume> volumes = createVolumeList();
        for (int i = 0; i < 100; i++) {
            final FsVolume selectedVolume = volumeSelector.select(volumes);
            assertThat(selectedVolume).isNotNull();
            if (validExpectedVolPaths != null && validExpectedVolPaths.length > 0) {
                assertThat(selectedVolume.getPath())
                        .isIn((Object[]) validExpectedVolPaths);
            }
        }
    }

}
