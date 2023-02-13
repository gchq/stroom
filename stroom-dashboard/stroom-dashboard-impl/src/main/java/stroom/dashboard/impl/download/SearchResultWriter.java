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
import stroom.dashboard.shared.DashboardSearchRequest;
import stroom.dashboard.shared.TableResultRequest;
import stroom.query.api.v2.Field;
import stroom.query.api.v2.Row;
import stroom.query.api.v2.TableResult;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SearchResultWriter {

    private final DashboardSearchRequest searchRequest;
    private final List<TableResult> tableResults;
    private final SampleGenerator sampleGenerator;

    public SearchResultWriter(final DashboardSearchRequest searchRequest,
                              final List<TableResult> tableResults,
                              final SampleGenerator sampleGenerator) {
        this.searchRequest = searchRequest;
        this.tableResults = tableResults;
        this.sampleGenerator = sampleGenerator;
    }

    public void write(final Target target) throws IOException {
        // Start writing.
        target.start();

        // Map each component ID to its corresponding table request
        final Map<String, TableResultRequest> tableRequestMap = new HashMap<>();
        searchRequest.getComponentResultRequests().forEach(request -> {
            if (request instanceof final TableResultRequest tableResultRequest) {
                tableRequestMap.put(tableResultRequest.getComponentId(), tableResultRequest);
            }
        });

        for (final TableResult tableResult : tableResults) {
            final TableResultRequest tableResultRequest = tableRequestMap.get(tableResult.getComponentId());
            final List<Field> fields = tableResultRequest.getTableSettings().getFields();
            final List<Row> rows = tableResult.getRows();

            target.startTable(tableResultRequest.getTableName());

            // Write heading.
            writeHeadings(fields, target);

            // Write content.
            writeContent(rows, fields, sampleGenerator, target);

            target.endTable();
        }

        // End writing.
        target.end();
    }

    private void writeHeadings(final List<Field> fields, final Target target) throws IOException {
        target.startLine();

        int fieldIndex = 0;
        for (final Field field : fields) {
            if (field.isVisible()) {
                target.writeHeading(fieldIndex, field, field.getName());
            }

            fieldIndex++;
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

        void startTable(final String tableName);

        void endTable();

        void startLine() throws IOException;

        void endLine() throws IOException;

        void writeHeading(int fieldIndex, Field field, String heading) throws IOException;

        void writeValue(Field field, String value) throws IOException;
    }
}
