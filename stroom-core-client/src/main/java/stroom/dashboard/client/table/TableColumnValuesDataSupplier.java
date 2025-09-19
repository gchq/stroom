package stroom.dashboard.client.table;

import stroom.dashboard.client.main.SearchModel;
import stroom.dashboard.shared.ColumnValues;
import stroom.dashboard.shared.ColumnValuesRequest;
import stroom.dashboard.shared.ComponentResultRequest;
import stroom.dashboard.shared.DashboardResource;
import stroom.dashboard.shared.DashboardSearchRequest;
import stroom.dashboard.shared.Search;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.dashboard.shared.TableResultRequest;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.query.api.ColumnValueSelection;
import stroom.query.api.ConditionalFormattingRule;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.OffsetRange;
import stroom.query.api.QueryKey;
import stroom.query.api.ResultRequest.Fetch;
import stroom.query.api.TableSettings;
import stroom.util.shared.PageRequest;
import stroom.util.shared.PageResponse;

import com.google.gwt.core.client.GWT;
import com.google.gwt.view.client.Range;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class TableColumnValuesDataSupplier  extends ColumnValuesDataSupplier {

    private static final DashboardResource DASHBOARD_RESOURCE = GWT.create(DashboardResource.class);

    private final RestFactory restFactory;
    private final SearchModel searchModel;
    private final DashboardSearchRequest searchRequest;

    public TableColumnValuesDataSupplier(
            final RestFactory restFactory,
            final SearchModel searchModel,
            final stroom.query.api.Column column,
            final TableSettings tableSettings,
            final DateTimeSettings dateTimeSettings,
            final String tableName,
            final List<ConditionalFormattingRule> conditionalFormattingRules) {
        super(column.copy().build(), conditionalFormattingRules);
        this.restFactory = restFactory;
        this.searchModel = searchModel;

        DashboardSearchRequest dashboardSearchRequest = null;
        if (searchModel != null) {
            final QueryKey queryKey = searchModel.getCurrentQueryKey();
            final Search currentSearch = searchModel.getCurrentSearch();
            if (queryKey != null && currentSearch != null) {
                final List<ComponentResultRequest> requests = new ArrayList<>();
                currentSearch.getComponentSettingsMap().entrySet()
                        .stream()
                        .filter(settings -> settings.getValue() instanceof TableComponentSettings)
                        .forEach(componentSettings -> requests.add(TableResultRequest
                                .builder()
                                .componentId(componentSettings.getKey())
                                .requestedRange(OffsetRange.UNBOUNDED)
                                .tableName(tableName)
                                .tableSettings(tableSettings)
                                .fetch(Fetch.ALL)
                                .build()));

                final Search search = Search
                        .builder()
                        .dataSourceRef(currentSearch.getDataSourceRef())
                        .expression(currentSearch.getExpression())
                        .componentSettingsMap(currentSearch.getComponentSettingsMap())
                        .params(currentSearch.getParams())
                        .timeRange(currentSearch.getTimeRange())
                        .incremental(true)
                        .queryInfo(currentSearch.getQueryInfo())
                        .build();

                dashboardSearchRequest = DashboardSearchRequest
                        .builder()
                        .searchRequestSource(searchModel.getSearchRequestSource())
                        .queryKey(queryKey)
                        .search(search)
                        .componentResultRequests(requests)
                        .dateTimeSettings(dateTimeSettings)
                        .build();
            }
        }

        searchRequest = dashboardSearchRequest;
    }

    @Override
    protected void exec(final Range range,
                        final Consumer<ColumnValues> dataConsumer,
                        final RestErrorHandler errorHandler,
                        final Map<String, ColumnValueSelection> selections) {
        if (searchRequest == null) {
            clear(dataConsumer);

        } else {
            final PageRequest pageRequest = new PageRequest(range.getStart(), range.getLength());
            final ColumnValuesRequest columnValuesRequest = new ColumnValuesRequest(
                    searchRequest,
                    getColumn(),
                    getNameFilter(),
                    pageRequest,
                    getConditionalFormattingRules(),
                    selections);

            restFactory
                    .create(DASHBOARD_RESOURCE)
                    .method(res -> res.getColumnValues(searchModel.getCurrentNode(),
                            columnValuesRequest))
                    .onSuccess(dataConsumer)
                    .onFailure(e -> clear(dataConsumer))
                    .taskMonitorFactory(getTaskMonitorFactory())
                    .exec();
        }
    }

    private void clear(final Consumer<ColumnValues> dataConsumer) {
        dataConsumer.accept(new ColumnValues(Collections.emptyList(), PageResponse.empty()));
    }
}