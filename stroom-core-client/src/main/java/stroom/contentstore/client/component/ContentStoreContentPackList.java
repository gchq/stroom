/*
 * Copyright 2026 Crown Copyright
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

package stroom.contentstore.client.component;

import stroom.contentstore.client.ImageTagUtil;
import stroom.contentstore.shared.ContentStoreContentPackWithDynamicState;
import stroom.util.client.DataGridUtil;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.task.client.TaskMonitorFactory;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.LoadingStateChangeEvent.LoadingState;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.AbstractDataProvider;

/// The content packs in the content stores, a row each (its icon, name, installation status and
/// store); one can be selected, the first once a page is shown if none is.
public class ContentStoreContentPackList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    // Index of the first item in the list of content packs
    private static final int FIRST_ITEM_INDEX = 0;

    @UiField
    PagerPanel pager;
    @UiField
    DataGrid<ContentStoreContentPackWithDynamicState> dataGrid;

    private final MultiSelectionModel<ContentStoreContentPackWithDynamicState> gridSelectionModel;
    private Runnable onLoaded = () -> {
    };

    /// Makes an empty list.
    public ContentStoreContentPackList() {
        initWidget(BINDER.createAndBindUi(this));
        // Rows keyed by their pack: each fetch brings new rows
        dataGrid.setKeyProvider(ContentStoreContentPackWithDynamicState::getContentPack);
        // Don't allow multi-selection
        gridSelectionModel = dataGrid.addDefaultSelectionModel(false);
        addColumns();

        // Get notified when the data has loaded & load up the first item on the page
        dataGrid.addLoadingStateChangeHandler(event -> {
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

            // Note: MUST NOT use .equals() here - it won't work
            if (event.getLoadingState() == LoadingState.LOADED) {
                onLoaded.run();
            }
        });
    }

    /// @param dataProvider Gives the list its rows, a page at a time.
    public void setDataProvider(final AbstractDataProvider<ContentStoreContentPackWithDynamicState> dataProvider) {
        dataProvider.addDataDisplay(dataGrid);
    }

    /// @return The task monitor that spins the pager's refresh button.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }

    /// @return The selection, one pack at most.
    public MultiSelectionModel<ContentStoreContentPackWithDynamicState> getSelectionModel() {
        return gridSelectionModel;
    }

    /// @return How many rows are shown.
    public int getVisibleItemCount() {
        return dataGrid.getVisibleItemCount();
    }

    /// @param index A row shown.
    /// @return Its pack.
    public ContentStoreContentPackWithDynamicState getVisibleItem(final int index) {
        return dataGrid.getVisibleItem(index);
    }

    /// Draws the rows again, so any change to their status shows.
    public void redraw() {
        dataGrid.redraw();
    }

    /// @param onLoaded Run each time the rows are drawn (a page is shown, or drawn again).
    public void setOnLoaded(final Runnable onLoaded) {
        this.onLoaded = onLoaded;
    }

    private void addColumns() {

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
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ContentStoreContentPackList> {

    }
}
