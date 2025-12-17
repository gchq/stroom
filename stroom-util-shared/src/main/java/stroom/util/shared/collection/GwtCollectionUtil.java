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

package stroom.util.shared.collection;

import stroom.util.shared.NullSafe;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class GwtCollectionUtil {

    private GwtCollectionUtil() {
    }

    /**
     * Return a {@link Set} containing items, with iteration order matching
     * the items in natural sort order. Useful in config classes where a HashSet would
     * result in an undefined order on serialisation.
     */
    public static <T> Set<T> asUnmodifiabledConsistentOrderSet(final T... items) {
        if (NullSafe.isEmptyArray(items)) {
            return Collections.emptySet();
        } else {
            return asUnmodifiabledConsistentOrderSet(NullSafe.asList(items));
        }
    }

    /**
     * Return a {@link Set} containing items, with iteration order matching
     * the items in natural sort order. Useful in config classes where a HashSet would
     * result in an undefined order on serialisation.
     */
    public static <T> Set<T> asUnmodifiabledConsistentOrderSet(final Set<T> items) {
        if (NullSafe.isEmptyCollection(items)) {
            return Collections.emptySet();
        } else {
            // Use a LinkedHashSet to ensure iteration order is consistent
            final Set<T> cleanedSet = NullSafe.stream(items)
                    .sorted() // Sort the items for consistent insert order
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            return Collections.unmodifiableSet(cleanedSet);
        }
    }

    /**
     * Return a {@link Set} containing items, with iteration order matching
     * the items in natural sort order. Useful in config classes where a HashSet would
     * result in an undefined order on serialisation.
     */
    public static <T> Set<T> asUnmodifiabledConsistentOrderSet(final List<T> items) {
        if (NullSafe.isEmptyCollection(items)) {
            return Collections.emptySet();
        } else {
            // Use a LinkedHashSet to ensure iteration order is consistent
            final Set<T> cleanedSet = NullSafe.stream(items)
                    .sorted() // Sort the items for consistent insert order
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            return Collections.unmodifiableSet(cleanedSet);
        }
    }
}
