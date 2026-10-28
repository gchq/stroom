/*
 * Copyright 2018 Crown Copyright
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

package stroom.activity.client.component;

import stroom.activity.shared.AcknowledgeSplashRequest;
import stroom.activity.shared.ActivityResource;
import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// The splash screen ([Splash]), made for one use: the terms to accept before using the system.
/// Accepting them is recorded; rejecting them says they must be accepted.
public class SplashController extends Controller {

    private static final ActivityResource ACTIVITY_RESOURCE = GWT.create(ActivityResource.class);

    private final Splash view;
    private final RestFactory restFactory;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus    The application's event bus.
    /// @param view        The content.
    /// @param restFactory Records the terms accepted.
    @Inject
    public SplashController(final EventBus eventBus, final Splash view, final RestFactory restFactory) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        initWidget(view);
    }

    /// Shows the terms in a dialog, owned by `opener` until it is closed.
    ///
    /// @param opener   What shows it.
    /// @param title    The dialog's title.
    /// @param body     The terms, as HTML.
    /// @param version  The terms' version.
    /// @param onClosed Told whether the terms were accepted, once the dialog is closed.
    public void show(final HasControllerNode opener,
                     final String title,
                     final String body,
                     final String version,
                     final Consumer<Boolean> onClosed) {
        view.setHtml(body);
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.ACCEPT_REJECT_DIALOG)
                .popupSize(PopupSize.resizable(800, 600))
                .caption(title)
                .modal(true)
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        restFactory
                                .create(ACTIVITY_RESOURCE)
                                .method(res -> res.acknowledgeSplash(new AcknowledgeSplashRequest(body, version)))
                                .onSuccess(result -> {
                                    e.hide();
                                    onClosed.accept(true);
                                })
                                .onFailure(RestErrorHandler.forPopup(this, e))
                                .owner(this)
                                .taskMonitorFactory(taskMonitor())
                                .exec();
                    } else {
                        AlertEvent.fireWarn(this, "You must accept the terms to use this system", null, () -> {
                            e.hide();
                            onClosed.accept(false);
                        });
                    }
                })
                .fire();
    }

}
