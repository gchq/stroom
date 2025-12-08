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

package stroom.util;

import stroom.util.shared.NullSafe;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Utility methods relating to randomness.
 */
public final class RandomUtil {

    private RandomUtil() {
    }

    /**
     * Gets a random item from the passed arr.
     * <p>
     * Uses {@link ThreadLocalRandom} to provide the randomness.
     * </p>
     *
     * @param arr The array to get an item from.
     * @return A random item from arr or null if arr is empty or null.
     */
    public static <T> T getRandomItem(final T[] arr) {
        if (NullSafe.isEmptyArray(arr)) {
            return null;
        } else {
            final int len = arr.length;
            if (len == 1) {
                return arr[0];
            } else {
                final int idx = ThreadLocalRandom.current().nextInt(len);
                return arr[idx];
            }
        }
    }

    /**
     * Gets a random item from the passed list.
     * <p>
     * Uses {@link ThreadLocalRandom} to provide the randomness.
     * </p>
     *
     * @param list The list to get an item from.
     * @return A random item from list or null if list is empty or null.
     */
    public static <T> T getRandomItem(final List<T> list) {
        if (NullSafe.isEmptyCollection(list)) {
            return null;
        } else {
            final int len = list.size();
            if (len == 1) {
                return list.getFirst();
            } else {
                final int idx = ThreadLocalRandom.current().nextInt(len);
                return list.get(idx);
            }
        }
    }
}
