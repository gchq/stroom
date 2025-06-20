package stroom.contentstore.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.contentstore.shared.ContentStoreContentPack;
import stroom.contentstore.shared.ContentStoreContentPackStatus;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.gwt.user.cellview.client.LoadingStateChangeEvent;
import com.google.gwt.user.cellview.client.LoadingStateChangeEvent.Handler;
import com.google.gwt.user.cellview.client.LoadingStateChangeEvent.LoadingState;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Represents the table of App Store Content Packs at the top of the App Store
 * Content Tab.
 */
public class ContentStoreContentPackListPresenter
        extends MyPresenterWidget<PagerView> {

    /** Points to top level of this page. Allows updating state of everything. */
    private ContentStorePresenter contentStorePresenter = null;

    /** REST to the server */
    final RestFactory restFactory;

    /** Table of content packs */
    final MyDataGrid<ContentStoreContentPack> dataGrid = new MyDataGrid<>();

    /** Shows what is selected in the App Store list */
    private final MultiSelectionModel<ContentStoreContentPack> gridSelectionModel;

    /** Data hookup */
    private final RestDataProvider<ContentStoreContentPack, ResultPage<ContentStoreContentPack>>
                dataProvider;

    /** Map of Content Packs -> status */
    private final Map<ContentStoreContentPack, ContentStoreContentPackStatus> contentPackStatusCache
                = new HashMap<>();

    /** Flag set when we request a new page; used to ignore unwanted LOADED events */
    private boolean requestedNewPage = false;

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
    public ContentStoreContentPackListPresenter(final EventBus eventBus,
                                                final PagerView view,
                                                final RestFactory restFactory) {
        super(eventBus, view);
        this.restFactory = restFactory;

        // Create the grid
        view.setDataWidget(dataGrid);

        // Add the style MULTI_LINE to the CSS
        dataGrid.setMultiLine(true);

        // Allow multi-selection
        this.gridSelectionModel = dataGrid.addDefaultSelectionModel(false);

        // Initialise the columns
        this.initColumns(dataGrid);

        // Hook up the data
        dataProvider = createDataProvider(eventBus, view, restFactory);
        dataProvider.addDataDisplay(dataGrid);

        // Get notified when the data has loaded & load up the first item on the page
        dataGrid.addLoadingStateChangeHandler(new Handler() {
            @Override
            public void onLoadingStateChanged(final LoadingStateChangeEvent event) {

                // If any data has loaded, try to select something
                // Note: MUST NOT use .equals() here - it won't work
                if (event.getLoadingState() == LoadingState.LOADED
                    || event.getLoadingState() == LoadingState.PARTIALLY_LOADED) {

                    // Got some data, so select the first row if there is one
                    if (gridSelectionModel.getSelected() == null
                        && dataGrid.getRowCount() > 0) {
                        // Nothing selected and we've got data so set something selected
                        ContentStoreContentPack cp = dataGrid.getVisibleItem(FIRST_ITEM_INDEX);
                        gridSelectionModel.setSelected(cp);
                    }
                }

                // Kick off the upgrade checks once everything has loaded
                // Note: MUST NOT use .equals() here - it won't work
                if (event.getLoadingState() == LoadingState.LOADED) {

                    // We get a LOADED event on every redraw, so we
                    // use a flag to detect the new page requests
                    if (requestedNewPage) {
                        requestedNewPage = false;
                        loadStateFromCache();
                        doUpgradeCheck();
                    }
                }
            }
        });
    }

    /**
     * Called when the page has loaded to load up all the state
     * we've found so far into the dataGrid Content Packs.
     * Means we don't need to redo work if we page backwards.
     */
    private void loadStateFromCache() {
        boolean dirty = false;
        for (int iCp = 0; iCp < dataGrid.getVisibleItemCount(); ++iCp) {
            ContentStoreContentPack cp = dataGrid.getVisibleItem(iCp);
            if (cp != null) {
                ContentStoreContentPackStatus status = contentPackStatusCache.get(cp);

                // If the status exists and doesn't match the stored status
                // then change it
                if (status != null
                    && !status.equals(cp.getInstallationStatus())) {

                    cp.setInstallationStatus(status);
                    dirty = true;
                }
            }
        }

        if (dirty) {
            if (contentStorePresenter != null) {
                contentStorePresenter.updateState();
            }
        }
    }

    /**
     * Method to create a data provider for the data grid that shows the content.
     * @param eventBus GWT event bus
     * @param view View to display the waiting icon
     * @param restFactory Where we get the REST stuff from
     * @return a data provider to plug into the data grid
     */
    private RestDataProvider<ContentStoreContentPack, ResultPage<ContentStoreContentPack>>
    createDataProvider(final EventBus eventBus,
                       final PagerView view,
                       final RestFactory restFactory) {

        return new RestDataProvider<>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<ContentStoreContentPack>> dataConsumer,
                                final RestErrorHandler restErrorHandler) {
                PageRequest pageRequest = new PageRequest(range.getStart(), range.getLength());
                restFactory
                        .create(ContentStorePresenter.CONTENT_STORE_RESOURCE)
                        .method((r) ->  {
                            requestedNewPage = true;
                            return r.list(pageRequest);
                        })
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
    private void initColumns(final MyDataGrid<ContentStoreContentPack> dataGrid) {

        // Icon for content pack, pulled from String in content pack
        dataGrid.addResizableColumn(
                DataGridUtil.svgStringColumn(ContentStoreContentPack::getIconSvg),
                DataGridUtil.headingBuilder("")
                        .withToolTip("Content Pack Icon")
                        .build(),
                50);

        // Name of content pack
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(ContentStoreContentPack::getUiName)
                        .build(),
                DataGridUtil.headingBuilder("Content Pack")
                        .withToolTip("The name of the content pack")
                        .build(),
                300);

        // Installation status
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder((ContentStoreContentPack cp) -> {
                    return cp.getInstallationStatus().toString();
                })
                        .build(),
                DataGridUtil.headingBuilder("Status")
                        .withToolTip("Whether installed, and if updates are available")
                        .build(),
                200);

        // Which 'store' it is from
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(
                        (ContentStoreContentPack cp) -> cp.getContentStoreMetadata().getOwnerName()
                        )
                        .build(),
                DataGridUtil.headingBuilder("Store")
                        .withToolTip("Name of the Content Store")
                        .build(),
                300);

        // End the columns
        dataGrid.addEndColumn(new EndColumn<>());
    }

    /**
     * Gives this component a reference to the top level of this page.
     * Must be called before UI is used.
     * @param contentStorePresenter The top level of this page. Must not be null.
     */
    void setContentStorePresenter(final ContentStorePresenter contentStorePresenter) {
        this.contentStorePresenter = contentStorePresenter;
    }

    /**
     * @return the selected items in the grid.
     */
    public MultiSelectionModel<ContentStoreContentPack> getSelectionModel() {
        return this.gridSelectionModel;
    }

    /**
     * Redraws this list so any state changes show up.
     */
    public void redraw() {
        dataGrid.redraw();
    }

    /**
     * Called once everything has loaded into the dataGrid.
     */
    private void doUpgradeCheck() {
        if (dataGrid.getVisibleItemCount() > 0) {
            doUpgradeCheckOn(0);
        }
    }

    /**
     * Called asynchronously to check a visible row.
     * @param rowIndex The row to check. If too big then ignored.
     */
    private void doUpgradeCheckOn(final int rowIndex) {
        if (rowIndex < dataGrid.getVisibleItemCount()) {
            ContentStoreContentPack cp = dataGrid.getVisibleItem(rowIndex);
            if (cp != null) {

                // Only check items with status INSTALLED
                // and that we haven't checked before
                // Checking other items would waste processor power
                ContentStoreContentPackStatus status = cp.getInstallationStatus();
                if (cp.getGitCommit().isEmpty()
                    && status.equals(ContentStoreContentPackStatus.INSTALLED)
                    && !contentPackStatusCache.containsKey(cp)) {
                    console("    Asking server about [" + rowIndex + "]: '"
                            + cp.getUiName() + "': (" + cp.getInstallationStatus() + ")");

                    restFactory
                            .create(ContentStorePresenter.CONTENT_STORE_RESOURCE)
                            .method(res -> res.checkContentUpgradeAvailable(cp))
                            .onSuccess(upgradeAvailable -> {
                                if (upgradeAvailable.getValue()) {
                                    cp.setInstallationStatus(ContentStoreContentPackStatus.CONTENT_UPGRADABLE);
                                    contentPackStatusCache.put(cp, ContentStoreContentPackStatus.CONTENT_UPGRADABLE);
                                    if (contentStorePresenter != null) {
                                        contentStorePresenter.updateState();
                                    }
                                } else {
                                    contentPackStatusCache.put(cp, cp.getInstallationStatus());
                                }

                                // Check the next item
                                doUpgradeCheckOn(rowIndex + 1);
                            })
                            .onFailure(restError -> {
                                AlertEvent.fireError(dataGrid,
                                        "Check for updated content failed",
                                        restError.getMessage(),
                                        null);
                            })
                            .taskMonitorFactory(getView())
                            .exec();
                } else {
                    // Try the next content pack
                    doUpgradeCheckOn(rowIndex + 1);
                }
            } else {
                // Try the next row
                doUpgradeCheckOn(rowIndex + 1);
            }
        } else {
            // End of async chain
        }
    }

    /**
     * Writes to the Javascript console for debugging.
     * @param text What to write.
     */
    public static native void console(String text)
        /*-{
        console.log(text);
         }-*/;

}
