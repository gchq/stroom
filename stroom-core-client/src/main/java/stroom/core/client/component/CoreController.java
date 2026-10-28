/*
 * Copyright 2016 Crown Copyright
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

package stroom.core.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.core.client.UrlParameters;
import stroom.data.client.event.AskStroomAiTableRequest;
import stroom.docref.DocRef;
import stroom.document.client.event.OpenDocumentEvent;
import stroom.hyperlink.client.OpenHyperlinkRequest;
import stroom.main.client.event.ShowMainEvent;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.api.event.CurrentUserChangedEvent;
import stroom.security.client.api.event.CurrentUserChangedEvent.CurrentUserChangedHandler;
import stroom.task.client.TaskEndEvent;
import stroom.task.client.TaskEndEvent.TaskEndHandler;
import stroom.task.client.TaskStartEvent;
import stroom.task.client.TaskStartEvent.TaskStartHandler;
import stroom.widget.component.client.Controller;
import stroom.widget.menu.client.MenuRequest;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// Shows Stroom: the loading message while it starts, then, once the user is known, the main
/// screen or the document the URL names. It relays the requests of widgets with no event bus.
public class CoreController extends Controller
        implements TaskStartHandler, TaskEndHandler, CurrentUserChangedHandler {

    private final ClientSecurityContext securityContext;
    private final UrlParameters urlParameters;
    private int taskCount;
    private final Core view;
    private Controller content;

    /// Made by GIN, once: the page's root.
    ///
    /// @param eventBus        The application's event bus.
    /// @param view            Its component.
    /// @param securityContext The user's permissions.
    /// @param urlParameters   The page's URL parameters.
    @Inject
    public CoreController(final EventBus eventBus,
                          final Core view,
                          final ClientSecurityContext securityContext,
                          final UrlParameters urlParameters) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        // Show the menus that widgets with no event bus ask for (e.g. a split pane's splitter).
        // Here, as both Stroom and the standalone dashboard show this, and only Stroom shows its
        // main controller
        own(MenuRequest.relay(this));
        // and the links and Stroom AI questions they ask for (e.g. a data grid's context menu's)
        own(OpenHyperlinkRequest.relay(this));
        own(AskStroomAiTableRequest.relay(this));
        this.securityContext = securityContext;
        this.urlParameters = urlParameters;
    }

    @Override
    public void onTaskStart(final TaskStartEvent event) {
        taskCount++;

        if (taskCount < 0) {
            GWT.log("Negative task count");
        }

        if (taskCount > 0) {
            if (!securityContext.isLoggedIn()) {
                view.showWorking(event.getTask().toString());
            }
        }
    }

    @Override
    public void onTaskEnd(final TaskEndEvent event) {
        taskCount--;

        if (taskCount < 0) {
            GWT.log("Negative task count");
        }

        if (taskCount == 0) {
            view.hideWorking();
        }
    }

    @Override
    public void onCurrentUserChanged(final CurrentUserChangedEvent event) {
        final String type = urlParameters.getType();
        final String uuid = urlParameters.getUuid();

        if (UrlParameters.OPEN_DOC_ACTION.equals(urlParameters.getAction())) {
            if (type == null) {
                AlertEvent.fireError(this, "Error", "No document type specified", null);
            } else if (uuid == null) {
                AlertEvent.fireError(this, "Error", "No document UUID specified", null);
            } else {
                ShowMainEvent.fire(this, new DocRef(type, uuid));
            }

        } else {
            // See if we want to open document directly.
            if (type != null && uuid != null) {
                OpenDocumentEvent.fire(this, new DocRef(type, uuid), true, true);

            } else {
                // Show the main controller without an initial document.
                ShowMainEvent.fire(this, null);
            }
        }
    }

    /// Shows a screen in the page (in place of the loading message), in place of the one before:
    /// the main screen or the full screen view.
    ///
    /// @param content The screen.
    public void setContent(final Controller content) {
        if (content != this.content) {
            this.content = content;
            view.setContent(content);
        }
    }

}
