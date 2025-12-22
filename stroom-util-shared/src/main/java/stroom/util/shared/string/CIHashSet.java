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

package stroom.util.shared.string;

import stroom.util.shared.NullSafe;

import java.util.Collection;
import java.util.HashSet;
import java.util.stream.Collectors;

/**
 * A {@link HashSet} containing case-insensitive {@link CIKey} string values.
 */
@SuppressWarnings("checkstyle:IllegalType")
public class CIHashSet extends HashSet<CIKey> {

    public CIHashSet(final Collection<String> collection) {
        super(NullSafe.stream(collection)
                .map(CIKey::of)
                .collect(Collectors.toList()));
    }

    public boolean add(final String value) {
        return super.add(CIKey.of(value));
    }

    /**
     * Remove an item matching value (case-insensitive).
     */
    public boolean removeString(final String value) {
        return super.remove(CIKey.of(value));
    }

    /**
     * True if there is an item matching value (case-insensitive).
     */
    public boolean containsString(final String value) {
        return super.contains(CIKey.of(value));
    }
}
