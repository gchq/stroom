package stroom.query.client.presenter;

import stroom.datasource.api.v2.FindFieldCriteria;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.docref.StringMatch;
import stroom.item.client.SelectionList;
import stroom.item.client.SelectionListModel;
import stroom.query.shared.QueryHelpRequest;
import stroom.query.shared.QueryHelpRow;
import stroom.query.shared.QueryHelpType;
import stroom.query.shared.QueryResource;
import stroom.task.client.HasTaskListener;
import stroom.task.client.TaskListener;
import stroom.task.client.TaskListenerImpl;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;
import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.inject.Inject;

public class DynamicQueryHelpSelectionListModel
        implements SelectionListModel<QueryHelpRow, QueryHelpSelectionItem>, HasTaskListener, HasHandlers {

    private static final QueryResource QUERY_RESOURCE = GWT.create(QueryResource.class);

    private static final String NONE_TITLE = "[ none ]";

    private final EventBus eventBus;
    private final RestFactory restFactory;
    private final TaskListenerImpl taskListener = new TaskListenerImpl(this);

    private DocRef dataSourceRef;
    private String query;
    private boolean showAll = true;
    private QueryHelpRequest lastRequest;
    private SelectionList<QueryHelpRow, QueryHelpSelectionItem> selectionList;

    @Inject
    public DynamicQueryHelpSelectionListModel(final EventBus eventBus,
                                              final RestFactory restFactory) {
        this.eventBus = eventBus;
        this.restFactory = restFactory;
    }

    public void setSelectionList(final SelectionList<QueryHelpRow, QueryHelpSelectionItem> selectionList) {
        this.selectionList = selectionList;
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

        final StringMatch stringMatch = StringMatch.containsIgnoreCase(filter);
        final CriteriaFieldSort sort = new CriteriaFieldSort(
                FindFieldCriteria.SORT_BY_NAME,
                false,
                true);
        final QueryHelpRequest request = new QueryHelpRequest(
                pageRequest,
                Collections.singletonList(sort),
                query,
                dataSourceRef,
                parentId,
                stringMatch,
                showAll);

        // Only fetch if the request has changed.
        if (!request.equals(lastRequest)) {
            lastRequest = request;

            restFactory
                    .create(QUERY_RESOURCE)
                    .method(res -> res.fetchQueryHelpItems(request))
                    .onSuccess(response -> {
                        // Only update if the request is still current.
                        if (request == lastRequest) {
                            final ResultPage<QueryHelpSelectionItem> resultPage;
                            if (response.getValues().size() > 0) {
                                List<QueryHelpSelectionItem> items = response
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
                                resultPage = new ResultPage<>(rows, new PageResponse(0, 1, 1L, true));
                            }

                            consumer.accept(resultPage);
                        }
                    })
                    .taskListener(taskListener)
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

    public void setShowAll(final boolean showAll) {
        this.showAll = showAll;
    }

    @Override
    public void setTaskListener(final TaskListener taskListener) {
        this.taskListener.setTaskListener(taskListener);
    }

    @Override
    public void fireEvent(final GwtEvent<?> gwtEvent) {
        eventBus.fireEvent(gwtEvent);
    }
}
