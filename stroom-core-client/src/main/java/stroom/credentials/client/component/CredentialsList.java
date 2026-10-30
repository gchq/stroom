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

package stroom.credentials.client.component;

import stroom.ai.shared.KeyStoreType;
import stroom.credentials.shared.Credential;
import stroom.credentials.shared.CredentialWithPerms;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.ColumnSizeConstants;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.task.client.TaskMonitorFactory;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.LoadingStateChangeEvent.LoadingState;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.AbstractDataProvider;

import java.util.function.Function;

/// The credentials, a row each (its name, type, key store type and when it expires), with New,
/// Delete, Edit and Replace Secret, as the selected one's permissions allow; one can be selected, and
/// a double click edits it. Unless told otherwise, the first is selected once they have loaded.
public class CredentialsList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final int FIRST_ITEM_INDEX = 0;

    @UiField
    PagerPanel pager;
    @UiField
    ToolbarButton addButton;
    @UiField
    ToolbarButton deleteButton;
    @UiField
    ToolbarButton editButton;
    @UiField
    ToolbarButton replaceSecretButton;
    @UiField
    DataGrid<CredentialWithPerms> dataGrid;

    private final MultiSelectionModel<CredentialWithPerms> selectionModel;
    private boolean selectFirstWhenLoaded = true;
    private Runnable onAdd = () -> {
    };
    private Runnable onDelete = () -> {
    };
    private Runnable onEdit = () -> {
    };
    private Runnable onReplaceSecret = () -> {
    };

    /// Makes an empty list, with no columns until [#addColumns(Function)].
    public CredentialsList() {
        initWidget(BINDER.createAndBindUi(this));
        selectionModel = dataGrid.addDefaultSelectionModel(false);

        addButton.setOnClick(() -> onAdd.run());
        deleteButton.setOnClick(() -> onDelete.run());
        editButton.setOnClick(() -> onEdit.run());
        replaceSecretButton.setOnClick(() -> onReplaceSecret.run());

        selectionModel.addSelectionHandler(event -> {
            if (event.getSelectionType().isDoubleSelect()) {
                // Edit the credentials
                onEdit.run();
            }
        });
        selectionModel.addSelectionHandler(event -> enableButtons());

        // Once loaded, select the first unless one is selected
        dataGrid.addLoadingStateChangeHandler(event -> {
            if (event.getLoadingState() == LoadingState.LOADED ||
                event.getLoadingState() == LoadingState.PARTIALLY_LOADED) {
                if (selectFirstWhenLoaded) {
                    if (selectionModel.getSelected() == null && dataGrid.getRowCount() > 0) {
                        selectionModel.setSelected(dataGrid.getVisibleItem(FIRST_ITEM_INDEX));
                    }
                }
            }
        });

        enableButtons();
    }

    /// Adds the columns, once.
    ///
    /// @param expiryFormat Formats when a credential expires, with how long until then.
    public void addColumns(final Function<Long, SafeHtml> expiryFormat) {
        dataGrid.addResizableColumn(DataGridUtil.textColumnBuilder(
                                (CredentialWithPerms cwp) -> cwp.getCredential().getName())
                        .build(),
                DataGridUtil.headingBuilder("Name")
                        .withToolTip("Name of credentials")
                        .build(),
                280);

        dataGrid.addResizableColumn(DataGridUtil.textColumnBuilder(
                                (CredentialWithPerms cwp) -> cwp.getCredential().getCredentialType().getDisplayValue())
                        .build(),
                DataGridUtil.headingBuilder("Credential Type")
                        .withToolTip("Type of credential")
                        .build(),
                150);

        dataGrid.addResizableColumn(DataGridUtil.textColumnBuilder(CredentialsList::getKeyStoreType)
                        .build(),
                DataGridUtil.headingBuilder("Key Store Type")
                        .withToolTip("Type of keystore")
                        .build(),
                150);

        dataGrid.addResizableColumn(DataGridUtil.htmlColumnBuilder(
                                (CredentialWithPerms cwp) -> getExpires(cwp, expiryFormat))
                        .build(),
                DataGridUtil.headingBuilder("Expires")
                        .withToolTip("When these credentials expire")
                        .build(),
                ColumnSizeConstants.DATE_AND_DURATION_COL);
    }

    /// @param dataProvider Gives the list its rows, a page at a time.
    public void setDataProvider(final AbstractDataProvider<CredentialWithPerms> dataProvider) {
        dataProvider.addDataDisplay(dataGrid);
    }

    /// @return The task monitor that spins the pager's refresh button.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }

    /// @return The selection, one credential at most.
    public MultiSelectionModel<CredentialWithPerms> getSelectionModel() {
        return selectionModel;
    }

    /// Selects the first credential shown, if there is one.
    public void selectFirst() {
        final CredentialWithPerms firstItem = dataGrid.getVisibleItem(FIRST_ITEM_INDEX);
        if (firstItem != null) {
            selectionModel.setSelected(firstItem);
        }
    }

    /// @param selectFirstWhenLoaded Whether the first credential is selected once they have loaded,
    ///                              if none is.
    public void setSelectFirstWhenLoaded(final boolean selectFirstWhenLoaded) {
        this.selectFirstWhenLoaded = selectFirstWhenLoaded;
    }

    /// @param onAdd Run when New is pressed.
    public void setOnAdd(final Runnable onAdd) {
        this.onAdd = onAdd;
    }

    /// @param onDelete Run when Delete is pressed.
    public void setOnDelete(final Runnable onDelete) {
        this.onDelete = onDelete;
    }

    /// @param onEdit Run when Edit is pressed, or a credential is double clicked.
    public void setOnEdit(final Runnable onEdit) {
        this.onEdit = onEdit;
    }

    /// @param onReplaceSecret Run when Replace Secret is pressed.
    public void setOnReplaceSecret(final Runnable onReplaceSecret) {
        this.onReplaceSecret = onReplaceSecret;
    }

    // Delete, Edit and Replace Secret, as the selected credential's permissions allow
    private void enableButtons() {
        final CredentialWithPerms cwp = selectionModel.getSelected();
        if (cwp != null) {
            deleteButton.setEnabled(cwp.isDelete());
            editButton.setEnabled(cwp.isEdit());
            replaceSecretButton.setEnabled(cwp.isEdit());
        } else {
            deleteButton.setEnabled(false);
            editButton.setEnabled(false);
            replaceSecretButton.setEnabled(false);
        }
    }

    // When a credential expires, or "Never"
    private static SafeHtml getExpires(final CredentialWithPerms cwp,
                                       final Function<Long, SafeHtml> expiryFormat) {
        final Long expiryTimeMs = NullSafe.get(cwp,
                CredentialWithPerms::getCredential,
                Credential::getExpiryTimeMs);
        return expiryTimeMs == null
                ? SafeHtmlUtils.fromSafeConstant("Never")
                : expiryFormat.apply(expiryTimeMs);
    }

    private static String getKeyStoreType(final CredentialWithPerms cwp) {
        return NullSafe.getOrElse(cwp,
                CredentialWithPerms::getCredential,
                Credential::getKeyStoreType,
                KeyStoreType::getDisplayValue,
                "");
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, CredentialsList> {

    }
}
