package stroom.contentstore.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.contentstore.shared.ContentStoreContentPack;
import stroom.contentstore.shared.ContentStoreContentPackStatus;
import stroom.contentstore.shared.ContentStoreContentPackWithDynamicState;
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
import stroom.widget.util.client.SafeHtmlUtil;

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
    final MyDataGrid<ContentStoreContentPackWithDynamicState> dataGrid;

    /** Shows what is selected in the App Store list */
    private final MultiSelectionModel<ContentStoreContentPackWithDynamicState> gridSelectionModel;

    /** Map of Content Packs -> status */
    private final Map<ContentStoreContentPack, ContentStoreContentPackStatus> contentPackStatusCache
                = new HashMap<>();

    /** Flag set when we request a new page; used to ignore unwanted LOADED events */
    private boolean requestedNewPage = false;

    /** Index of the first item in the list of content packs */
    private static final int FIRST_ITEM_INDEX = 0;

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
        this.dataGrid = new MyDataGrid<>(this);

        // Create the grid
        view.setDataWidget(dataGrid);

        // Add the style MULTI_LINE to the CSS
        dataGrid.setMultiLine(true);

        // Don't allow multi-selection
        this.gridSelectionModel = dataGrid.addDefaultSelectionModel(false);

        // Initialise the columns
        this.initColumns(dataGrid);

        // Hook up the data
        final RestDataProvider<ContentStoreContentPackWithDynamicState,
                               ResultPage<ContentStoreContentPackWithDynamicState>>

                dataProvider = createDataProvider(eventBus,
                                                  view,
                                                  restFactory);
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
                        final ContentStoreContentPackWithDynamicState cpds = dataGrid.getVisibleItem(FIRST_ITEM_INDEX);
                        gridSelectionModel.setSelected(cpds);
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
     * Having a cache means we don't need to redo work if we page backwards.
     */
    private void loadStateFromCache() {
        boolean dirty = false;
        for (int iCp = 0; iCp < dataGrid.getVisibleItemCount(); ++iCp) {
            final ContentStoreContentPackWithDynamicState cpws = dataGrid.getVisibleItem(iCp);
            if (cpws != null) {
                final ContentStoreContentPackStatus status = contentPackStatusCache.get(cpws.getContentPack());

                // If the status exists and doesn't match the stored status
                // then change it
                if (status != null
                    && !status.equals(cpws.getInstallationStatus())) {

                    cpws.setInstallationStatus(status);
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
    private RestDataProvider<ContentStoreContentPackWithDynamicState,
                             ResultPage<ContentStoreContentPackWithDynamicState>>

        createDataProvider(final EventBus eventBus,
                           final PagerView view,
                           final RestFactory restFactory) {

        return new RestDataProvider<>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<ContentStoreContentPackWithDynamicState>> dataConsumer,
                                final RestErrorHandler restErrorHandler) {
                final PageRequest pageRequest = new PageRequest(range.getStart(), range.getLength());
                restFactory
                        .create(ContentStorePresenter.CONTENT_STORE_RESOURCE)
                        .method((r) ->  {
                            // Set flag so load event isn't ignored
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
    private void initColumns(final MyDataGrid<ContentStoreContentPackWithDynamicState> dataGrid) {

        // Icon for content pack, pulled via passthrough servlet
        dataGrid.addResizableColumn(
                DataGridUtil.safeHtmlColumn(
                        cpws
                                -> SafeHtmlUtil.getSafeHtmlFromTrustedString(
                                ImageTagUtil.getImageTag(16, 16, cpws.getContentPack().getId()))
                ),
                DataGridUtil.headingBuilder("")
                        .withToolTip("Content Pack Icon")
                        .build(),
                50);

        // Name of content pack
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder((ContentStoreContentPackWithDynamicState cpws)
                                -> cpws.getContentPack().getUiName())
                        .build(),
                DataGridUtil.headingBuilder("Content Pack")
                        .withToolTip("The name of the content pack")
                        .build(),
                300);

        // Installation status
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder((ContentStoreContentPackWithDynamicState cpws)
                                -> cpws.getInstallationStatus().toString())
                        .build(),
                DataGridUtil.headingBuilder("Status")
                        .withToolTip("Whether installed, and if updates are available")
                        .build(),
                200);

        // Which 'store' it is from
        dataGrid.addAutoResizableColumn(
                DataGridUtil.textColumnBuilder(
                        (ContentStoreContentPackWithDynamicState cpws)
                                -> cpws.getContentPack().getContentStoreMetadata().getOwnerName()
                        )
                        .build(),
                DataGridUtil.headingBuilder("Store")
                        .withToolTip("Name of the Content Store")
                        .build(),
                80);

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
    public MultiSelectionModel<ContentStoreContentPackWithDynamicState> getSelectionModel() {
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
            final ContentStoreContentPackWithDynamicState cpws = dataGrid.getVisibleItem(rowIndex);
            if (cpws != null) {

                // Only check items with status INSTALLED
                // and that we haven't checked before
                // Checking other items would waste processor power
                final ContentStoreContentPackStatus status = cpws.getInstallationStatus();
                if (cpws.getContentPack().getGitCommit().isEmpty()
                    && status.equals(ContentStoreContentPackStatus.INSTALLED)
                    && !contentPackStatusCache.containsKey(cpws.getContentPack())) {
                    console("    Asking server about [" + rowIndex + "]: '"
                            + cpws.getContentPack().getUiName() + "': (" + cpws.getInstallationStatus() + ")");

                    restFactory
                            .create(ContentStorePresenter.CONTENT_STORE_RESOURCE)
                            .method(res -> res.checkContentUpgradeAvailable(cpws.getContentPack()))
                            .onSuccess(upgradeAvailable -> {
                                if (upgradeAvailable.getValue()) {
                                    cpws.setInstallationStatus(ContentStoreContentPackStatus.CONTENT_UPGRADABLE);
                                    contentPackStatusCache.put(cpws.getContentPack(), cpws.getInstallationStatus());
                                    if (contentStorePresenter != null) {
                                        contentStorePresenter.updateState();
                                    }
                                } else {
                                    contentPackStatusCache.put(cpws.getContentPack(), cpws.getInstallationStatus());
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
