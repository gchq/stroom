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

package stroom.analytics.client.component;

import stroom.analytics.shared.NotificationConfig;
import stroom.cell.tickbox.client.TickBoxState;
import stroom.docref.HasDisplayValue;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.ColumnSizeConstants;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.AbstractDataProvider;

import java.util.function.BiConsumer;
import java.util.function.Function;

/// A rule's notifications, a row each (whether it is enabled, its type and destination, and whether
/// and how far it is limited), faded where disabled, with New, Edit and Remove; one can be selected,
/// and a double click edits it. When read only, nothing can be changed.
public class NotificationList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    ToolbarButton addButton;
    @UiField
    ToolbarButton editButton;
    @UiField
    ToolbarButton removeButton;
    @UiField
    DataGrid<NotificationConfig> dataGrid;

    private final MultiSelectionModelImpl<NotificationConfig> selectionModel;
    private Function<NotificationConfig, String> destination;
    private Runnable onAdd = () -> {
    };
    private Runnable onEdit = () -> {
    };
    private Runnable onRemove = () -> {
    };
    private BiConsumer<NotificationConfig, Boolean> onEnabledChange = (row, enabled) -> {
    };
    private BiConsumer<NotificationConfig, Boolean> onLimitChange = (row, limit) -> {
    };

    /// Makes an empty list, with no columns until [#addColumns(Function)].
    public NotificationList() {
        initWidget(BINDER.createAndBindUi(this));
        // An edit replaces the row (their equals compares every field), so keep the row by its uuid
        dataGrid.setKeyProvider(NotificationConfig::getUuid);
        selectionModel = dataGrid.addDefaultSelectionModel(false);

        addButton.setOnClick(() -> onAdd.run());
        editButton.setOnClick(() -> onEdit.run());
        removeButton.setOnClick(() -> onRemove.run());
        enableButtons();

        selectionModel.addSelectionHandler(event -> {
            enableButtons();
            // A read only document can't be edited, so double clicking does nothing
            if (event.getSelectionType().isDoubleSelect() && !isReadOnly()) {
                onEdit.run();
            }
        });
    }

    /// Adds the columns, once; they are added again when read only changes.
    ///
    /// @param destination Where a notification goes, e.g. its feed or its recipient.
    public void addColumns(final Function<NotificationConfig, String> destination) {
        this.destination = destination;
        addColumns();
    }

    /// @param dataProvider Gives the list its rows.
    public void setDataProvider(final AbstractDataProvider<NotificationConfig> dataProvider) {
        dataProvider.addDataDisplay(dataGrid);
    }

    /// @return The selection, one notification at most.
    public MultiSelectionModelImpl<NotificationConfig> getSelectionModel() {
        return selectionModel;
    }

    /// @param onAdd Run when New is pressed.
    public void setOnAdd(final Runnable onAdd) {
        this.onAdd = onAdd;
    }

    /// @param onEdit Run when Edit is pressed, or a notification is double clicked (not when read
    ///               only).
    public void setOnEdit(final Runnable onEdit) {
        this.onEdit = onEdit;
    }

    /// @param onRemove Run when Remove is pressed.
    public void setOnRemove(final Runnable onRemove) {
        this.onRemove = onRemove;
    }

    /// @param onEnabledChange Given a notification and whether it is now enabled, when its tick box
    ///                        is pressed.
    public void setOnEnabledChange(final BiConsumer<NotificationConfig, Boolean> onEnabledChange) {
        this.onEnabledChange = onEnabledChange;
    }

    /// @param onLimitChange Given a notification and whether it is now limited, when its tick box is
    ///                      pressed.
    public void setOnLimitChange(final BiConsumer<NotificationConfig, Boolean> onLimitChange) {
        this.onLimitChange = onLimitChange;
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        // The tick boxes can only be changed if the document isn't read only
        if (destination != null) {
            while (dataGrid.getColumnCount() > 0) {
                dataGrid.removeColumn(dataGrid.getColumn(0));
            }
            addColumns();
        }
        enableButtons();
    }

    private void enableButtons() {
        final boolean editable = !isReadOnly();
        final boolean hasSelection = NullSafe.hasItems(selectionModel.getSelectedItems());
        addButton.setEnabled(editable);
        editButton.setEnabled(editable && hasSelection);
        removeButton.setEnabled(editable && hasSelection);
    }

    private void addColumns() {
        final boolean updatable = !isReadOnly();

        // Enable notifications
        dataGrid.addColumn(
                DataGridUtil.updatableTickBoxColumnBuilder(
                                TickBoxState.createTickBoxFunc(NotificationConfig::isEnabled),
                                updatable)
                        .withFieldUpdater((ignored, row, value) -> {
                            if (!isReadOnly()) {
                                onEnabledChange.accept(row, TickBoxState.getAsBoolean(value));
                            }
                        })
                        .build(),
                DataGridUtil.headingBuilder("Enabled")
                        .withToolTip("Whether notifications will be sent to this destination or not.")
                        .build(),
                80);

        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(DataGridUtil.toStringFunc(
                                NotificationConfig::getDestinationType,
                                HasDisplayValue::getDisplayValue))
                        .enabledWhen(NotificationConfig::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Type")
                        .withToolTip("The type of notification to perform (Email or Stream).")
                        .build(),
                ColumnSizeConstants.MEDIUM_COL);

        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(destination)
                        .enabledWhen(NotificationConfig::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Destination")
                        .withToolTip("The destination of this notification. Either the Feed for a Stream " +
                                     "destination, or the recipient for an Email destination.")
                        .build(),
                ColumnSizeConstants.BIG_COL);

        // Limit notifications
        dataGrid.addColumn(
                DataGridUtil.updatableTickBoxColumnBuilder(TickBoxState.createTickBoxFunc(
                                        NotificationConfig::isLimitNotifications),
                                updatable)
                        .enabledWhen(NotificationConfig::isEnabled)
                        .withFieldUpdater((ignored, row, value) -> {
                            if (!isReadOnly()) {
                                onLimitChange.accept(row, TickBoxState.getAsBoolean(value));
                            }
                        })
                        .build(),
                DataGridUtil.headingBuilder("Limit")
                        .withToolTip("If set, limits the number of notification to the value of " +
                                     "'Maximum Notifications'.")
                        .build(),
                80);

        // Max notifications
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(DataGridUtil.toStringFunc(
                                NotificationConfig::getMaxNotifications,
                                String::valueOf))
                        .enabledWhen(NotificationConfig::isEnabled)
                        .rightAligned()
                        .build(),
                DataGridUtil.headingBuilder("Max")
                        .withToolTip("If 'Limit' is set, limits the number of notification to this value.")
                        .rightAligned()
                        .build(),
                ColumnSizeConstants.MEDIUM_COL);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, NotificationList> {

    }
}
