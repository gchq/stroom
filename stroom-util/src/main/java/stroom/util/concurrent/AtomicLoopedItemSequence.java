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

package stroom.util.concurrent;

import java.util.List;
import java.util.Optional;

/**
 * Gets sequential items from the provided list, looping back to the beginning
 * once it hits the end.
 * Thread safe.
 */
public class AtomicLoopedItemSequence {

    private final AtomicLoopedIntegerSequence sequence = new AtomicLoopedIntegerSequence(
            Integer.MAX_VALUE);

    public static AtomicLoopedItemSequence create() {
        return new AtomicLoopedItemSequence();
    }

    /**
     * Gets sequential items from the provided list, looping back to the beginning
     * once it hits the end.
     *
     * @param list The list to get items from in sequential order.
     * @return The next item in the list or an empty {@link Optional} if there isn't one. If the
     * passed list is null or empty and empty {@link Optional} will be returned.
     */
    public <T> Optional<T> getNextItem(final List<T> list) {
        if (list == null) {
            return Optional.empty();
        } else {
            final int size = list.size();
            if (size == 0) {
                return Optional.empty();
            } else if (size == 1) {
                return Optional.ofNullable(list.get(0));
            } else {
                final int idx = sequence.getNext() % size;
                return Optional.ofNullable(list.get(idx));
            }
        }
    }
}
