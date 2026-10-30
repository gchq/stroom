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

package stroom.floormap.client.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestFloorMapImageSizeCache {

    /// Regression test: the cache was an unbounded map, growing with every distinct image URL
    /// for as long as the tab stayed open.
    @Test
    void testTheCacheNeverHoldsMoreThanItsLimit() {
        final FloorMapImageSizeCache cache = new FloorMapImageSizeCache(3);
        for (int i = 0; i < 10; i++) {
            cache.put("/assets/doc/img" + i + ".png", new double[]{i, i});
        }

        assertThat(cache.size()).isEqualTo(3);
        assertThat(cache.get("/assets/doc/img9.png")).containsExactly(9, 9);
        assertThat(cache.get("/assets/doc/img0.png")).isNull();
    }

    @Test
    void testTheLeastRecentlyUsedEntryIsEvictedFirst() {
        final FloorMapImageSizeCache cache = new FloorMapImageSizeCache(2);
        cache.put("a", new double[]{1, 1});
        cache.put("b", new double[]{2, 2});
        // Reading "a" makes "b" the least recently used.
        assertThat(cache.get("a")).isNotNull();

        cache.put("c", new double[]{3, 3});

        assertThat(cache.get("a")).isNotNull();
        assertThat(cache.get("b")).isNull();
        assertThat(cache.get("c")).isNotNull();
    }

    @Test
    void testTheDefaultLimitIsUsedByTheNoArgumentConstructor() {
        final FloorMapImageSizeCache cache = new FloorMapImageSizeCache();
        for (int i = 0; i < FloorMapImageSizeCache.DEFAULT_MAX_ENTRIES + 10; i++) {
            cache.put("url" + i, new double[]{1, 1});
        }

        assertThat(cache.size()).isEqualTo(FloorMapImageSizeCache.DEFAULT_MAX_ENTRIES);
    }

    @Test
    void testALimitBelowOneIsRejected() {
        assertThatThrownBy(() -> new FloorMapImageSizeCache(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
