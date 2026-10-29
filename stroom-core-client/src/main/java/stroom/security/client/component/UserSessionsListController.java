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

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.dispatch.client.RestFactory;
import stroom.preferences.client.DateTimeFormatter;
import stroom.security.shared.SessionDetails;
import stroom.security.shared.SessionResource;
import stroom.security.shared.UserAccessResource;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Controller;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Collections;
import java.util.Objects;

/// The individual sessions held by the user selected above ([UserSessionsList]): the drill-down that
/// makes ending one session possible without disturbing that user's other logins.
public class UserSessionsListController extends Controller {

    private static final UserAccessResource USER_ACCESS_RESOURCE = GWT.create(UserAccessResource.class);
    private static final SessionResource SESSION_RESOURCE = GWT.create(SessionResource.class);

    private final RestFactory restFactory;
    private final DateTimeFormatter dateTimeFormatter;
    private UserSessionsList view;

    private String subjectId;
    private String displayName;

    /// Made by GIN; [#attach(UserSessionsList)] gives it its list.
    ///
    /// @param eventBus          The application's event bus.
    /// @param restFactory       Lists and ends sessions.
    /// @param dateTimeFormatter Formats times in the user's preferences.
    @Inject
    public UserSessionsListController(final EventBus eventBus,
                                      final RestFactory restFactory,
                                      final DateTimeFormatter dateTimeFormatter) {
        super(eventBus);
        this.restFactory = restFactory;
        this.dateTimeFormatter = dateTimeFormatter;
    }

    /// Attaches this to a list, e.g. one a template declares. The list is then this controller's
    /// widget.
    ///
    /// @param view The list.
    /// @return This, for its owner to keep.
    public UserSessionsListController attach(final UserSessionsList view) {
        this.view = Objects.requireNonNull(view, "view");
        initWidget(view);
        view.addColumns(dateTimeFormatter::formatWithDuration);
        view.setOnEndSession(this::endSelectedSession);
        return this;
    }

    /// Shows the sessions for one user, or clears the list when nothing is selected above.
    ///
    /// @param subjectId   The user's subject id, or `null`.
    /// @param displayName The user's name, for the confirmation when a session is ended.
    public void setUser(final String subjectId, final String displayName) {
        this.subjectId = subjectId;
        this.displayName = displayName;
        refresh();
    }

    /// Fetches the sessions again.
    public void refresh() {
        if (NullSafe.isBlankString(subjectId)) {
            view.setRows(Collections.emptyList());
            return;
        }
        restFactory
                .create(USER_ACCESS_RESOURCE)
                .method(res -> res.listSessions(subjectId))
                .onSuccess(view::setRows)
                .onFailure(restError -> {
                    AlertEvent.fireError(this, "Error fetching sessions: " + restError.getMessage(), null);
                    view.setRows(Collections.emptyList());
                })
                .owner(this)
                .taskMonitorFactory(view.getTaskMonitor())
                .exec();
    }

    private void endSelectedSession() {
        final SessionDetails session = view.getSelected();
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
                        .owner(this)
                        .taskMonitorFactory(view.getTaskMonitor())
                        .exec();
            }
        });
    }
}
