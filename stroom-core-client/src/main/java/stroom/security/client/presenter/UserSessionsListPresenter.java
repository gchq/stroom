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

package stroom.security.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestFactory;
import stroom.preferences.client.DateTimeFormatter;
import stroom.security.shared.SessionDetails;
import stroom.security.shared.SessionResource;
import stroom.security.shared.UserAccessResource;
import stroom.svg.client.SvgPresets;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.Collections;
import java.util.List;

/**
 * The individual sessions held by the user selected above: the drill-down that makes ending one session possible
 * without disturbing that user's other logins.
 * <p>
 * Sessions are named by the opaque handle the server supplies, never by a session id - a session id is the session
 * cookie value, so putting one in the browser of an administrator would hand them a credential for impersonating
 * its owner.
 * </p>
 */
public class UserSessionsListPresenter extends MyPresenterWidget<PagerView> {

    private static final UserAccessResource USER_ACCESS_RESOURCE = GWT.create(UserAccessResource.class);
    private static final SessionResource SESSION_RESOURCE = GWT.create(SessionResource.class);

    private final RestFactory restFactory;
    private final DateTimeFormatter dateTimeFormatter;
    private final MyDataGrid<SessionDetails> dataGrid;
    private final MultiSelectionModelImpl<SessionDetails> selectionModel;
    private final ButtonView endSessionButton;

    private String subjectId;
    private String displayName;

    @Inject
    public UserSessionsListPresenter(final EventBus eventBus,
                                     final PagerView view,
                                     final RestFactory restFactory,
                                     final DateTimeFormatter dateTimeFormatter) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.dateTimeFormatter = dateTimeFormatter;
        this.dataGrid = new MyDataGrid<>(this);
        this.dataGrid.setTableName("Sessions");
        this.selectionModel = dataGrid.addDefaultSelectionModel(true);
        view.setDataWidget(dataGrid);

        endSessionButton = view.addButton(SvgPresets.DELETE.title("End this session"));
        initTableColumns();
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(endSessionButton.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                endSelectedSession();
            }
        }));
        registerHandler(selectionModel.addSelectionHandler(e -> setButtonStates()));
        setButtonStates();
    }

    private void setButtonStates() {
        endSessionButton.setEnabled(selectionModel.getSelected() != null);
    }

    private void initTableColumns() {
        final Column<SessionDetails, String> nodeColumn = DataGridUtil.textColumnBuilder(
                        SessionDetails::getNodeName)
                .build();
        dataGrid.addResizableColumn(nodeColumn, "Node", 150);

        final Column<SessionDetails, String> createdColumn = DataGridUtil.textColumnBuilder(
                        (SessionDetails row) -> dateTimeFormatter.formatWithDuration(row.getCreateMs()))
                .build();
        dataGrid.addColumn(createdColumn, "Signed In", ColumnSizeConstants.DATE_AND_DURATION_COL);

        final Column<SessionDetails, String> lastAccessedColumn = DataGridUtil.textColumnBuilder(
                        (SessionDetails row) -> dateTimeFormatter.formatWithDuration(row.getLastAccessedMs()))
                .build();
        dataGrid.addColumn(lastAccessedColumn, "Last Accessed", ColumnSizeConstants.DATE_AND_DURATION_COL);

        // The user agent is how an administrator tells one of a user's sessions from another - there is nothing
        // else human-meaningful to go on, since the handle is deliberately opaque.
        final Column<SessionDetails, String> agentColumn = DataGridUtil.textColumnBuilder(
                        SessionDetails::getLastAccessedAgent)
                .build();
        dataGrid.addAutoResizableColumn(agentColumn, "Agent", ColumnSizeConstants.BIG_COL);
    }

    /**
     * Show the sessions for one user, or clear the list when nothing is selected above.
     */
    public void setUser(final String subjectId, final String displayName) {
        this.subjectId = subjectId;
        this.displayName = displayName;
        refresh();
    }

    public void refresh() {
        if (NullSafe.isBlankString(subjectId)) {
            setRows(Collections.emptyList());
            return;
        }
        restFactory
                .create(USER_ACCESS_RESOURCE)
                .method(res -> res.listSessions(subjectId))
                .onSuccess(this::setRows)
                .onFailure(restError -> {
                    AlertEvent.fireError(this, "Error fetching sessions: " + restError.getMessage(), null);
                    setRows(Collections.emptyList());
                })
                .taskMonitorFactory(getView())
                .exec();
    }

    private void setRows(final List<SessionDetails> rows) {
        selectionModel.clear();
        dataGrid.setRowData(0, rows);
        dataGrid.setRowCount(rows.size(), true);
        setButtonStates();
    }

    private void endSelectedSession() {
        final SessionDetails session = selectionModel.getSelected();
        if (session == null) {
            return;
        }
        final String msg = "Are you sure you want to end this session for '"
                           + displayName
                           + "' on node '"
                           + session.getNodeName()
                           + "'?"
                           + "\n\nThey will be signed out on that device only, and their other sessions will be "
                           + "left alone. Any token already issued to them keeps working - to stop those too, "
                           + "revoke their access instead.";
        ConfirmEvent.fire(this, msg, ok -> {
            if (ok) {
                restFactory
                        .create(SESSION_RESOURCE)
                        .method(res -> res.terminateSession(
                                session.getSessionHandle(), session.getNodeName()))
                        .onSuccess(terminated -> refresh())
                        .onFailure(restError -> {
                            AlertEvent.fireError(this, restError.getMessage(), null);
                            refresh();
                        })
                        .taskMonitorFactory(getView())
                        .exec();
            }
        });
    }
}
