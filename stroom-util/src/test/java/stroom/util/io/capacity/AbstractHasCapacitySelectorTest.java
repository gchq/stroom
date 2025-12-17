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

package stroom.util.io.capacity;

import stroom.util.shared.HasCapacity;
import stroom.util.shared.HasCapacityInfo;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractHasCapacitySelectorTest {

    protected static final String PATH_1 = "path1";
    protected static final String PATH_2 = "path2";
    protected static final String PATH_3 = "path3";
    protected static final String PATH_4 = "path4";
    protected static final String PATH_5 = "path5";

    protected static final String[] ALL_PATHS = new String[]{
            PATH_1,
            PATH_2,
            PATH_3,
            PATH_4,
            PATH_5};

    protected static List<NoddyVolume> VOLUME_LIST = List.of(
            // 4k free, 80% free
            new NoddyVolume(PATH_1, 1_000, 5_000),
            // 4k free, 40% free
            new NoddyVolume(PATH_2, 6_000, 10_000),
            // 2k free, 20% free
            new NoddyVolume(PATH_3, 8_000, 10_000),
            // 0k free, 0% free
            new NoddyVolume(PATH_4, 10_000, 10_000),
            // 1k free, 10% free
            new NoddyVolume(PATH_5, 9_000L, 10_000L, 100_000L));

    abstract HasCapacitySelector getSelector();

    @Test
    void testNullList() {
        final HasCapacitySelector selector = getSelector();
        Assertions
                .assertThatThrownBy(() -> {
                    selector.select(null);
                })
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void testEmptyList() {
        final HasCapacitySelector selector = getSelector();
        Assertions
                .assertThatThrownBy(() -> {
                    selector.select(Collections.emptyList());
                })
                .isInstanceOf(RuntimeException.class);
    }

    protected void testMultipleTimes(final String... validExpectedVolPaths) {
        final HasCapacitySelector selector = getSelector();
        final List<NoddyVolume> volumes = VOLUME_LIST;
        for (int i = 0; i < 100; i++) {
            final NoddyVolume selectedVolume = selector.select(volumes);
            assertThat(selectedVolume).isNotNull();
            if (validExpectedVolPaths != null && validExpectedVolPaths.length > 0) {
                assertThat(selectedVolume.getPath())
                        .isIn((Object[]) validExpectedVolPaths);
            }
        }
    }

    protected NoddyVolume testOnce(final HasCapacitySelector volumeSelector,
                                   final String... validExpectedVolPaths) {
        final List<NoddyVolume> volumes = VOLUME_LIST;
        final NoddyVolume selectedVolume = volumeSelector.select(volumes);
        assertThat(selectedVolume).isNotNull();
        if (validExpectedVolPaths != null && validExpectedVolPaths.length > 0) {
            assertThat(selectedVolume.getPath())
                    .isIn((Object[]) validExpectedVolPaths);
        }
        return selectedVolume;
    }

    protected static class NoddyVolume implements HasCapacity {

        private final String path;
        private final Long used;
        private final Long limit;
        private final Long total;

        public NoddyVolume(final String path,
                           final long used,
                           final long total) {
            this(path, used, null, total);
        }

        public NoddyVolume(final String path,
                           final Long used,
                           final Long limit,
                           final Long total) {
            this.path = path;
            this.used = used;
            this.limit = limit;
            this.total = total;
        }

        public String getPath() {
            return path;
        }

        @Override
        public HasCapacityInfo getCapacityInfo() {
            return new HasCapacityInfo() {

                @Override
                public OptionalLong getCapacityUsedBytes() {
                    return used != null
                            ? OptionalLong.of(used)
                            : OptionalLong.empty();
                }

                @Override
                public OptionalLong getCapacityLimitBytes() {
                    return limit != null
                            ? OptionalLong.of(limit)
                            : OptionalLong.empty();
                }

                @Override
                public OptionalLong getTotalCapacityBytes() {
                    return total != null
                            ? OptionalLong.of(total)
                            : OptionalLong.empty();
                }
            };
        }

        @Override
        public String getIdentifier() {
            return path;
        }
    }
}
