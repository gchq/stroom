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

import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.security.shared.SessionDetails;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.client.DataGridUtil;
import stroom.widget.button.client.ToolbarButton;
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

/// The individual sessions held by a user, a row each (its node, when it signed in, when it was last
/// used and the agent it was used from); the one selected can be ended.
///
/// Sessions are named by the opaque handle the server supplies, never by a session id - a session id
/// is the session cookie value, so putting one in the browser of an administrator would hand them a
/// credential for impersonating its owner.
public class UserSessionsList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    PagerPanel pager;
    @UiField
    ToolbarButton endSessionButton;
    @UiField
    DataGrid<SessionDetails> dataGrid;

    private final MultiSelectionModelImpl<SessionDetails> selectionModel;
    private Function<Long, String> dateFormat = String::valueOf;
    private Runnable onEndSession = () -> {
    };

    /// Makes an empty list, with no columns until [#addColumns(Function)].
    public UserSessionsList() {
        initWidget(BINDER.createAndBindUi(this));
        // Rows keyed by id, so a row whose values change is still the same row
        dataGrid.setKeyProvider(SessionDetails::getSessionHandle);
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        endSessionButton.setOnClick(() -> onEndSession.run());
        selectionModel.addSelectionHandler(e -> setButtonStates());
    }

    /// Adds the columns.
    ///
    /// @param dateFormat Formats a time with how long ago it was, in the user's preferences.
    public void addColumns(final Function<Long, String> dateFormat) {
        this.dateFormat = dateFormat;
        addColumns();
    }

    /// Shows sessions, none selected.
    ///
    /// @param rows The sessions.
    public void setRows(final List<SessionDetails> rows) {
        selectionModel.clear();
        dataGrid.setRowData(0, rows);
        dataGrid.setRowCount(rows.size(), true);
        setButtonStates();
    }

    /// @return The session selected, or `null`.
    public SessionDetails getSelected() {
        return selectionModel.getSelected();
    }

    /// @return The task monitor that spins the pager's refresh button.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }

    /// @param onEndSession Run when End Session is pressed.
    public void setOnEndSession(final Runnable onEndSession) {
        this.onEndSession = onEndSession;
    }

    private void setButtonStates() {
        endSessionButton.setEnabled(selectionModel.getSelected() != null);
    }

    private void addColumns() {
        final Column<SessionDetails, String> nodeColumn = DataGridUtil.textColumnBuilder(
                        SessionDetails::getNodeName)
                .build();
        dataGrid.addResizableColumn(nodeColumn, "Node", 150);

        final Column<SessionDetails, String> createdColumn = DataGridUtil.textColumnBuilder(
                        (SessionDetails row) -> dateFormat.apply(row.getCreateMs()))
                .build();
        dataGrid.addColumn(createdColumn, "Signed In", ColumnSizeConstants.DATE_AND_DURATION_COL);

        final Column<SessionDetails, String> lastAccessedColumn = DataGridUtil.textColumnBuilder(
                        (SessionDetails row) -> dateFormat.apply(row.getLastAccessedMs()))
                .build();
        dataGrid.addColumn(lastAccessedColumn, "Last Accessed", ColumnSizeConstants.DATE_AND_DURATION_COL);

        // The user agent is how an administrator tells one of a user's sessions from another - there is nothing
        // else human-meaningful to go on, since the handle is deliberately opaque.
        final Column<SessionDetails, String> agentColumn = DataGridUtil.textColumnBuilder(
                        SessionDetails::getLastAccessedAgent)
                .build();
        dataGrid.addAutoResizableColumn(agentColumn, "Agent", ColumnSizeConstants.BIG_COL);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, UserSessionsList> {

    }
}
