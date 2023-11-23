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

import stroom.query.api.v2.Column;
import stroom.query.api.v2.OffsetRange;
import stroom.query.api.v2.Result;
import stroom.query.api.v2.ResultRequest;
import stroom.query.api.v2.ResultRequest.Fetch;
import stroom.query.api.v2.Row;
import stroom.query.api.v2.TableResult;
import stroom.query.api.v2.TableResultBuilder;
import stroom.query.api.v2.TableSettings;
import stroom.query.common.v2.format.ColumnFormatter;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class TableResultCreator implements ResultCreator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TableResultCreator.class);

    private final ColumnFormatter columnFormatter;

    private final ErrorConsumer errorConsumer = new ErrorConsumerImpl();
    private final boolean cacheLastResult;
    private TableResult lastResult;

    public TableResultCreator(final ColumnFormatter columnFormatter) {
        this(columnFormatter, false);
    }

    public TableResultCreator(final ColumnFormatter columnFormatter,
                              final boolean cacheLastResult) {
        this.columnFormatter = columnFormatter;
        this.cacheLastResult = cacheLastResult;
    }

    public TableResultBuilder createTableResultBuilder() {
        return TableResult.builder();
    }

    @Override
    public Result create(final DataStore dataStore, final ResultRequest resultRequest) {
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
            final List<Column> columns = dataStore.getColumns();
            TableSettings tableSettings = resultRequest.getMappings().get(0);

            resultBuilder.columns(columns);

            // Create the row creator.
            Optional<ItemMapper<Row>> optionalRowCreator = Optional.empty();
            if (tableSettings != null) {
                optionalRowCreator = ConditionalFormattingRowCreator.create(
                        columnFormatter,
                        keyFactory,
                        tableSettings.getAggregateFilter(),
                        tableSettings.getConditionalFormattingRules(),
                        columns,
                        errorConsumer);
                if (optionalRowCreator.isEmpty()) {
                    optionalRowCreator = FilteredRowCreator.create(
                            columnFormatter,
                            keyFactory,
                            tableSettings.getAggregateFilter(),
                            columns,
                            errorConsumer);
                }
            }

            if (optionalRowCreator.isEmpty()) {
                optionalRowCreator = SimpleRowCreator.create(columnFormatter, keyFactory, errorConsumer);
            }

            final ItemMapper<Row> rowCreator = optionalRowCreator.orElse(null);
            final Set<Key> openGroups = keyFactory.decodeSet(resultRequest.getOpenGroups());
            dataStore.fetch(
                    range,
                    new OpenGroupsImpl(openGroups),
                    resultRequest.getTimeFilter(),
                    rowCreator,
                    row -> {
                        resultBuilder.addRow(row);
                        pageLength.incrementAndGet();
                    },
                    resultBuilder::totalResults);
        } catch (final UncheckedInterruptedException e) {
            LOGGER.debug(e.getMessage(), e);
            errorConsumer.add(e);
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            errorConsumer.add(e);
        }

        long offset = 0;
        if (range != null) {
            offset = range.getOffset();
        }

        resultBuilder.componentId(resultRequest.getComponentId());
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
