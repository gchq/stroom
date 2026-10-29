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

package stroom.processor.client.component;

import stroom.processor.shared.ProfilePeriod;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.time.Days;
import stroom.util.shared.time.Time;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;
import java.util.function.Consumer;

/// A processor profile's periods, a row each (its days, start and end times, and node and cluster
/// thread limits), with New, Edit and Remove; one can be selected, and a double click edits it. When
/// read only, the buttons are disabled and say so.
public class ProfilePeriodList extends Component implements Focus {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final String READ_ONLY_SUFFIX = " (read only)";

    @UiField
    ToolbarButton addButton;
    @UiField
    ToolbarButton editButton;
    @UiField
    ToolbarButton removeButton;
    @UiField
    DataGrid<ProfilePeriod> dataGrid;

    private final String addTitle;
    private final String editTitle;
    private final String removeTitle;
    private final MultiSelectionModelImpl<ProfilePeriod> selectionModel;
    private Runnable onAdd = () -> {
    };
    private Consumer<ProfilePeriod> onEdit = period -> {
    };
    private Consumer<ProfilePeriod> onRemove = period -> {
    };

    /// Makes an empty list.
    public ProfilePeriodList() {
        initWidget(BINDER.createAndBindUi(this));
        addTitle = addButton.getTitle();
        editTitle = editButton.getTitle();
        removeTitle = removeButton.getTitle();
        // Rows keyed by uuid: an edit replaces the row
        dataGrid.setKeyProvider(ProfilePeriod::getUuid);
        selectionModel = dataGrid.addDefaultSelectionModel(false);

        addButton.setOnClick(() -> onAdd.run());
        editButton.setOnClick(() -> onEdit.accept(selectionModel.getSelected()));
        removeButton.setOnClick(() -> onRemove.accept(selectionModel.getSelected()));

        addColumns();
        enableButtons();

        selectionModel.addSelectionHandler(event -> {
            enableButtons();
            if (event.getSelectionType().isDoubleSelect()) {
                onEdit.accept(selectionModel.getSelected());
            }
        });
    }

    @Override
    public void focus() {
        addButton.focus();
    }

    /// @param profilePeriods The periods.
    public void setData(final List<ProfilePeriod> profilePeriods) {
        dataGrid.setRowData(0, profilePeriods);
        dataGrid.setRowCount(profilePeriods.size());
        enableButtons();
    }

    /// @param onAdd Run when New is pressed.
    public void setOnAdd(final Runnable onAdd) {
        this.onAdd = onAdd;
    }

    /// @param onEdit Given the selected period, or `null`, when Edit is pressed or a period is double
    ///               clicked.
    public void setOnEdit(final Consumer<ProfilePeriod> onEdit) {
        this.onEdit = onEdit;
    }

    /// @param onRemove Given the selected period, or `null`, when Remove is pressed.
    public void setOnRemove(final Consumer<ProfilePeriod> onRemove) {
        this.onRemove = onRemove;
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        enableButtons();
    }

    private void addColumns() {
        dataGrid.addResizableColumn(
                DataGridUtil.copyTextColumnBuilder((ProfilePeriod ctr) ->
                                NullSafe.get(ctr, ProfilePeriod::getDays, Days::toString))
                        .build(),
                "Days",
                200);
        dataGrid.addResizableColumn(
                DataGridUtil.copyTextColumnBuilder((ProfilePeriod ctr) ->
                                NullSafe.get(ctr, ProfilePeriod::getStartTime, Time::toString))
                        .build(),
                "Start Time",
                85);
        dataGrid.addResizableColumn(
                DataGridUtil.copyTextColumnBuilder((ProfilePeriod ctr) ->
                                NullSafe.get(ctr, ProfilePeriod::getEndTime, Time::toString))
                        .build(),
                "End Time",
                85);
        dataGrid.addResizableColumn(
                DataGridUtil.copyTextColumnBuilder((final ProfilePeriod profilePeriod) ->
                                profilePeriod.isLimitNodeThreads()
                                        ? "" + profilePeriod.getMaxNodeThreads()
                                        : "Unlimited")
                        .build(),
                "Max Node Threads",
                150);
        dataGrid.addResizableColumn(
                DataGridUtil.copyTextColumnBuilder((final ProfilePeriod profilePeriod) ->
                                profilePeriod.isLimitClusterThreads()
                                        ? "" + profilePeriod.getMaxClusterThreads()
                                        : "Unlimited")
                        .build(),
                "Max Cluster Threads",
                150);
    }

    private void enableButtons() {
        final boolean readOnly = isReadOnly();
        addButton.setEnabled(!readOnly);

        final ProfilePeriod selected = selectionModel.getSelected();

        editButton.setEnabled(!readOnly && selected != null);
        removeButton.setEnabled(!readOnly && selected != null);

        final String readOnlySuffix = readOnly
                ? READ_ONLY_SUFFIX
                : "";
        addButton.setTitle(addTitle + readOnlySuffix);
        editButton.setTitle(editTitle + readOnlySuffix);
        removeButton.setTitle(removeTitle + readOnlySuffix);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ProfilePeriodList> {

    }
}
