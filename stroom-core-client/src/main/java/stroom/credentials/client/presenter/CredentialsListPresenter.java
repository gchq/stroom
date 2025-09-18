package stroom.credentials.client.presenter;

import stroom.credentials.shared.Credentials;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import com.google.gwt.view.client.Range;
import com.google.gwt.view.client.SelectionModel;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.function.Consumer;
import javax.inject.Inject;

/**
 * Shows the list of credentials.
 */
public class CredentialsListPresenter extends MyPresenterWidget<PagerView> {

    /** Reference to top level of page. Allows updating state */
    private CredentialsPresenter credentialsPresenter = null;

    /** REST to server */
    private final RestFactory restFactory;

    /** List of credentials */
    private final MyDataGrid<Credentials> dataGrid;

    /** What is selected in the list? */
    private final SelectionModel<Credentials> gridSelectionModel;

    /** Index of the first item in the list of credentials */
    private static final int FIRST_ITEM_INDEX = 0;

    /**
     * Injected constructor.
     */
    @SuppressWarnings("unused")
    @Inject
    public CredentialsListPresenter(final EventBus eventBus,
                                    final PagerView view,
                                    final RestFactory restFactory) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.dataGrid = new MyDataGrid<>(this);

        // Create the grid
        view.setDataWidget(dataGrid);

        this.gridSelectionModel = dataGrid.addDefaultSelectionModel(false);

        this.initColumns(dataGrid);

        // Hook up the data
        final RestDataProvider<Credentials,
                ResultPage<Credentials>> dataProvider = createDataProvider(eventBus,
                view,
                restFactory);
    }

    private void initColumns(final MyDataGrid<Credentials> grid) {
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(Credentials::getName)
                        .build(),
                DataGridUtil.headingBuilder("Credential")
                        .withToolTip("The set of credentials")
                        .build(),
                300);
        dataGrid.addEndColumn(new EndColumn<>());
    }

    private RestDataProvider<Credentials, ResultPage<Credentials>>
    createDataProvider(final EventBus eventBus,
                       final PagerView view,
                       final RestFactory restFactory) {

        return new RestDataProvider<Credentials, ResultPage<Credentials>>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<Credentials>> dataConsumer,
                                final RestErrorHandler restErrorHandler) {
                final PageRequest pageRequest = new PageRequest(range.getStart(), range.getLength());
                restFactory
                        .create(CredentialsPresenter.CREDENTIALS_RESOURCE)
                        .method((r) -> {
                            return r.list(pageRequest);
                        })
                        .onSuccess(dataConsumer)
                        .onFailure(restErrorHandler)
                        .taskMonitorFactory(view)
                        .exec();
            }
        };
    }

}
