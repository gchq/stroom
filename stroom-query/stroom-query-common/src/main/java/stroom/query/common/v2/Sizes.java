/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.query.common.v2;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Class for describing the maximum number of items to hold in a store at each level of grouping.
 * e.g. 100,10,1 means hold 100 items at group level 0, 10 for each group level 1 and 1 for
 * each group level 2
 */
public class Sizes {
    static final int FALLBACK = Integer.MAX_VALUE;

    private final int[] sizes;
    private final int defaultSize;

    private Sizes(final int[] sizes, final int defaultSize) {
        this.sizes = sizes;
        this.defaultSize = defaultSize;
    }

    /**
     * Create a set of sizes based on a single default size.
     *
     * @param defaultSize The default size to return if needed.
     * @return A new set of sizes.
     */
    public static Sizes create(final int defaultSize) {
        return new Sizes(new int[0], defaultSize);
    }

    /**
     * Create a set of sizes based on a list of integers. The default size will be derived from the last item in the list or will default to 1.
     *
     * @param list The list of sizes to use.
     * @return A new set of sizes.
     */
    public static Sizes create(final List<Integer> list) {
        if (list != null && list.size() > 0) {
            // If the list has some values the set the default size to the last value in the list.
            return create(list, list.get(list.size() - 1));
        }
        return create(FALLBACK);
    }

    /**
     * Create a set of sizes based on a list of integers. Where a size is not provided for a requested depth the supplied default size will be used.
     *
     * @param list        The list of sizes to use.
     * @param defaultSize The default size to return if needed.
     * @return A new set of sizes.
     */
    public static Sizes create(final List<Integer> list, final int defaultSize) {
        int[] sizes = new int[0];
        if (list != null) {
            sizes = list.stream().mapToInt(i -> i).toArray();
        }

        return new Sizes(sizes, defaultSize);
    }

    public static Sizes max(final Sizes s1, final Sizes s2) {
        return combine(s1, s2, Math::max);
    }

    public static Sizes min(final Sizes s1, final Sizes s2) {
        return combine(s1, s2, Math::min);
    }

    private static Sizes combine(final Sizes s1, final Sizes s2, final BiFunction<Integer, Integer, Integer> function) {
        if (s1 == null) {
            return s2;
        } else if (s2 == null) {
            return s1;
        }

        final int length = Math.max(s1.sizes.length, s2.sizes.length);
        final int[] combinedSizes = new int[length];
        for (int i = 0; i < combinedSizes.length; i++) {
            final int v1 = s1.size(i);
            final int v2 = s2.size(i);
            combinedSizes[i] = function.apply(v1, v2);
        }
        final int combinedDefaultSize = function.apply(s1.defaultSize, s2.defaultSize);
        return new Sizes(combinedSizes, combinedDefaultSize);
    }

    public int size(final int depth) {
        if (depth < sizes.length) {
            return sizes[depth];
        }
        return defaultSize;
    }

    @Override
    public String toString() {
        return "StoreSize{" +
                "sizes=" + Arrays.toString(sizes) +
                ", defaultSize=" + defaultSize +
                '}';
    }
}
