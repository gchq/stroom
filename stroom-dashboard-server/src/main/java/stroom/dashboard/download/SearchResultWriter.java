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

package stroom.dashboard.download;

import stroom.dashboard.SampleGenerator;
import stroom.dashboard.shared.Field;
import stroom.query.api.v2.Row;

import java.io.IOException;
import java.util.List;

public class SearchResultWriter {
    private final List<Field> fields;
    private final List<Row> rows;
    private final SampleGenerator sampleGenerator;

    public SearchResultWriter(final List<Field> fields,
                              final List<Row> rows,
                              final SampleGenerator sampleGenerator) {
        this.fields = fields;
        this.rows = rows;
        this.sampleGenerator = sampleGenerator;
    }

    public void write(final Target target) throws IOException {
        // Start writing.
        target.start();

        // Write heading.
        writeHeadings(fields, target);

        // Write content.
        writeContent(rows, fields, sampleGenerator, target);

        // End writing.
        target.end();
    }

    private void writeHeadings(final List<Field> fields, final Target target) throws IOException {
        target.startLine();
        for (final Field field : fields) {
            if (field.isVisible()) {
                target.writeHeading(field, field.getName());
            }
        }
        target.endLine();
    }

    private void writeContent(final List<Row> rows, final List<Field> fields,
                              final SampleGenerator sampleGenerator, final Target target) throws IOException {
        for (final Row row : rows) {
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
            }
        }
    }

    public interface Target {
        void start() throws IOException;

        void end() throws IOException;

        void startLine() throws IOException;

        void endLine() throws IOException;

        void writeHeading(Field field, String heading) throws IOException;

        void writeValue(Field field, String value) throws IOException;
    }
}
