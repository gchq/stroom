package stroom.appstore.client.presenter;

import stroom.appstore.shared.AppStoreContentPack;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.data.table.client.Refreshable;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.LoadingStateChangeEvent;
import com.google.gwt.user.cellview.client.LoadingStateChangeEvent.Handler;
import com.google.gwt.user.cellview.client.LoadingStateChangeEvent.LoadingState;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.function.Consumer;

/**
 * Represents the table of App Store Content Packs at the top of the App Store
 * Content Tab.
 */
public class AppStoreContentPackListPresenter
        extends MyPresenterWidget<PagerView>
        implements Refreshable {

    /** Shows what is selected in the App Store list */
    private final MultiSelectionModel<AppStoreContentPack> gridSelectionModel;

    /** Data hookup */
    private final RestDataProvider<AppStoreContentPack, ResultPage<AppStoreContentPack>>
                dataProvider;

    /** Index of the first item in the list of content packs */
    private final static int FIRST_ITEM_INDEX = 0;

    /**
     * Injected constructor.
     * @param eventBus GWT event bus
     * @param view Where this component is going to be inserted
     * @param restFactory Creates connection to server
     */
    @SuppressWarnings("unused")
    @Inject
    public AppStoreContentPackListPresenter(final EventBus eventBus,
                                            final PagerView view,
                                            final RestFactory restFactory) {
        super(eventBus, view);

        // Create the grid
        final MyDataGrid<AppStoreContentPack> dataGrid = new MyDataGrid<>();
        view.setDataWidget(dataGrid);

        // Add the style MULTI_LINE to the CSS
        dataGrid.setMultiLine(true);

        // Allow multi-selection
        this.gridSelectionModel = dataGrid.addDefaultSelectionModel(false);

        // Initialise the columns
        this.initColumns(dataGrid);

        // Hook up the data
        this.dataProvider = createDataProvider(eventBus, view, restFactory);
        dataProvider.addDataDisplay(dataGrid);

        // Get notified when the data has loaded & load up the first item on the page
        dataGrid.addLoadingStateChangeHandler(new Handler() {
            @Override
            public void onLoadingStateChanged(final LoadingStateChangeEvent event) {

                // If any data has loaded, try to select something
                if (event.getLoadingState().equals(LoadingState.LOADED)
                    || event.getLoadingState().equals(LoadingState.PARTIALLY_LOADED)) {

                    // Got some data, so select the first row if there is one
                    if (gridSelectionModel.getSelected() == null
                        && dataGrid.getRowCount() > 0) {
                        // Nothing selected and we've got data so set something selected
                        var cp = dataGrid.getVisibleItem(FIRST_ITEM_INDEX);
                        gridSelectionModel.setSelected(cp);
                    }
                }
            }
        });
    }

    /**
     * Method to create a data provider for the data grid that shows the content.
     * @param eventBus GWT event bus
     * @param view View to display the waiting icon
     * @param restFactory Where we get the REST stuff from
     * @return a data provider to plug into the data grid
     */
    private static RestDataProvider<AppStoreContentPack, ResultPage<AppStoreContentPack>>
    createDataProvider(final EventBus eventBus,
                       final PagerView view,
                       final RestFactory restFactory) {

        return new RestDataProvider<>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<AppStoreContentPack>> dataConsumer,
                                final RestErrorHandler restErrorHandler) {
                PageRequest pageRequest = new PageRequest(range.getStart(), range.getLength());
                restFactory
                        .create(AppStorePresenter.APP_STORE_RESOURCE)
                        .method((r) ->  r.list(pageRequest))
                        .onSuccess(dataConsumer)
                        .onFailure(restErrorHandler)
                        .taskMonitorFactory(view)
                        .exec();
            }
        };
    }

    /**
     * Creates the columns for displaying the Content Packs.
     * @param dataGrid The widget to display stuff in.
     */
    private void initColumns(final MyDataGrid<AppStoreContentPack> dataGrid) {

        SvgIconCell svgCell = new SvgIconCell();
        Column<AppStoreContentPack, String> svgColumn = new Column<>(svgCell) {
            @Override
            public String getValue(final AppStoreContentPack cp) {
                return cp.getIconSvg();
            }
        };
        dataGrid.addColumn(svgColumn, "Icon");

        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(AppStoreContentPack::getUiName)
                        .build(),
                DataGridUtil.headingBuilder("Content Pack")
                        .withToolTip("The name of the content pack")
                        .build(),
                300);
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(AppStoreContentPack::getContentStoreUiName)
                        .build(),
                DataGridUtil.headingBuilder("Store")
                        .withToolTip("Name of the Content Store")
                        .build(),
                300);
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(AppStoreContentPack::getLicenseName)
                        .build(),
                DataGridUtil.headingBuilder("License")
                        .withToolTip("License")
                        .build(),
                300);

        dataGrid.addEndColumn(new EndColumn<>());
    }

    /**
     * @return the selected items in the grid.
     */
    public MultiSelectionModel<AppStoreContentPack> getSelectionModel() {
        return this.gridSelectionModel;
    }

    /**
     * Refreshes the display by telling the data provider to refresh its data.
     */
    public void refresh() {
        dataProvider.refresh();
    }

}
