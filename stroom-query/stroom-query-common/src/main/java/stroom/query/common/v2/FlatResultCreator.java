/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.query.common.v2;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dashboard.expression.v1.GroupKey;
import stroom.dashboard.expression.v1.Val;
import stroom.query.api.v2.Field;
import stroom.query.api.v2.FlatResult;
import stroom.query.api.v2.Format.Type;
import stroom.query.api.v2.OffsetRange;
import stroom.query.api.v2.Result;
import stroom.query.api.v2.ResultRequest;
import stroom.query.api.v2.TableSettings;
import stroom.query.common.v2.Data.DataItem;
import stroom.query.common.v2.Data.DataItems;
import stroom.query.common.v2.format.FieldFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FlatResultCreator implements ResultCreator {
    private static final Logger LOGGER = LoggerFactory.getLogger(FlatResultCreator.class);
    private final FieldFormatter fieldFormatter;
    private final List<Mapper> mappers;
    private final List<Field> fields;

    private String error;

    public FlatResultCreator(final ResultRequest resultRequest,
                             final Map<String, String> paramMap,
                             final FieldFormatter fieldFormatter,
                             final Sizes defaultMaxResultsSizes) {

        this.fieldFormatter = fieldFormatter;

        final List<TableSettings> tableSettings = resultRequest.getMappings();

        if (tableSettings.size() > 1) {
            mappers = new ArrayList<>(tableSettings.size() - 1);
            for (int i = 0; i < tableSettings.size() - 1; i++) {
                final TableSettings parent = tableSettings.get(i);
                final TableSettings child = tableSettings.get(i + 1);

                // Create a set of sizes that are the minimum values for the combination of user provided sizes for the parent table and the default maximum sizes.
                final Sizes sizes = Sizes.min(Sizes.create(parent.getMaxResults()), defaultMaxResultsSizes);
                final int maxItems = sizes.size(0);

                mappers.add(new Mapper(parent, child, paramMap, maxItems));
            }
        } else {
            mappers = Collections.emptyList();
        }

        final TableSettings child = tableSettings.get(tableSettings.size() - 1);

        fields = child.getFields();
    }

    private List<Object> toNodeKey(final Map<Integer, List<Field>> groupFields, final GroupKey key) {
        if (key == null || key.getValues() == null) {
            return null;
        }

        final LinkedList<Object> result = new LinkedList<>();
        GroupKey k = key;
        while (k != null && k.getValues() != null) {
            final List<Field> fields = groupFields.get(k.getDepth());
            final Val[] values = k.getValues();

            if (values.length == 0) {
                result.addFirst(null);
            } else if (values.length == 1) {
                final Val val = values[0];
                if (val == null) {
                    result.addFirst(null);
                } else {
                    Field field = null;
                    if (fields != null) {
                        field = fields.get(0);
                    }

                    result.addFirst(convert(field, val));
                }

            } else {
                final StringBuilder sb = new StringBuilder();
                for (Val val : values) {
                    if (val != null) {
                        sb.append(val);
                    }
                    sb.append("|");
                }
                sb.setLength(sb.length() - 1);
                result.addFirst(sb.toString());
            }

            k = k.getParent();
        }

        return result;
    }

    @Override
    public Result create(final Data data, final ResultRequest resultRequest) {
        if (error == null) {
            try {
                // Map data.
                Data mappedData = data;
                for (final Mapper mapper : mappers) {
                    mappedData = mapper.map(mappedData);
                }

                long totalResults = 0;

                // Get top level items.
                final DataItems items = mappedData.get();
                final List<List<Object>> results = new ArrayList<>(items.size());
                if (items.size() > 0) {
                    final RangeChecker rangeChecker = RangeCheckerFactory.create(resultRequest.getRequestedRange());
                    final OpenGroups openGroups = OpenGroupsFactory.create(resultRequest.getOpenGroups());

                    // Extract the maxResults settings from the last TableSettings object in the chain.
                    // Do not constrain the max results with the default max results as the result size will have already
                    // been constrained by the previous table mapping.
                    final List<TableSettings> mappings = resultRequest.getMappings();
                    final TableSettings tableSettings = mappings.get(mappings.size() - 1);
                    // Create a set of max result sizes that are determined by the supplied max results or default to integer max value.
                    final Sizes maxResults = Sizes.create(tableSettings.getMaxResults(), Integer.MAX_VALUE);

                    totalResults = addResults(mappedData, rangeChecker, openGroups, items, results, 0,
                            0, maxResults);
                }

                final FlatResult.Builder resultBuilder = new FlatResult.Builder()
                        .componentId(resultRequest.getComponentId())
                        .size(totalResults)
                        .error(error)
                        .addField(new Field.Builder()
                                .name(":ParentKey")
                                .build())
                        .addField(new Field.Builder()
                                .name(":Key")
                                .build())
                        .addField(new Field.Builder()
                                .name(":Depth")
                                .build());
                this.fields.forEach(resultBuilder::addField);

                results.forEach(resultBuilder::addValues);

                return resultBuilder.build();

            } catch (final Exception e) {
                LOGGER.error("Error creating result for resultRequest {}", resultRequest.getComponentId(), e);
                error = e.getMessage();
            }
        }

        return new FlatResult(resultRequest.getComponentId(), null, null, 0L, error);
    }

    private int addResults(final Data data, final RangeChecker rangeChecker,
                           final OpenGroups openGroups, final DataItems items, final List<List<Object>> results,
                           final int depth, final int parentCount, final Sizes maxResults) {
        int count = parentCount;
        int maxResultsAtThisDepth = maxResults.size(depth);
        int resultCountAtThisLevel = 0;

        final Map<Integer, List<Field>> groupFields = new HashMap<>();
        for (final Field field : fields) {
            if (field.getGroup() != null) {
                groupFields.computeIfAbsent(field.getGroup(), k -> new ArrayList<>()).add(field);
            }
        }

        for (final DataItem item : items) {
            if (rangeChecker.check(count)) {
                final List<Object> resultList = new ArrayList<>(fields.size() + 3);

                if (item.getKey() != null) {
                    resultList.add(toNodeKey(groupFields, item.getKey().getParent()));
                    resultList.add(toNodeKey(groupFields, item.getKey()));
                } else {
                    resultList.add(null);
                    resultList.add(null);
                }
                resultList.add(depth);

                // Convert all list into fully resolved objects evaluating
                // functions where necessary.
                int i = 0;
                for (final Field field : fields) {
                    final Val val = item.getValue(i);
                    Object result = null;
                    if (val != null) {
                        // Convert all list into fully resolved
                        // objects evaluating functions where necessary.
                        if (fieldFormatter != null) {
                            result = fieldFormatter.format(field, val);
                        } else {
                            result = convert(field, val);
                        }
                    }

                    resultList.add(result);
                    i++;
                }

                // Add the values.
                results.add(resultList);
                resultCountAtThisLevel++;

                // Add child results if a node is open.
                if (item.getKey() != null && openGroups.isOpen(item.getKey())) {
                    final DataItems childItems = data.get(item.getKey());
                    if (childItems.size() > 0) {
                        count = addResults(data, rangeChecker, openGroups,
                                childItems, results, depth + 1, count, maxResults);
                    }
                }
            }

            // Increment the position.
            count++;

            if (resultCountAtThisLevel >= maxResultsAtThisDepth) {
                break;
            }
        }

        return count;
    }

    // TODO : Replace this with conversion at the item level.
    private Object convert(final Field field, final Val val) {
        if (field != null && field.getFormat() != null && field.getFormat().getType() != null) {
            final Type type = field.getFormat().getType();
            if (Type.NUMBER.equals(type) || Type.DATE_TIME.equals(type)) {
                return val.toDouble();
            }
        }

        return val.toString();
    }

    @FunctionalInterface
    private interface RangeChecker {
        boolean check(long count);
    }

    @FunctionalInterface
    private interface OpenGroups {
        boolean isOpen(GroupKey group);
    }

    private static class Mapper {
        private final int[] parentFieldIndices;
        private final TableDataStore tableDataStore;
        private final int maxItems;

        Mapper(final TableSettings parent,
               final TableSettings child,
               final Map<String, String> paramMap,
               final int maxItems) {
            this.maxItems = maxItems;

            // Create a set of max result sizes that are determined by the supplied max results or default to integer max value.
            final Sizes maxResults = Sizes.create(child.getMaxResults(), Integer.MAX_VALUE);

            final FieldIndex fieldIndex = new FieldIndex();

            final List<Field> fields = child.getFields();
            final CompiledDepths compiledDepths = new CompiledDepths(fields, child.showDetail());
            final CompiledFields compiledFields = new CompiledFields(fields, fieldIndex, paramMap);

            tableDataStore = new TableDataStore(
                    new TableCoprocessorSettings(null, child),
                    fieldIndex,
                    paramMap,
                    maxResults,
                    null);

            int i = 0;
            final Map<String, Integer> parentIndex = new HashMap<>();
            for (final Field field : parent.getFields()) {
                parentIndex.put(field.getName(), i++);
            }

            parentFieldIndices = new int[compiledFields.size()];
            Arrays.fill(parentFieldIndices, -1);
            for (i = 0; i < compiledFields.size(); i++) {
                final Integer index = parentIndex.get(compiledFields.getField(i).getField().getName());
                if (index != null) {
                    parentFieldIndices[i] = index;
                }
            }
        }

        public Data map(final Data data) {
            // Get top level items.
            // TODO : Add an option to get detail level items rather than root level items.
            final DataItems items = data.get();

            tableDataStore.clear();
            if (items.size() > 0) {
                int itemCount = 0;
                for (final DataItem item : items) {
                    final Val[] values = new Val[parentFieldIndices.length];
                    for (int i = 0; i < values.length; i++) {
                        final int index = parentFieldIndices[i];
                        if (index != -1) {
                            final Val val = item.getValue(index);
                            values[index] = val;
                        }
                    }
                    tableDataStore.add(values);

                    // Trim the data to the parent first level result size.
                    itemCount++;
                    if (itemCount >= maxItems) {
                        break;
                    }
                }
            }

            return tableDataStore.getData();
        }
    }

    private static class RangeCheckerFactory {
        public static RangeChecker create(final OffsetRange range) {
            if (range == null) {
                return count -> true;
            }

            final long start = range.getOffset();
            final long end = range.getOffset() + range.getLength();
            return count -> count >= start && count < end;
        }
    }

    private static class OpenGroupsFactory {
        public static OpenGroups create(final List<String> openGroups) {
            if (openGroups == null || openGroups.size() == 0) {
                return group -> true;
            }

            final Set<String> set = new HashSet<>(openGroups);
            return key -> key != null && set.contains(key.toString());
        }
    }
}