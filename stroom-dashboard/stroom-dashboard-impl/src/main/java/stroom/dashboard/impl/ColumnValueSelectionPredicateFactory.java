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

package stroom.dashboard.impl;

import stroom.query.api.ColumnValueSelection;
import stroom.query.common.v2.Item;
import stroom.query.language.functions.Val;
import stroom.util.shared.NullSafe;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class ColumnValueSelectionPredicateFactory {

    private ColumnValueSelectionPredicateFactory() {
        // No construction needed.
    }

    public static Predicate<Item> create(final List<String> columnIdList,
                                         final Map<String, ColumnValueSelection> selections,
                                         final int primaryColumnIndex) {
        // Get selection indexes.
        if (!NullSafe.isEmptyMap(selections)) {
            final Map<Integer, ColumnValueSelection> selectionIndexes = new HashMap<>(selections.size());
            for (final Map.Entry<String, ColumnValueSelection> entry :
                    selections.entrySet()) {
                final int index = columnIdList.indexOf(entry.getKey());
                if (index != -1 && index != primaryColumnIndex) {
                    selectionIndexes.put(index, entry.getValue());
                }
            }

            return item -> {
                final long count = selectionIndexes
                        .entrySet()
                        .stream()
                        .filter(entry -> {
                            final Val selectionVal = item.getValue(entry.getKey());
                            if (selectionVal == null) {
                                return true;
                            }
                            final boolean contains = entry
                                    .getValue()
                                    .getValues()
                                    .contains(selectionVal.toString());
                            return entry.getValue().isInvert() != contains;
                        }).count();

                // If all selections are ok then include.
                return selectionIndexes.size() == count;
            };
        }

        return item -> true;
    }
}
