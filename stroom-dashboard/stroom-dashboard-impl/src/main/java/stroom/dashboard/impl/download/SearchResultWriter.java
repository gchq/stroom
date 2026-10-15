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

package stroom.dashboard.impl.download;

import stroom.dashboard.impl.SampleGenerator;
import stroom.query.api.Column;
import stroom.query.api.OffsetRange;
import stroom.query.api.Row;
import stroom.query.api.SpecialColumns;
import stroom.query.api.TableResult;
import stroom.query.api.TableResultBuilder;
import stroom.util.shared.ErrorMessage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class SearchResultWriter implements TableResultBuilder {

    // The names the stream and event ids are written under, when asked for.
    static final String STREAM_ID_HEADING = "StreamId";
    static final String EVENT_ID_HEADING = "EventId";

    private final SampleGenerator sampleGenerator;
    private final Target target;
    private final boolean includeSourceIds;
    private List<Column> columns;
    // The names of the visible columns, so a hidden id isn't written as well as a visible column of the same name.
    private Set<String> visibleNames = Set.of();
    private long rowCount;

    public SearchResultWriter(final SampleGenerator sampleGenerator,
                              final Target target) throws IOException {
        this(sampleGenerator, target, false);
    }

    /// @param includeSourceIds True to also write the hidden stream and event id columns (as
    ///                         [#STREAM_ID_HEADING] and [#EVENT_ID_HEADING]), so each row can be traced to the
    ///                         event it came from.
    public SearchResultWriter(final SampleGenerator sampleGenerator,
                              final Target target,
                              final boolean includeSourceIds) throws IOException {
        this.includeSourceIds = includeSourceIds;
        this.sampleGenerator = sampleGenerator;
        this.target = target;
    }

    @Override
    public TableResultBuilder componentId(final String componentId) {
        return this;
    }

    @Override
    public TableResultBuilder columns(final List<Column> columns) {
        this.columns = columns;
        this.visibleNames = columns.stream()
                .filter(Column::isVisible)
                .map(Column::getName)
                .collect(Collectors.toSet());

        // Write heading.
        try {
            target.startLine();

            int fieldIndex = 0;
            for (final Column column : columns) {
                if (isWritten(column)) {
                    target.writeHeading(fieldIndex, column, heading(column));
                    fieldIndex++;
                }
            }
            target.endLine();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }

        return this;
    }

    @Override
    public TableResultBuilder addRow(final Row row) {
        try {
            if (row.getDepth() == 0) {
                if (sampleGenerator.includeResult()) {
                    target.startLine();
                    for (int i = 0; i < columns.size(); i++) {
                        final Column column = columns.get(i);
                        if (isWritten(column)) {
                            final String val = row.getValues().get(i);
                            target.writeValue(column, val);
                        }
                    }
                    target.endLine();
                }
                rowCount++;
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }

    private boolean isWritten(final Column column) {
        return column.isVisible()
               || (includeSourceIds && isSourceId(column) && !visibleNames.contains(heading(column)));
    }

    private static boolean isSourceId(final Column column) {
        return SpecialColumns.RESERVED_STREAM_ID.equals(column.getId())
               || SpecialColumns.RESERVED_EVENT_ID.equals(column.getId());
    }

    private static String heading(final Column column) {
        if (!column.isVisible() && SpecialColumns.RESERVED_STREAM_ID.equals(column.getId())) {
            return STREAM_ID_HEADING;
        } else if (!column.isVisible() && SpecialColumns.RESERVED_EVENT_ID.equals(column.getId())) {
            return EVENT_ID_HEADING;
        }
        return column.getName();
    }

    @Override
    public TableResultBuilder errorMessages(final List<ErrorMessage> errorMessages) {
        return this;
    }

    @Override
    public TableResultBuilder resultRange(final OffsetRange resultRange) {
        return this;
    }

    @Override
    public TableResultBuilder totalResults(final Long totalResults) {
        return this;
    }

    @Override
    public TableResult build() {
        return null;
    }

    public long getRowCount() {
        return rowCount;
    }

    public interface Target {

        void start() throws IOException;

        void end() throws IOException;

        void startTable(final String tableName);

        void endTable();

        void startLine() throws IOException;

        void endLine() throws IOException;

        void writeHeading(int fieldIndex, Column column, String heading) throws IOException;

        void writeValue(Column column, String value) throws IOException;
    }
}
