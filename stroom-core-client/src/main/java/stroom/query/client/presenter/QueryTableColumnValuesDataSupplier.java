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

package stroom.query.client.presenter;

import stroom.dashboard.client.table.ColumnValuesDataSupplier;
import stroom.dashboard.shared.ColumnValues;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.query.api.ColumnValueSelection;
import stroom.query.api.ConditionalFormattingRule;
import stroom.query.api.OffsetRange;
import stroom.query.api.QueryKey;
import stroom.query.shared.QueryColumnValuesRequest;
import stroom.query.shared.QueryResource;
import stroom.query.shared.QuerySearchRequest;
import stroom.util.shared.PageRequest;
import stroom.util.shared.PageResponse;

import com.google.gwt.core.client.GWT;
import com.google.gwt.view.client.Range;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class QueryTableColumnValuesDataSupplier extends ColumnValuesDataSupplier {

    private static final QueryResource QUERY_RESOURCE = GWT.create(QueryResource.class);

    private final RestFactory restFactory;
    private final QueryModel searchModel;
    private final QuerySearchRequest searchRequest;


    public QueryTableColumnValuesDataSupplier(
            final RestFactory restFactory,
            final QueryModel searchModel,
            final stroom.query.api.Column column,
            final List<ConditionalFormattingRule> conditionalFormattingRules) {
        super(column.copy().build(), conditionalFormattingRules);
        this.restFactory = restFactory;
        this.searchModel = searchModel;

        QuerySearchRequest querySearchRequest = null;
        final QueryKey queryKey = searchModel.getCurrentQueryKey();
        final QuerySearchRequest currentSearch = searchModel.getCurrentSearch();
        if (queryKey != null && currentSearch != null) {
            querySearchRequest = currentSearch
                    .copy()
                    .queryKey(queryKey)
                    .storeHistory(false)
                    .requestedRange(OffsetRange.UNBOUNDED)
                    .build();
        }
        searchRequest = querySearchRequest;
    }

    @Override
    protected void exec(final Range range,
                        final Consumer<ColumnValues> dataConsumer,
                        final RestErrorHandler errorHandler,
                        final Map<String, ColumnValueSelection> selections) {
        if (searchRequest == null) {
            dataConsumer.accept(new ColumnValues(Collections.emptyList(), PageResponse.empty()));

        } else {
            final PageRequest pageRequest = new PageRequest(range.getStart(), range.getLength());
            final QueryColumnValuesRequest columnValuesRequest = new QueryColumnValuesRequest(
                    searchRequest,
                    getColumn(),
                    getNameFilter(),
                    pageRequest,
                    getConditionalFormattingRules(),
                    selections);

            restFactory
                    .create(QUERY_RESOURCE)
                    .method(res -> res.getColumnValues(searchModel.getCurrentNode(),
                            columnValuesRequest))
                    .onSuccess(dataConsumer)
                    .taskMonitorFactory(getTaskMonitorFactory())
                    .exec();
        }
    }
}
