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

package stroom.dashboard.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// The query info dialog ([QueryInfoDialog]), made for one use: asks why a query is being run,
/// checking the text against the site's pattern.
public class QueryInfoDialogController extends Controller {

    private final QueryInfoDialog view;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     The content.
    @Inject
    public QueryInfoDialogController(final EventBus eventBus, final QueryInfoDialog view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
    }

    /// Asks for the query info in a dialog, owned by `opener` until it is closed.
    ///
    /// @param opener          What shows it.
    /// @param caption         The dialog's title, as the site sets it.
    /// @param validationRegex A pattern the text must match, or null or empty for any.
    /// @param queryInfo       The text to start with, e.g. the last given.
    /// @param onOk            Given the text when OK is pressed with valid text.
    public void show(final HasControllerNode opener,
                     final String caption,
                     final String validationRegex,
                     final String queryInfo,
                     final Consumer<String> onOk) {
        view.setQueryInfo(queryInfo);
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .modal()
                .popupSize(PopupSize.resizable(640, 480))
                .caption(caption)
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        boolean valid = true;
                        if (validationRegex != null && !validationRegex.isEmpty()) {
                            valid = false;
                            try {
                                valid = view.getQueryInfo().matches(validationRegex);
                            } catch (final RuntimeException ex) {
                                AlertEvent.fireErrorFromException(this, ex, e::reset);
                            }
                        }
                        if (valid) {
                            e.hide();
                            onOk.accept(view.getQueryInfo());
                        } else {
                            AlertEvent.fireWarn(this, "The text entered is not valid", e::reset);
                        }
                    } else {
                        e.hide();
                    }
                })
                .fire();
    }
}
