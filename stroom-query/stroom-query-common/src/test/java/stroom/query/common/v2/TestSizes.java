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

package stroom.query.common.v2;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestSizes {

    @Test
    void testSize_null() {
        test(null, 0, Sizes.MAX_SIZE);
        test(null, 1, Sizes.MAX_SIZE);
    }

    @Test
    void testSize_emptyList() {
        final List<Long> storeSizes = Collections.emptyList();
        test(storeSizes, 0, Sizes.MAX_SIZE);
        test(storeSizes, 1, Sizes.MAX_SIZE);
    }

    @Test
    void testSize_populatedList() {
        final List<Long> storeSizes = Arrays.asList(100L, 10L, 1L);
        test(storeSizes, 0, storeSizes.get(0));
        test(storeSizes, 1, storeSizes.get(1));
        test(storeSizes, 2, storeSizes.get(2));
        test(storeSizes, 3, storeSizes.get(2));
    }

    private void test(final List<Long> storeSizes, final int depth, final long expectedSize) {
        final Sizes storeSize = Sizes.create(storeSizes);
        assertThat(storeSize.size(depth)).isEqualTo(expectedSize);
    }

    @Test
    void testSize_bothNull() {
        testMin(null, null, 0, Sizes.MAX_SIZE);
        testMin(null, null, 1, Sizes.MAX_SIZE);
    }

    @Test
    void testSize_bothEmpty() {
        testMin(Collections.emptyList(), Collections.emptyList(), 0, Sizes.MAX_SIZE);
        testMin(Collections.emptyList(), Collections.emptyList(), 1, Sizes.MAX_SIZE);
    }

    @Test
    void testSize_userIsNull() {
        final List<Long> defaultSizes = Arrays.asList(100L, 10L, 1L);
        testMin(null, defaultSizes, 0, defaultSizes.get(0));
        testMin(null, defaultSizes, 1, defaultSizes.get(1));
        testMin(null, defaultSizes, 2, defaultSizes.get(2));
        testMin(null, defaultSizes, 3, defaultSizes.get(2));
    }

    @Test
    void testSize_defaultIsNull() {
        final List<Long> userSizes = Arrays.asList(100L, 10L, 1L);
        testMin(userSizes, null, 0, userSizes.get(0));
        testMin(userSizes, null, 1, userSizes.get(1));
        testMin(userSizes, null, 2, userSizes.get(2));
        testMin(userSizes, null, 3, userSizes.get(2));
    }

    @Test
    void testSize_bothSuppliedSameSize() {
        final List<Long> userSizes = Arrays.asList(100L, 10L, 1L);
        final List<Long> defaultSizes = Arrays.asList(2000L, 200L, 20L);

        //user sizes are all smaller so will use those
        testMin(userSizes, defaultSizes, 0, userSizes.get(0));
        testMin(userSizes, defaultSizes, 1, userSizes.get(1));
        testMin(userSizes, defaultSizes, 2, userSizes.get(2));
        testMin(userSizes, defaultSizes, 3, userSizes.get(2));
    }

    @Test
    void testMin_bothSuppliedListSizeMismatch() {
        final List<Long> userSizes = Collections.singletonList(100L);
        final List<Long> defaultSizes = Arrays.asList(2000L, 200L, 20L);

        //user sizes are all smaller so will use those
        testMin(userSizes, defaultSizes, 0, 100L);
        testMin(userSizes, defaultSizes, 1, 100L);
        testMin(userSizes, defaultSizes, 2, 20L);
        testMin(userSizes, defaultSizes, 3, 20L);
    }

    @Test
    void testMax_bothSuppliedListSizeMismatch() {
        final List<Long> userSizes = Collections.singletonList(100L);
        final List<Long> defaultSizes = Arrays.asList(2000L, 200L, 20L);

        //user sizes are all smaller so will use those
        testMax(userSizes, defaultSizes, 0, 2000L);
        testMax(userSizes, defaultSizes, 1, 200L);
        testMax(userSizes, defaultSizes, 2, 100L);
        testMax(userSizes, defaultSizes, 3, 100L);
    }

    private void testMin(final List<Long> userSizes,
                         final List<Long> defaultSizes,
                         final int depth,
                         final long expectedSize) {
        final Sizes maxResults = Sizes.min(Sizes.create(userSizes), Sizes.create(defaultSizes));
        assertThat(maxResults.size(depth)).isEqualTo(expectedSize);
    }

    private void testMax(final List<Long> userSizes,
                         final List<Long> defaultSizes,
                         final int depth,
                         final long expectedSize) {
        final Sizes maxResults = Sizes.max(Sizes.create(userSizes), Sizes.create(defaultSizes));
        assertThat(maxResults.size(depth)).isEqualTo(expectedSize);
    }
}
