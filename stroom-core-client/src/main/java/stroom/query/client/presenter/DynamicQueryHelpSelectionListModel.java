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

import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.item.client.SelectionListModel;
import stroom.query.api.datasource.IndexFieldFields;
import stroom.query.shared.QueryHelpRequest;
import stroom.query.shared.QueryHelpRow;
import stroom.query.shared.QueryHelpType;
import stroom.query.shared.QueryResource;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.task.client.HasTaskMonitorFactory;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.inject.Inject;

public class DynamicQueryHelpSelectionListModel
        implements SelectionListModel<QueryHelpRow, QueryHelpSelectionItem>, HasTaskMonitorFactory, HasHandlers {

    private static final QueryResource QUERY_RESOURCE = GWT.create(QueryResource.class);

    private static final String NONE_TITLE = "[ none ]";

    private final EventBus eventBus;
    private final RestFactory restFactory;
    private TaskMonitorFactory taskMonitorFactory = new DefaultTaskMonitorFactory(this);

    private DocRef dataSourceRef;
    private String query;
    private Set<QueryHelpType> includedTypes = QueryHelpType.ALL_TYPES;
    private QueryHelpRequest lastRequest;
    private ResultPage<QueryHelpRow> lastResponse;

    @Inject
    public DynamicQueryHelpSelectionListModel(final EventBus eventBus,
                                              final RestFactory restFactory) {
        this.eventBus = eventBus;
        this.restFactory = restFactory;
    }

    @Override
    public void onRangeChange(final QueryHelpSelectionItem parent,
                              final String filter,
                              final boolean filterChange,
                              final PageRequest pageRequest,
                              final Consumer<ResultPage<QueryHelpSelectionItem>> consumer) {
        final String parentId;
        if (parent != null) {
            parentId = unwrap(parent).getId() + ".";
        } else {
            parentId = "";
        }

        final CriteriaFieldSort sort = new CriteriaFieldSort(
                IndexFieldFields.NAME,
                false,
                true);
        final QueryHelpRequest request = new QueryHelpRequest(
                pageRequest,
                Collections.singletonList(sort),
                query,
                dataSourceRef,
                parentId,
                filter,
                includedTypes);

        // Only fetch if the request has changed.
        if (!request.equals(lastRequest)) {
            lastRequest = request;

            restFactory
                    .create(QUERY_RESOURCE)
                    .method(res -> res.fetchQueryHelpItems(request))
                    .onSuccess(response -> {
                        // Only update if the request is still current.
                        if (request == lastRequest && !Objects.equals(lastResponse, response)) {
                            lastResponse = response;

                            final ResultPage<QueryHelpSelectionItem> resultPage;
                            if (NullSafe.hasItems(response.getValues())) {
                                final List<QueryHelpSelectionItem> items = response
                                        .getValues()
                                        .stream()
                                        .map(this::wrap)
                                        .collect(Collectors.toList());

                                resultPage = new ResultPage<>(items, response.getPageResponse());
                            } else {
                                final List<QueryHelpSelectionItem> rows = Collections
                                        .singletonList(new QueryHelpSelectionItem(QueryHelpRow
                                                .builder()
                                                .type(QueryHelpType.TITLE)
                                                .id(parentId + "none")
                                                .title(NONE_TITLE)
                                                .build()));
                                resultPage = new ResultPage<>(
                                        rows,
                                        new PageResponse(0, 1, 1L, true));
                            }

                            consumer.accept(resultPage);
                        }
                    })
                    .taskMonitorFactory(taskMonitorFactory)
                    .exec();
        }
    }

    public void setQuery(final String query) {
        this.query = query;
    }

    @Override
    public void reset() {
        lastRequest = null;
    }

    @Override
    public boolean displayFilter() {
        return true;
    }

    @Override
    public boolean displayPath() {
        return true;
    }

    @Override
    public boolean displayPager() {
        return true;
    }

    @Override
    public QueryHelpSelectionItem wrap(final QueryHelpRow item) {
        return new QueryHelpSelectionItem(item);
    }

    @Override
    public QueryHelpRow unwrap(final QueryHelpSelectionItem selectionItem) {
        if (selectionItem == null) {
            return null;
        }
        return selectionItem.getQueryHelpRow();
    }

    @Override
    public boolean isEmptyItem(final QueryHelpSelectionItem selectionItem) {
        return NONE_TITLE.equals(selectionItem.getLabel());
    }

    public void setDataSourceRef(final DocRef dataSourceRef) {
        this.dataSourceRef = dataSourceRef;
    }

    public void setIncludedTypes(final Set<QueryHelpType> includedTypes) {
        this.includedTypes = includedTypes;
    }

    @Override
    public void setTaskMonitorFactory(final TaskMonitorFactory taskMonitorFactory) {
        this.taskMonitorFactory = taskMonitorFactory;
    }

    @Override
    public void fireEvent(final GwtEvent<?> gwtEvent) {
        eventBus.fireEvent(gwtEvent);
    }
}
