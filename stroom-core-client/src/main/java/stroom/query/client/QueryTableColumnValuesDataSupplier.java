/*
 * Copyright 2025 Crown Copyright
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

package stroom.query.client;

import stroom.dashboard.shared.ColumnValues;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.query.api.ConditionalFormattingRule;
import stroom.query.api.OffsetRange;
import stroom.query.api.QueryKey;
import stroom.query.client.result.table.ColumnValuesDataSupplier;
import stroom.query.shared.QueryColumnValuesRequest;
import stroom.query.shared.QueryResource;
import stroom.query.shared.QuerySearchRequest;
import stroom.query.shared.QueryTablePreferences;
import stroom.util.shared.PageRequest;

import com.google.gwt.core.client.GWT;
import com.google.gwt.view.client.Range;

import java.util.List;
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
            final QueryTablePreferences queryTablePreferences,
            final List<ConditionalFormattingRule> conditionalFormattingRules) {
        super(column.copy().build(), conditionalFormattingRules);
        this.restFactory = restFactory;
        this.searchModel = searchModel;

        QuerySearchRequest querySearchRequest = null;
        final QueryKey queryKey = searchModel.getCurrentQueryKey();
        final QuerySearchRequest currentSearch = searchModel.getCurrentSearch();
        if (queryKey != null && currentSearch != null) {
            // The current search holds the preferences from when it started, so use the latest ones. They carry
            // the filters that decide which values are available.
            querySearchRequest = currentSearch
                    .copy()
                    .queryKey(queryKey)
                    .storeHistory(false)
                    .requestedRange(OffsetRange.UNBOUNDED)
                    .queryTablePreferences(queryTablePreferences)
                    .build();
        }
        searchRequest = querySearchRequest;
    }

    @Override
    public void exec(final Range range,
                     final Consumer<ColumnValues> dataConsumer,
                     final RestErrorHandler errorHandler) {
        if (searchRequest == null) {
            dataConsumer.accept(ColumnValues.empty());

        } else {
            final PageRequest pageRequest = new PageRequest(range.getStart(), range.getLength());
            final QueryColumnValuesRequest columnValuesRequest = new QueryColumnValuesRequest(
                    searchRequest,
                    getColumn(),
                    getNameFilter(),
                    pageRequest,
                    getConditionalFormattingRules());

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
