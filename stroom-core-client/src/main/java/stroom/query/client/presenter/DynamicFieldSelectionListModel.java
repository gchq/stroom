package stroom.query.client.presenter;

import stroom.datasource.api.v2.FindFieldCriteria;
import stroom.datasource.api.v2.QueryField;
import stroom.docref.DocRef;
import stroom.docref.StringMatch;
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
    private DocRef dataSourceRef;
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
        if (dataSourceRef != null) {
            final StringMatch stringMatch = StringMatch.containsIgnoreCase(filter);
            final FindFieldCriteria findFieldInfoCriteria = new FindFieldCriteria(
                    pageRequest,
                    null,
                    dataSourceRef,
                    stringMatch,
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
        }
    }

    public void setDataSourceRef(final DocRef dataSourceRef) {
        this.dataSourceRef = dataSourceRef;
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
        dataSourceClient.findFieldByName(dataSourceRef, fieldName, queryable, consumer, taskMonitorFactory);
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
