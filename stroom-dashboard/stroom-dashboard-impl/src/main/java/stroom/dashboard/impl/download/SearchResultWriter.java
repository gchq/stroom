/*
 * Copyright 2017 Crown Copyright
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
import stroom.query.api.v2.Field;
import stroom.query.api.v2.OffsetRange;
import stroom.query.api.v2.Row;
import stroom.query.api.v2.TableResult;
import stroom.query.api.v2.TableResultBuilder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

public class SearchResultWriter implements TableResultBuilder {

    private final SampleGenerator sampleGenerator;
    private final Target target;
    private List<Field> fields;
    private long rowCount;

    public SearchResultWriter(final SampleGenerator sampleGenerator,
                              final Target target) throws IOException {
        this.sampleGenerator = sampleGenerator;
        this.target = target;
    }

    @Override
    public TableResultBuilder componentId(final String componentId) {
        return this;
    }

    @Override
    public TableResultBuilder fields(final List<Field> fields) {
        this.fields = fields;

        // Write heading.
        try {
            target.startLine();

            int fieldIndex = 0;
            for (final Field field : fields) {
                if (field.isVisible()) {
                    target.writeHeading(fieldIndex, field, field.getName());
                }

                fieldIndex++;
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
                    for (int i = 0; i < fields.size(); i++) {
                        final Field field = fields.get(i);
                        if (field.isVisible()) {
                            final String val = row.getValues().get(i);
                            target.writeValue(field, val);
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

    @Override
    public TableResultBuilder errors(final List<String> errors) {
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

        void writeHeading(int fieldIndex, Field field, String heading) throws IOException;

        void writeValue(Field field, String value) throws IOException;
    }
}
