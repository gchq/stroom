package stroom.security.client.presenter;

import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.preferences.client.DateTimeFormatter;
import stroom.security.shared.ApiKey;
import stroom.security.shared.ApiKeyResource;
import stroom.security.shared.ApiKeyResultPage;
import stroom.security.shared.FindApiKeyCriteria;
import stroom.task.shared.TaskProgress;
import stroom.util.client.DataGridUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public class ApiKeysListPresenter extends MyPresenterWidget<PagerView> {

    private static final ApiKeyResource API_KEY_RESOURCE = GWT.create(ApiKeyResource.class);

    private final FindApiKeyCriteria criteria = new FindApiKeyCriteria();
    private final RestFactory restFactory;
    private final DateTimeFormatter dateTimeFormatter;
    private final Set<Integer> selectedApiKeyIds = new HashSet<>();
    private final QuickFilterTimer timer = new QuickFilterTimer();
    private final RestDataProvider<ApiKey, ApiKeyResultPage> dataProvider;
    private final MyDataGrid<TaskProgress> dataGrid;

    private Range range;
    private Consumer<ApiKeyResultPage> dataConsumer;

    @Inject
    public ApiKeysListPresenter(final EventBus eventBus,
                                final PagerView view,
                                final RestFactory restFactory,
                                final DateTimeFormatter dateTimeFormatter) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.dateTimeFormatter = dateTimeFormatter;
        this.dataGrid = new MyDataGrid<>(1000);
        view.setDataWidget(dataGrid);

        initTableColumns();
        dataProvider = new RestDataProvider<ApiKey, ApiKeyResultPage>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ApiKeyResultPage> dataConsumer,
                                final Consumer<Throwable> throwableConsumer) {
                ApiKeysListPresenter.this.range = range;
                ApiKeysListPresenter.this.dataConsumer = dataConsumer;
//                delayedUpdate.reset();
                fetchData(range, dataConsumer, throwableConsumer);
            }
        };
        dataProvider.addDataDisplay(dataGrid);
    }

    private void initTableColumns() {

        final Column<ApiKey, TickBoxState> checkBoxCol = DataGridUtil.columnBuilder(
                        (ApiKey row) ->
                                TickBoxState.fromBoolean(selectedApiKeyIds.contains(row.getId())),
                        () ->
                                TickBoxCell.create(false, false))
                .build();
    }

    private void fetchData(final Range range,
                           final Consumer<ApiKeyResultPage> dataConsumer,
                           final Consumer<Throwable> throwableConsumer) {

        final Rest<ApiKeyResultPage> rest = restFactory.create();
        rest
                .onSuccess(response -> {
                    dataConsumer.accept(response);
//                    responseMap.put(nodeName, response.getValues());
//                    errorMap.put(nodeName, response.getErrors());
//                    delayedUpdate.update();
                    update();
                })
                .onFailure(throwable -> {
//                    responseMap.remove(nodeName);
//                    errorMap.put(nodeName, Collections.singletonList(throwable.getMessage()));
//                    delayedUpdate.update();
                })
                .call(API_KEY_RESOURCE)
                .find(criteria);
    }

    private void update() {

    }

    public void setQuickFilter(final String userInput) {

    }

    public void refresh() {

    }

    private void internalRefresh() {
        dataProvider.refresh();
    }


    // --------------------------------------------------------------------------------


    private class QuickFilterTimer extends Timer {

        private String name;

        @Override
        public void run() {
            String filter = name;
            if (filter != null) {
                filter = filter.trim();
                if (filter.length() == 0) {
                    filter = null;
                }
            }

            if (!Objects.equals(filter, criteria.getQuickFilterInput())) {

                // This is a new filter so reset all the expander states
                criteria.setQuickFilterInput(filter);
                internalRefresh();
            }
        }

        public void setName(final String name) {
            this.name = name;
        }
    }


    // --------------------------------------------------------------------------------


    @Deprecated
    public interface ApiKeysListView extends View {

        void setTable(Widget widget);
    }
}
