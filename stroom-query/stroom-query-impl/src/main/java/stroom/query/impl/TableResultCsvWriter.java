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

package stroom.query.impl;

import stroom.dashboard.impl.download.CSVWriter;
import stroom.query.api.Column;
import stroom.query.api.TableResult;
import stroom.util.shared.NullSafe;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.function.Predicate;

public class TableResultCsvWriter {

    private final TableResult tableResult;

    public TableResultCsvWriter(final TableResult tableResult) {
        this.tableResult = tableResult;
    }

    private static final Predicate<Column> IS_VISIBLE_COLUMN = c -> c.isVisible() && !c.isSpecial();

    public String toCsv() {
        final StringWriter writer = new StringWriter();
        try (final CSVWriter csv = new CSVWriter(writer)) {
            final List<Column> columns = NullSafe.list(tableResult.getColumns());
            columns.stream()
                    .filter(IS_VISIBLE_COLUMN)
                    .map(Column::getName)
                    .forEach(value -> {
                        try {
                            csv.writeValue(NullSafe.string(value));
                        } catch (final IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
            csv.newLine();
            NullSafe.stream(tableResult.getRows()).forEach(row -> {
                try {
                    final List<String> values = NullSafe.list(row.getValues());
                    for (int i = 0; i < columns.size(); i++) {
                        if (IS_VISIBLE_COLUMN.test(columns.get(i))) {
                            csv.writeValue(NullSafe.string(values.get(i)));
                        }
                    }
                    csv.newLine();
                } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            return writer.toString();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
