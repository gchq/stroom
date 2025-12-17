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

import stroom.query.api.Column;
import stroom.query.api.Format;
import stroom.query.api.Format.Type;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValComparators;
import stroom.util.shared.NullSafe;

import java.util.Comparator;
import java.util.Objects;

public class ComparatorFactory {

    private ComparatorFactory() {
    }

    public static Comparator<Val> create(final Column column) {
        Objects.requireNonNull(column);
        // We use case-insensitive comparators for sorting, but case-sensitive
        // ones for the boolean condition funcs like >, <, =, !=, etc.
        // Not sure this makes sense, but it is how it is.
        final Format.Type formatType = NullSafe.get(
                column,
                Column::getFormat,
                Format::getType);
        // Format type trumps commonReturnType, but we may not have either
        if (Type.TEXT == formatType) {
            // Pure string comparison, no comparing as doubles first.
            return ValComparators.AS_CASE_INSENSITIVE_STRING_COMPARATOR;
        } else if (Type.NUMBER == formatType) {
            return ValComparators.AS_DOUBLE_COMPARATOR;
        } else if (Type.DATE_TIME == formatType) {
            return ValComparators.AS_LONG_COMPARATOR;
        } else {
            // Even if we have numbers mixed in with words, we want the numbers
            // sorted numerically, i.e. 3, 20, 100, foo
            // rather than 100, 20, 3, foo
            return ValComparators.AS_DOUBLE_THEN_CASE_INSENSITIVE_STRING_COMPARATOR;
        }
    }
}
