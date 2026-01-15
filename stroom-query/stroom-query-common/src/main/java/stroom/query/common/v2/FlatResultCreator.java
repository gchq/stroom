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

package stroom.query.common.v2;

import stroom.query.api.Column;
import stroom.query.api.FlatResult;
import stroom.query.api.FlatResultBuilder;
import stroom.query.api.Format;
import stroom.query.api.Format.Type;
import stroom.query.api.OffsetRange;
import stroom.query.api.QueryKey;
import stroom.query.api.Result;
import stroom.query.api.ResultRequest;
import stroom.query.api.ResultRequest.Fetch;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchRequestSource;
import stroom.query.api.TableSettings;
import stroom.query.api.TimeFilter;
import stroom.query.common.v2.format.Formatter;
import stroom.query.common.v2.format.FormatterFactory;
import stroom.query.language.functions.ExpressionContext;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.Values;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class FlatResultCreator implements ResultCreator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FlatResultCreator.class);

    private final DataStoreFactory dataStoreFactory;
    private final SearchRequest searchRequest;
    private final String componentId;
    private final ExpressionContext expressionContext;
    private final Map<String, String> paramMap;
    private final FormatterFactory formatterFactory;
    private final ExpressionPredicateFactory expressionPredicateFactory;
    private final ErrorConsumer errorConsumer = new ErrorConsumerImpl();
    private final Sizes defaultMaxResultsSizes;
    private final boolean cacheLastResult;
    private FlatResult lastResult;

    public FlatResultCreator(final DataStoreFactory dataStoreFactory,
                             final SearchRequest searchRequest,
                             final String componentId,
                             final ExpressionContext expressionContext,
                             final Map<String, String> paramMap,
                             final FormatterFactory formatterFactory,
                             final ExpressionPredicateFactory expressionPredicateFactory,
                             final Sizes defaultMaxResultsSizes,
                             final boolean cacheLastResult) {
        this.dataStoreFactory = dataStoreFactory;
        this.searchRequest = searchRequest;
        this.componentId = componentId;
        this.expressionContext = expressionContext;
        this.paramMap = paramMap;
        this.formatterFactory = formatterFactory;
        this.expressionPredicateFactory = expressionPredicateFactory;
        this.defaultMaxResultsSizes = defaultMaxResultsSizes;
        this.cacheLastResult = cacheLastResult;
    }

    private List<Object> toNodeKey(final Map<Integer, List<Column>> groupColumns, final Key key) {
        if (key == null || key.getKeyParts().isEmpty()) {
            return null;
        }

        if (!key.isGrouped()) {
            return null;
        }

        int depth = 0;
        final List<Object> result = new ArrayList<>(key.getKeyParts().size());
        for (final KeyPart keyPart : key.getKeyParts()) {
            final Val[] values = keyPart.getGroupValues();

            if (values.length == 0) {
                result.add(null);
            } else if (values.length == 1) {
                final Val val = values[0];
                if (val == null) {
                    result.add(null);
                } else {
                    Column column = null;

                    final List<Column> columns = groupColumns.get(depth);
                    if (columns != null) {
                        column = columns.getFirst();
                    }

                    result.add(convert(column, val));
                }

            } else {
                final StringBuilder sb = new StringBuilder();
                for (final Val val : values) {
                    if (val != null) {
                        sb.append(val);
                    }
                    sb.append("|");
                }
                sb.setLength(sb.length() - 1);
                result.add(sb.toString());
            }

            depth++;
        }

        return result;
    }

    @Override
    public Result create(final DataStore dataStore,
                         final ResultRequest resultRequest) {
        final Fetch fetch = resultRequest.getFetch();
        if (Fetch.NONE.equals(fetch)) {
            return null;
        }

        // User may have added a vis pane but not defined the vis
        final List<TableSettings> tableSettings = resultRequest.getMappings()
                .stream()
                .filter(Objects::nonNull)
                .toList();

        final FlatResultBuilder resultBuilder = FlatResult.builder();
        if (!errorConsumer.hasErrors()) {
            try {
                // Map data.
                DataStore mappedDataStore = dataStore;
                if (tableSettings.size() > 1) {
                    for (int i = 0; i < tableSettings.size() - 1; i++) {
                        final TableSettings parent = tableSettings.get(i);
                        final TableSettings child = tableSettings.get(i + 1);

                        final Sizes maxResults;
                        if (child != null && child.getMaxResults() != null && !child.getMaxResults().isEmpty()) {
                            maxResults = Sizes.create(child.getMaxResults());
                        } else {
                            maxResults = defaultMaxResultsSizes;
                        }

                        final DataStoreSettings dataStoreSettings = DataStoreSettings
                                .createBasicSearchResultStoreSettings()
                                .copy()
                                .maxResults(maxResults)
                                .build();

                        final Mapper mapper = new Mapper(
                                dataStoreFactory,
                                dataStoreSettings,
                                searchRequest.getSearchRequestSource(),
                                expressionContext,
                                searchRequest.getKey(),
                                componentId,
                                parent,
                                child,
                                paramMap,
                                errorConsumer,
                                expressionPredicateFactory);
                        mappedDataStore = mapper.map(mappedDataStore, resultRequest.getTimeFilter());
                    }

                    final TableSettings child = tableSettings.getLast();
                    final List<Column> columns = child != null
                            ? child.getColumns()
                            : Collections.emptyList();


                    final Map<Integer, List<Column>> groupFields = new HashMap<>();
                    for (final Column column : columns) {
                        if (column.getGroup() != null) {
                            groupFields.computeIfAbsent(column.getGroup(), k ->
                                            new ArrayList<>())
                                    .add(column);
                        }
                    }

                    // Get top level items.
                    final Function<Val, Object>[] converters = new Function[mappedDataStore.getColumns().size()];
                    if (formatterFactory != null) {
                        final Formatter[] formatters = RowUtil.createFormatters(mappedDataStore.getColumns(),
                                formatterFactory);
                        for (int i = 0; i < converters.length; i++) {
                            final Column column = mappedDataStore.getColumns().get(i);
                            if (column.getFormat() == null) {
                                converters[i] = val -> convert(column, val);
                            } else {
                                final Formatter formatter = formatters[i];
                                converters[i] = formatter::format;
                            }
                        }
                    } else {
                        for (int i = 0; i < converters.length; i++) {
                            final Column column = mappedDataStore.getColumns().get(i);
                            converters[i] = val -> convert(column, val);
                        }
                    }

                    // Now fetch data.
                    mappedDataStore.fetch(
                            mappedDataStore.getColumns(),
                            resultRequest.getRequestedRange(),
                            OpenGroups.ALL,
                            resultRequest.getTimeFilter(),
                            IdentityItemMapper.INSTANCE,
                            item -> {
                                final List<Object> resultList = new ArrayList<>(columns.size() + 3);

                                final Key key = item.getKey();
                                if (key != null) {
                                    resultList.add(toNodeKey(groupFields, key.getParent()));
                                    resultList.add(toNodeKey(groupFields, key));
                                    resultList.add(key.getDepth());
                                } else {
                                    resultList.add(null);
                                    resultList.add(null);
                                    resultList.add(0);
                                }

                                // Convert all list into fully resolved objects evaluating
                                // functions where necessary.
                                int i = 0;
                                for (final Column col : columns) {
                                    final Val val = item.getValue(i);
                                    Object result = null;
                                    if (val != null) {
                                        Column column = col;

                                        // Ensure a column has a format if none explicitly set.
                                        if (val.type().isValue() && (column.getFormat() == null ||
                                                                     column.getFormat().getType() == Type.GENERAL)) {
                                            if (stroom.query.language.functions.Type.DATE.equals(val.type())) {
                                                column = column.copy().format(Format.DATE_TIME).build();
                                            } else if (val.type().isNumber()) {
                                                column = column.copy().format(Format.NUMBER).build();
                                            } else {
                                                column = column.copy().format(Format.TEXT).build();
                                            }

                                            columns.set(i, column);
                                        }

                                        // Convert all list into fully resolved
                                        // objects evaluating functions where necessary.
                                        result = converters[i].apply(val);
                                    }

                                    resultList.add(result);
                                    i++;
                                }

                                // Add the values.
                                resultBuilder.addValues(resultList);
                            },
                            resultBuilder::totalResults);

                    final List<Column> structure = new ArrayList<>();
                    structure.add(Column.builder().id(":ParentKey").name(":ParentKey").build());
                    structure.add(Column.builder().id(":Key").name(":Key").build());
                    structure.add(Column.builder().id(":Depth").name(":Depth").build());
                    structure.addAll(columns);

                    resultBuilder.structure(structure);

                } else {
                    LOGGER.debug(() -> LogUtil.message(
                            "Invalid non-null tableSettings count ({}) for search: {} and componentId: {}",
                            tableSettings.size(),
                            searchRequest.getKey(),
                            componentId));
                    errorConsumer.add(() -> LogUtil.message(
                            "Component with ID: '{}' has not been configured correctly so will not show any data.",
                            componentId));
                }

            } catch (final UncheckedInterruptedException e) {
                LOGGER.debug(e::getMessage, e);
            } catch (final Exception e) {
                LOGGER.error(() ->
                        LogUtil.message(
                                "Error creating result for resultRequest {}", resultRequest.getComponentId()), e);
                errorConsumer.add(e);
            }
        }

        resultBuilder
                .componentId(resultRequest.getComponentId())
                .errorMessages(errorConsumer.getErrorMessages());
        FlatResult result = resultBuilder.build();

        if (cacheLastResult) {
            if (Fetch.CHANGES.equals(fetch)) {
                // See if we have delivered an identical result before, so we
                // don't send more data to the client than we need to.
                if (result.equals(lastResult)) {
                    result = null;
                } else {
                    lastResult = result;
                }
            } else {
                lastResult = result;
            }
        }

        LOGGER.debug("Delivering {} for {}", result, resultRequest.getComponentId());
        return result;
    }

    private Object convert(final Column column, final Val val) {
        final Format format = NullSafe.getOrElse(column, Column::getFormat, Format.GENERAL);
        final Type type = NullSafe.getOrElse(format, Format::getType, Type.GENERAL);
        if (Type.NUMBER.equals(type) || Type.DATE_TIME.equals(type)) {
            return val.toDouble();
        } else if (Type.TEXT.equals(type)) {
            return val.toString();
        }

        return val.unwrap();
    }

    private static class Mapper {

        private final DataStoreFactory dataStoreFactory;
        private final DataStoreSettings dataStoreSettings;
        private final SearchRequestSource searchRequestSource;
        private final ExpressionContext expressionContext;
        private final ExpressionPredicateFactory expressionPredicateFactory;
        private final QueryKey queryKey;
        private final String componentId;
        private final TableSettings parent;
        private final TableSettings child;
        private final Map<String, String> paramMap;
        private final ErrorConsumer errorConsumer;
        private final FieldIndex childFieldIndex;
        private final int[] parentFieldIndices;

        Mapper(final DataStoreFactory dataStoreFactory,
               final DataStoreSettings dataStoreSettings,
               final SearchRequestSource searchRequestSource,
               final ExpressionContext expressionContext,
               final QueryKey queryKey,
               final String componentId,
               final TableSettings parent,
               final TableSettings child,
               final Map<String, String> paramMap,
               final ErrorConsumer errorConsumer,
               final ExpressionPredicateFactory expressionPredicateFactory) {
            this.dataStoreFactory = dataStoreFactory;
            this.dataStoreSettings = dataStoreSettings;
            this.searchRequestSource = searchRequestSource;
            this.expressionContext = expressionContext;
            this.expressionPredicateFactory = expressionPredicateFactory;
            this.queryKey = queryKey;
            this.componentId = componentId;
            this.parent = parent;
            this.child = child;
            this.paramMap = paramMap;
            this.errorConsumer = errorConsumer;

            // Parent fields are now table column names.
            final FieldIndex parentFieldIndex = new FieldIndex();
            for (final Column column : parent.getColumns()) {
                parentFieldIndex.create(column.getName());
            }

            // Extract child fields from expressions.
            childFieldIndex = new FieldIndex();
            final List<Column> childFields = child != null
                    ? child.getColumns()
                    : Collections.emptyList();
            CompiledColumns.create(expressionContext, childFields, childFieldIndex, paramMap);

            // Create the index mapping.
            parentFieldIndices = new int[childFieldIndex.size()];
            for (int i = 0; i < childFieldIndex.size(); i++) {
                final String childField = childFieldIndex.getField(i);
                final Integer parentIndex = parentFieldIndex.getPos(childField);
                parentFieldIndices[i] = Objects.requireNonNullElse(parentIndex, -1);
            }
        }

        public DataStore map(final DataStore dataStore,
                             final TimeFilter timeFilter) {
            // Create a set of max result sizes that are determined by the supplied max results or default to integer
            // max value.
            final DataStore childDataStore = dataStoreFactory.create(
                    expressionContext,
                    searchRequestSource,
                    queryKey,
                    componentId,
                    child,
                    childFieldIndex,
                    paramMap,
                    dataStoreSettings,
                    errorConsumer);

            // Apply filter to parent.
            final Optional<Predicate<Values>> filter = FilteredMapper.createValuesPredicate(
                    parent.getColumns(),
                    parent.applyValueFilters(),
                    parent.getAggregateFilter(),
                    dataStore.getDateTimeSettings(),
                    expressionPredicateFactory);
            final Consumer<Item> consumer = filter.map(predicate -> (Consumer<Item>) item -> {
                final List<Column> parentColumns = parent.getColumns();
                final Val[] parentValues = new Val[parentColumns.size()];
                for (int i = 0; i < parentValues.length; i++) {
                    // TODO : @66 Currently evaluating more values than will be needed as we don't know what is
                    //  needed by the filter.
                    parentValues[i] = item.getValue(i);
                }

                if (predicate.test(Values.of(parentValues))) {
                    final Val[] values = new Val[parentFieldIndices.length];
                    for (int i = 0; i < parentFieldIndices.length; i++) {
                        final int index = parentFieldIndices[i];
                        if (index != -1) {
                            // TODO : @66 Currently evaluating more values than will be needed.
                            values[i] = parentValues[index];
                        } else {
                            values[i] = ValNull.INSTANCE;
                        }
                    }
                    childDataStore.accept(Val.of(values));
                }
            }).orElseGet(() -> item -> {
                final Val[] values = new Val[parentFieldIndices.length];
                for (int i = 0; i < parentFieldIndices.length; i++) {
                    final int index = parentFieldIndices[i];
                    if (index != -1) {
                        // TODO : @66 Currently evaluating more values than will be needed.
                        final Val val = item.getValue(index);
                        values[i] = val;
                    } else {
                        values[i] = ValNull.INSTANCE;
                    }
                }
                childDataStore.accept(Val.of(values));
            });

            // Get top level items.
            // TODO : Add an option to get detail level items rather than root level items.
            dataStore.fetch(
                    parent.getColumns(),
                    OffsetRange.UNBOUNDED,
                    OpenGroups.NONE,
                    timeFilter,
                    IdentityItemMapper.INSTANCE,
                    consumer,
                    null);

            return childDataStore;
        }
    }
}
