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

import stroom.docref.DocRef;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.QueryField;
import stroom.query.client.DataSourceClient;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.task.client.HasTaskMonitorFactory;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.PageRequest;
import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.inject.Inject;

public class DynamicFieldSelectionListModel
        implements FieldSelectionListModel, HasTaskMonitorFactory, HasHandlers {

    private final EventBus eventBus;
    private final DataSourceClient dataSourceClient;
    private Consumer<Consumer<DocRef>> dataSourceRefConsumer;
    private Boolean queryable;
    private FindFieldCriteria lastCriteria;
    private TaskMonitorFactory taskMonitorFactory = new DefaultTaskMonitorFactory(this);

    @Inject
    public DynamicFieldSelectionListModel(final EventBus eventBus,
                                          final DataSourceClient dataSourceClient) {
        this.eventBus = eventBus;
        this.dataSourceClient = dataSourceClient;
    }

    @Override
    public void onRangeChange(final FieldInfoSelectionItem parent,
                              final String filter,
                              final boolean filterChange,
                              final PageRequest pageRequest,
                              final Consumer<ResultPage<FieldInfoSelectionItem>> consumer) {
        consumeDataSource(dataSourceRef -> {
            final FindFieldCriteria findFieldInfoCriteria = new FindFieldCriteria(
                    pageRequest,
                    FindFieldCriteria.DEFAULT_SORT_LIST,
                    dataSourceRef,
                    filter,
                    queryable);

            // Only fetch if the request has changed.
            if (!findFieldInfoCriteria.equals(lastCriteria)) {
                lastCriteria = findFieldInfoCriteria;
                dataSourceClient.findFields(findFieldInfoCriteria, response -> {
                    // Only update if the request is still current.
                    if (findFieldInfoCriteria == lastCriteria) {
                        final ResultPage<FieldInfoSelectionItem> resultPage;
                        if (response.getValues().size() > 0) {
                            final List<FieldInfoSelectionItem> items = response
                                    .getValues()
                                    .stream()
                                    .map(this::wrap)
                                    .collect(Collectors.toList());
                            resultPage = new ResultPage<>(items, response.getPageResponse());
                        } else {
                            // Create empty item.
                            final List<FieldInfoSelectionItem> items = Collections.singletonList(
                                    new FieldInfoSelectionItem(null));
                            resultPage = new ResultPage<>(items, new PageResponse(0, 1, 1L, true));
                        }

                        consumer.accept(resultPage);
                    }
                }, taskMonitorFactory);
            }
        });
    }

    public void setDataSourceRefConsumer(final Consumer<Consumer<DocRef>> dataSourceRefConsumer) {
        this.dataSourceRefConsumer = dataSourceRefConsumer;
    }

    public void setQueryable(final Boolean queryable) {
        this.queryable = queryable;
    }

    @Override
    public void reset() {
        lastCriteria = null;
    }

    @Override
    public void findFieldByName(final String fieldName, final Consumer<QueryField> consumer) {
        consumeDataSource(dataSourceRef ->
                dataSourceClient.findFieldByName(dataSourceRef, fieldName, queryable, consumer, taskMonitorFactory));
    }

    private void consumeDataSource(final Consumer<DocRef> dsc) {
        if (dataSourceRefConsumer != null) {
            dataSourceRefConsumer.accept(dsc);
        }
    }

    @Override
    public boolean displayFilter() {
        return true;
    }

    @Override
    public boolean displayPath() {
        return false;
    }

    @Override
    public boolean displayPager() {
        return true;
    }

    @Override
    public FieldInfoSelectionItem wrap(final QueryField item) {
        return new FieldInfoSelectionItem(item);
    }

    @Override
    public QueryField unwrap(final FieldInfoSelectionItem selectionItem) {
        if (selectionItem == null) {
            return null;
        }
        return selectionItem.getField();
    }

    @Override
    public boolean isEmptyItem(final FieldInfoSelectionItem selectionItem) {
        return unwrap(selectionItem) == null;
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
