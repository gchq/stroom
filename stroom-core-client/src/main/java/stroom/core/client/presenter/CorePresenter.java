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

package stroom.core.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.core.client.UrlParameters;
import stroom.core.client.presenter.CorePresenter.CoreView;
import stroom.docref.DocRef;
import stroom.document.client.event.OpenDocumentEvent;
import stroom.main.client.event.ShowMainEvent;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.api.event.CurrentUserChangedEvent;
import stroom.security.client.api.event.CurrentUserChangedEvent.CurrentUserChangedHandler;
import stroom.task.client.TaskEndEvent;
import stroom.task.client.TaskEndEvent.TaskEndHandler;
import stroom.task.client.TaskStartEvent;
import stroom.task.client.TaskStartEvent.TaskStartHandler;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.presenter.client.View;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

public class CorePresenter extends MyPresenterWidget<CoreView>
        implements TaskStartHandler, TaskEndHandler, CurrentUserChangedHandler {

    private final ClientSecurityContext securityContext;
    private final UrlParameters urlParameters;
    private int taskCount;
    private MyPresenterWidget<?> content;

    @Inject
    public CorePresenter(final EventBus eventBus, final CoreView view,
                         final ClientSecurityContext securityContext,
                         final UrlParameters urlParameters) {
        super(eventBus, view);
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
                getView().showWorking(event.getTask().toString());
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
            getView().hideWorking();
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
                // Show the main presenter without an initial document.
                ShowMainEvent.fire(this, null);
            }
        }
    }

    /// Shows a screen in the page (in place of the loading message), in place of the one before:
    /// the main screen or the full screen view.
    ///
    /// @param content The screen.
    public void setContent(final MyPresenterWidget<?> content) {
        if (content != this.content) {
            this.content = content;
            getView().setContentView(content.getView());
            RootPanel.get("logo").setVisible(false);
        }
    }


    // --------------------------------------------------------------------------------


    public interface CoreView extends View {

        /// Shows a screen in the page.
        ///
        /// @param view Its view.
        void setContentView(View view);

        void showWorking(final String message);

        void hideWorking();
    }
}
