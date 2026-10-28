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

package stroom.security.client.component;

import stroom.data.client.ColumnSizeConstants;
import stroom.security.identity.shared.SigningKeyRow;
import stroom.security.identity.shared.SigningKeyStatus;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.client.DataGridUtil;
import stroom.widget.button.client.Toolbar;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;
import java.util.function.Function;

/// The internal identity provider's signing keys, a row each (its status and when it was issued).
///
/// Unlike the other lists here there is no quick filter and no paging: a deployment holds one signing
/// key and perhaps one or two on their way out, so both would be furniture rather than help.
public class SigningKeyList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    PagerPanel pager;
    @UiField
    DataGrid<SigningKeyRow> dataGrid;

    private final MultiSelectionModelImpl<SigningKeyRow> selectionModel;
    private Function<Long, String> dateFormat = String::valueOf;

    /// Makes an empty list.
    public SigningKeyList() {
        initWidget(BINDER.createAndBindUi(this));
        // Rows keyed by id, so a row whose values change is still the same row
        dataGrid.setKeyProvider(SigningKeyRow::getId);
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        addColumns();
    }

    /// @param dateFormat Formats a time, in the user's preferences.
    public void setDateFormat(final Function<Long, String> dateFormat) {
        this.dateFormat = dateFormat;
    }

    /// @param rows The keys to show.
    public void setData(final List<SigningKeyRow> rows) {
        dataGrid.setRowData(0, rows);
        dataGrid.setRowCount(rows.size(), true);
        if (rows.isEmpty()) {
            selectionModel.clear();
        }
    }

    /// @return The selection.
    public MultiSelectionModelImpl<SigningKeyRow> getSelectionModel() {
        return selectionModel;
    }

    /// @return The toolbar beside the pager, for the screen's own buttons.
    public Toolbar getToolbar() {
        return pager.getToolbar();
    }

    /// @return The pager, whose refresh button spins while the keys are fetched.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }

    // Two columns and no more. Nothing here describes the key itself - the stored key includes its private
    // material, so the server never sends anything but what the key is doing and when.
    private void addColumns() {
        final Column<SigningKeyRow, String> statusColumn = DataGridUtil.textColumnBuilder(
                        this::describeStatus)
                .build();
        dataGrid.addResizableColumn(
                statusColumn,
                DataGridUtil.headingBuilder("Status")
                        .withToolTip("Active signs new tokens and has no scheduled end: rotation replaces "
                                     + "it, and only then does a wind-down date exist. Retired still "
                                     + "verifies tokens already issued, until the date shown. Revoked was "
                                     + "withdrawn by an administrator.")
                        .build(),
                300);

        final Column<SigningKeyRow, String> issuedColumn = DataGridUtil.textColumnBuilder(
                        (SigningKeyRow row) -> dateFormat.apply(row.getIssuedMs()))
                .build();
        dataGrid.addAutoResizableColumn(
                issuedColumn,
                DataGridUtil.headingBuilder("Issued")
                        .withToolTip("When this key was created.")
                        .build(),
                ColumnSizeConstants.DATE_COL);
    }

    // The trust-window end rides with the status for the two states that have one, rather than sitting in
    // an "Expires" column. These keys do not expire on a schedule - the active key has no date at all until
    // rotation replaces it - so a column shaped like a certificate lifetime answered a question the most
    // important row could not, and "While active" was that column admitting it.
    private String describeStatus(final SigningKeyRow row) {
        final SigningKeyStatus status = row.getStatus();
        if (status == null) {
            return "";
        }
        if (row.getExpiresMs() == null) {
            return status.getDisplayValue();
        }
        return status.getDisplayValue()
               + " (trusted until " + dateFormat.apply(row.getExpiresMs()) + ")";
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, SigningKeyList> {

    }
}
