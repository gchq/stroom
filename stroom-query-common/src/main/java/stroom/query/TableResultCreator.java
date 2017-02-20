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

package stroom.query;

import stroom.dashboard.expression.Generator;
import stroom.query.api.Field;
import stroom.query.api.OffsetRange;
import stroom.query.api.Result;
import stroom.query.api.ResultRequest;
import stroom.query.api.Row;
import stroom.query.api.TableResult;
import stroom.query.format.FieldFormatter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TableResultCreator implements ResultCreator {
    private final FieldFormatter fieldFormatter;
    private volatile List<Field> latestFields;

    public TableResultCreator(final FieldFormatter fieldFormatter) {
        this.fieldFormatter = fieldFormatter;
    }

    @Override
    public Result create(final Data data, final ResultRequest resultRequest) {
        final List<Row> resultList = new ArrayList<>();
        int offset = 0;
        int length = Integer.MAX_VALUE;
        int totalResults = 0;
        String error = null;

        try {
            final OffsetRange range = resultRequest.getRequestedRange();
            if (range != null) {
                offset = range.getOffset().intValue();
                length = range.getLength().intValue();
            }

            Set<String> openGroups = Collections.emptySet();
            if (resultRequest.getOpenGroups() != null) {
                openGroups = new HashSet<>(resultRequest.getOpenGroups());
            }

            latestFields = resultRequest.getTableSettings().get(0).getFields();
            totalResults = addTableResults(data, latestFields, offset, length, openGroups, resultList, null, 0,
                    0);
        } catch (final Exception e) {
            error = e.getMessage();
        }

        final TableResult tableResult = new TableResult(resultRequest.getComponentId());
        tableResult.setRows(resultList);
        tableResult.setResultRange(new OffsetRange(offset, resultList.size()));
        tableResult.setTotalResults(totalResults);
        tableResult.setError(error);

        return tableResult;
    }

    private int addTableResults(final Data data, final List<Field> fields, final int offset,
                                final int length, final Set<String> openGroups, final List<Row> resultList, final Key parentKey,
                                final int depth, final int position) {
        int pos = position;
        // Get top level items.
        final Items<Item> items = data.getChildMap().get(parentKey);
        if (items != null) {
            for (final Item item : items) {
                if (pos >= offset && resultList.size() < length) {
                    // Convert all list into fully resolved objects evaluating
                    // functions where necessary.
                    final List<String> values = new ArrayList<>(item.getValues().length);
                    int i = 0;

                    for (final Field field : fields) {
                        String string = null;

                        if (item.getValues().length > i) {
                            final Object o = item.getValues()[i];
                            if (o != null) {
                                // Convert all list into fully resolved
                                // objects evaluating functions where necessary.
                                Object val = o;
                                if (o instanceof Generator) {
                                    final Generator generator = (Generator) o;
                                    val = generator.eval();
                                }

                                if (val != null) {
                                    string = fieldFormatter.format(field, val);
                                }
                            }
                        }

                        values.add(string);

                        i++;
                    }

                    if (item.getKey() != null) {
                        resultList.add(new Row(item.getKey().toString(), values, item.getDepth()));
                    } else {
                        resultList.add(new Row(null, values, item.getDepth()));
                    }
                }

                // Increment the position.
                pos++;

                // Add child results if a node is open.
                if (item.getKey() != null && openGroups != null && openGroups.contains(item.getKey().toString())) {
                    pos = addTableResults(data, fields, offset, length, openGroups, resultList,
                            item.getKey(), depth + 1, pos);
                }
            }
        }
        return pos;
    }

    public List<Field> getFields() {
        return latestFields;
    }
}
