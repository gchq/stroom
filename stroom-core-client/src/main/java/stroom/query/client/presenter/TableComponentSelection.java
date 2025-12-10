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

package stroom.query.client.presenter;

import stroom.dashboard.client.table.ComponentSelection;
import stroom.query.api.ColumnRef;
import stroom.query.api.Param;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TableComponentSelection implements ComponentSelection {

    private final List<ColumnRef> columns;
    private final TableRow tableRow;
    private final Map<String, String> values = new HashMap<>();

    private TableComponentSelection(final List<ColumnRef> columns,
                                    final TableRow tableRow) {
        this.columns = columns;
        this.tableRow = tableRow;

        for (final ColumnRef column : columns) {
            if (column.getId() != null) {
                final String value = tableRow.getText(column.getId());
                if (value != null) {
                    values.computeIfAbsent(column.getId(), k -> value);
                }
            }
        }
        for (final ColumnRef column : columns) {
            if (column.getName() != null) {
                final String value = tableRow.getText(column.getName());
                if (value != null) {
                    values.computeIfAbsent(column.getName(), k -> value);
                }
            }
        }
    }

    public static List<ComponentSelection> create(final List<ColumnRef> columns,
                                                  final List<TableRow> tableRows) {
        return NullSafe.list(tableRows)
                .stream()
                .map(tableRow -> new TableComponentSelection(columns, tableRow))
                .collect(Collectors.toList());
    }

    @Override
    public List<Param> getParams() {
        final List<Param> params = new ArrayList<>();
        for (final ColumnRef column : columns) {
            if (column.getId() != null) {
                final String value = tableRow.getText(column.getId());
                if (value != null) {
                    params.add(new Param(column.getName(), value));
                }
            }
        }
        return params;
    }

    @Override
    public String getParamValue(final String key) {
        return values.get(key);
    }
}
