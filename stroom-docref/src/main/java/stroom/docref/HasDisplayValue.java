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

package stroom.docref;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * <p>
 * Used by classes that have some form of name or value that can be displayed as a String
 * </p>
 */
public interface HasDisplayValue {

    /**
     * @return The string label/description of this object.
     */
    String getDisplayValue();

    /// @return collection sorted by each item's display value
    static <T extends HasDisplayValue> List<T> sortedByDisplayValue(final Collection<T> collection) {
        if (collection == null || collection.isEmpty()) {
            return List.of();
        } else {
            return collection.stream()
                    .sorted(Comparator.comparing(HasDisplayValue::getDisplayValue))
                    .toList();
        }
    }

    /// @return collection sorted by each item's display value
    static <T extends HasDisplayValue> List<T> sortedByDisplayValue(final T... items) {
        if (items == null || items.length == 0) {
            return List.of();
        } else {
            return Arrays.stream(items)
                    .sorted(Comparator.comparing(HasDisplayValue::getDisplayValue))
                    .toList();
        }
    }
}
