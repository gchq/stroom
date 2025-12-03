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

package stroom.analytics.impl;

import stroom.analytics.shared.DuplicateCheckRow;
import stroom.analytics.shared.DuplicateNotificationConfig;
import stroom.query.common.v2.CompiledColumn;
import stroom.query.common.v2.CompiledColumns;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.Values;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

class DuplicateCheckRowFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DuplicateCheckRowSerde.class);

    private final Function<Values, List<String>> function;
    private final List<Integer> selectedIndexes;
    private final List<String> columnNames;

    public DuplicateCheckRowFactory(final DuplicateNotificationConfig duplicateNotificationConfig,
                                    final CompiledColumns compiledColumns) {
        final List<String> allColumnNames = new ArrayList<>();
        final List<String> selectedColumnNames = new ArrayList<>();

        selectedIndexes = new ArrayList<>(compiledColumns.getCompiledColumns().length);
        boolean useSelectedIndexes = false;
        for (int i = 0; i < compiledColumns.getCompiledColumns().length; i++) {
            final CompiledColumn compiledColumn = compiledColumns.getCompiledColumns()[i];
            allColumnNames.add(compiledColumn.getColumn().getName());

            // If we are told to choose columns then add chosen columns.
            if (duplicateNotificationConfig.isChooseColumns()) {
                if (duplicateNotificationConfig.getColumnNames() != null &&
                    duplicateNotificationConfig.getColumnNames().contains(compiledColumn.getColumn().getName())) {
                    selectedColumnNames.add(compiledColumn.getColumn().getName());
                    selectedIndexes.add(i);
                }
                useSelectedIndexes = true;
            } else if (compiledColumn.getGroupDepth() >= 0) {
                // Treat grouped columns as selected columns if the user has not chosen specific columns.
                selectedColumnNames.add(compiledColumn.getColumn().getName());
                selectedIndexes.add(i);
                useSelectedIndexes = true;
            }
        }

        if (useSelectedIndexes) {
            this.columnNames = selectedColumnNames;
            function = item -> {
                final List<String> values = new ArrayList<>();
                for (final Integer index : selectedIndexes) {
                    final Val val = item.getValue(index);
                    final String str = NullSafe.getOrElse(val, Val::toString, "");
                    LOGGER.trace(() -> "Adding selected string (" + index + ") = " + str);
                    values.add(str);
                }
                LOGGER.trace(() -> "Selected row values = " + String.join(", ", values));
                return values;
            };
        } else {
            this.columnNames = allColumnNames;
            function = item -> {
                final List<String> values = new ArrayList<>();
                for (int i = 0; i < columnNames.size(); i++) {
                    final Val val = item.getValue(i);
                    final String str = NullSafe.getOrElse(val, Val::toString, "");
                    final int index = i;
                    LOGGER.trace(() -> "Adding selected string (" + index + ") = " + str);
                    values.add(str);
                }
                LOGGER.trace(() -> "Row values = " + String.join(", ", values));
                return values;
            };
        }
    }

    public List<String> getColumnNames() {
        return columnNames;
    }

    public DuplicateCheckRow createDuplicateCheckRow(final Values values) {
        final List<String> strings = function.apply(values);
        return new DuplicateCheckRow(strings);
    }
}
