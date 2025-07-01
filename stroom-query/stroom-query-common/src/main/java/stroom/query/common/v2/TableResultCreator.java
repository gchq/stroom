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

import stroom.query.api.Column;
import stroom.query.api.OffsetRange;
import stroom.query.api.Result;
import stroom.query.api.ResultRequest;
import stroom.query.api.ResultRequest.Fetch;
import stroom.query.api.Row;
import stroom.query.api.TableResult;
import stroom.query.api.TableResultBuilder;
import stroom.query.api.TableSettings;
import stroom.query.common.v2.format.FormatterFactory;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class TableResultCreator implements ResultCreator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TableResultCreator.class);

    private final FormatterFactory formatterFactory;
    private final ExpressionPredicateFactory expressionPredicateFactory;
    private final AnnotationsPostProcessorFactory annotationsPostProcessorFactory;

    private final ErrorConsumer errorConsumer = new ErrorConsumerImpl();
    private final boolean cacheLastResult;
    private TableResult lastResult;

    public TableResultCreator() {
        this(new FormatterFactory(null),
                new ExpressionPredicateFactory(),
                AnnotationsPostProcessorFactory.NO_OP,
                false);
    }

    public TableResultCreator(final FormatterFactory formatterFactory,
                              final ExpressionPredicateFactory expressionPredicateFactory,
                              final AnnotationsPostProcessorFactory annotationsPostProcessorFactory) {
        this(formatterFactory, expressionPredicateFactory, annotationsPostProcessorFactory, false);
    }

    public TableResultCreator(final FormatterFactory formatterFactory,
                              final ExpressionPredicateFactory expressionPredicateFactory,
                              final AnnotationsPostProcessorFactory annotationsPostProcessorFactory,
                              final boolean cacheLastResult) {
        this.formatterFactory = formatterFactory;
        this.expressionPredicateFactory = expressionPredicateFactory;
        this.annotationsPostProcessorFactory = annotationsPostProcessorFactory;
        this.cacheLastResult = cacheLastResult;
    }

    public TableResultBuilder createTableResultBuilder() {
        return TableResult.builder();
    }

    @Override
    public Result create(final DataStore dataStore, final ResultRequest resultRequest) {
        errorConsumer.clear();

        final Fetch fetch = resultRequest.getFetch();
        if (Fetch.NONE.equals(fetch)) {
            return null;
        }

        final TableResultBuilder resultBuilder = createTableResultBuilder();
        final KeyFactory keyFactory = dataStore.getKeyFactory();
        final AtomicLong pageLength = new AtomicLong();
        final OffsetRange range = resultRequest.getRequestedRange();

        try {
            // What is the interaction between the paging and the maxResults? The assumption is that
            // maxResults defines the max number of records to come back and the paging can happen up to
            // that maxResults threshold
            final TableSettings tableSettings = resultRequest.getMappings().getFirst();
            final List<Column> columns = new ArrayList<>(NullSafe.list(
                    NullSafe.get(tableSettings, TableSettings::getColumns)));
            resultBuilder.columns(columns);

            if (RowValueFilter.matches(columns)) {
                // Create the row creator.
                final ItemMapper<Row> rowCreator = ConditionalFormattingRowCreator.create(
                        dataStore.getColumns(),
                        columns,
                        tableSettings.applyValueFilters(),
                        formatterFactory,
                        keyFactory,
                        tableSettings.getAggregateFilter(),
                        tableSettings.getConditionalFormattingRules(),
                        dataStore.getDateTimeSettings(),
                        expressionPredicateFactory,
                        errorConsumer,
                        annotationsPostProcessorFactory.createProcessor(columns));

                final Set<Key> openGroups = keyFactory.decodeSet(resultRequest.getOpenGroups());
                dataStore.fetch(
                        columns,
                        range,
                        new OpenGroupsImpl(openGroups),
                        resultRequest.getTimeFilter(),
                        rowCreator,
                        row -> {
                            resultBuilder.addRow(row);
                            pageLength.incrementAndGet();
                        },
                        resultBuilder::totalResults);
            }
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
            errorConsumer.add(e);
        }

        long offset = 0;
        if (range != null) {
            offset = range.getOffset();
        }

        resultBuilder.componentId(resultRequest.getComponentId());
        resultBuilder.errors(errorConsumer.getErrors());
        resultBuilder.resultRange(new OffsetRange(offset, pageLength.get()));
        TableResult result = resultBuilder.build();

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
}
