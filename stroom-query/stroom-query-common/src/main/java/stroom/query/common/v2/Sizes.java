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

import java.text.ParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * Class for describing the maximum number of items to hold in a store at each level of grouping.
 * e.g. 100,10,1 means hold 100 items at group level 0, 10 for each group level 1 and 1 for
 * each group level 2
 */
public class Sizes {

    public static final long MAX_SIZE = Long.MAX_VALUE;
    private static final Sizes UNLIMITED = create(MAX_SIZE);

    private final long[] sizes;
    private final long defaultSize;

    private Sizes(final long[] sizes, final long defaultSize) {
        this.sizes = sizes;
        this.defaultSize = defaultSize;
    }

    public static Sizes unlimited() {
        return UNLIMITED;
    }

    /**
     * Create a set of sizes based on a single default size.
     *
     * @param defaultSize The default size to return if needed.
     * @return A new set of sizes.
     */
    public static Sizes create(final long defaultSize) {
        return new Sizes(new long[0], defaultSize);
    }

    /**
     * Create a set of sizes based on a list of longs. The default size will be derived from the last item in
     * the list or will default to 1.
     *
     * @param list The list of sizes to use.
     * @return A new set of sizes.
     */
    public static Sizes create(final List<Long> list) {
        if (list != null && !list.isEmpty()) {
            // If the list has some values the set the default size to the last value in the list.
            return create(list, list.getLast());
        }
        return unlimited();
    }

    /**
     * Create a set of sizes based on a list of longs. Where a size is not provided for a requested depth the
     * supplied default size will be used.
     *
     * @param list        The list of sizes to use.
     * @param defaultSize The default size to return if needed.
     * @return A new set of sizes.
     */
    public static Sizes create(final List<Long> list, final long defaultSize) {
        long[] sizes = new long[0];
        if (list != null) {
            sizes = list.stream().mapToLong(i -> Objects.requireNonNullElse(i, defaultSize)).toArray();
        }

        return new Sizes(sizes, defaultSize);
    }

    public static Sizes parse(final String value) throws ParseException {
        if (value != null) {
            try {
                return Sizes.create(Arrays.stream(value.split(","))
                        .map(String::trim)
                        .map(Long::valueOf)
                        .collect(Collectors.toList()));
            } catch (final RuntimeException e) {
                throw new ParseException(e.getMessage(), 0);
            }
        }
        return Sizes.create(Long.MAX_VALUE);
    }

    public static Sizes max(final Sizes s1, final Sizes s2) {
        return combine(s1, s2, Math::max);
    }

    public static Sizes min(final Sizes s1, final Sizes s2) {
        return combine(s1, s2, Math::min);
    }

    private static Sizes combine(
            final Sizes s1,
            final Sizes s2,
            final BiFunction<Long, Long, Long> function) {

        if (s1 == null) {
            return s2;
        } else if (s2 == null) {
            return s1;
        }

        final int length = Math.max(s1.sizes.length, s2.sizes.length);
        final long[] combinedSizes = new long[length];
        for (int i = 0; i < combinedSizes.length; i++) {
            final long v1 = s1.size(i);
            final long v2 = s2.size(i);
            combinedSizes[i] = function.apply(v1, v2);
        }
        final long combinedDefaultSize = function.apply(s1.defaultSize, s2.defaultSize);
        return new Sizes(combinedSizes, combinedDefaultSize);
    }

    public long size(final int depth) {
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
