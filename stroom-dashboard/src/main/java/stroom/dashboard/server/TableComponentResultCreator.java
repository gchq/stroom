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

package stroom.dashboard.server;

import stroom.dashboard.expression.v1.Generator;
import stroom.dashboard.server.format.FieldFormatter;
import stroom.dashboard.shared.Row;
import stroom.dashboard.shared.TableResult;
import stroom.dashboard.shared.TableResultRequest;
import stroom.query.Item;
import stroom.query.Items;
import stroom.query.ResultStore;
import stroom.query.shared.ComponentResultRequest;
import stroom.query.shared.Field;
import stroom.util.shared.OffsetRange;
import stroom.util.shared.SharedObject;
import stroom.util.shared.SharedString;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class TableComponentResultCreator implements ComponentResultCreator {
    private final FieldFormatter fieldFormatter;
    private volatile List<Field> latestFields;

    public TableComponentResultCreator(final FieldFormatter fieldFormatter) {
        this.fieldFormatter = fieldFormatter;
    }

    @Override
    public SharedObject create(final ResultStore resultStore, final ComponentResultRequest componentResultRequest) {
        final TableResultRequest resultRequest = (TableResultRequest) componentResultRequest;
        final List<Row> resultList = new ArrayList<Row>();
        int offset = 0;
        int length = 0;
        int totalResults = 0;
        String error = null;

        try {
            final OffsetRange<Integer> range = resultRequest.getRequestedRange();
            final Set<String> openGroups = resultRequest.getOpenGroups();
            offset = range.getOffset();
            length = range.getLength();
            latestFields = resultRequest.getTableSettings().getFields();
            totalResults = addTableResults(resultStore, latestFields, offset, length, openGroups, resultList, null, 0,
                    0);
        } catch (final Exception e) {
            error = e.getMessage();
        }

        final TableResult tableResult = new TableResult();
        tableResult.setRows(resultList);
        tableResult.setResultRange(new OffsetRange<Integer>(offset, resultList.size()));
        tableResult.setTotalResults(totalResults);
        tableResult.setError(error);

        return tableResult;
    }

    private int addTableResults(final ResultStore resultStore, final List<Field> fields, final int offset,
            final int length, final Set<String> openGroups, final List<Row> resultList, final String parentKey,
            final int depth, final int position) {
        int pos = position;
        // Get top level items.
        final Items<Item> items = resultStore.getChildMap().get(parentKey);
        if (items != null) {
            for (final Item item : items) {
                if (pos >= offset && resultList.size() < length) {
                    // Convert all values into fully resolved objects evaluating
                    // functions where necessary.
                    final SharedObject[] values = new SharedObject[item.getValues().length];
                    for (int i = 0; i < fields.size(); i++) {
                        final Field field = fields.get(i);

                        if (item.getValues().length > i) {
                            final Object o = item.getValues()[i];
                            if (o != null) {
                                // Convert all values into fully resolved
                                // objects evaluating functions where necessary.
                                Object val = o;
                                if (o instanceof Generator) {
                                    final Generator generator = (Generator) o;
                                    val = generator.eval();
                                }

                                if (val != null) {
                                    final String formatted = fieldFormatter.format(field, val);
                                    values[i] = SharedString.wrap(formatted);
                                }
                            }
                        }
                    }

                    resultList.add(new Row(item.getGroupKey(), values, item.getDepth()));
                }

                // Increment the position.
                pos++;

                // Add child results if a node is open.
                if (item.getGroupKey() != null && openGroups != null && openGroups.contains(item.getGroupKey())) {
                    pos = addTableResults(resultStore, fields, offset, length, openGroups, resultList,
                            item.getGroupKey(), depth + 1, pos);
                }
            }
        }
        return pos;
    }

    public List<Field> getFields() {
        return latestFields;
    }
}
