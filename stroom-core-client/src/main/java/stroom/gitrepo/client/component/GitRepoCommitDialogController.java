/*
 * Copyright 2025 Crown Copyright
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

package stroom.gitrepo.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// The Git Push dialog ([GitRepoCommitDialog]), made for one use: asks for the commit message,
/// which is required.
public class GitRepoCommitDialogController extends Controller {

    private static final int DIALOG_WIDTH = 400;
    private static final int DIALOG_HEIGHT = 400;

    private final GitRepoCommitDialog view;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     The content.
    @Inject
    public GitRepoCommitDialogController(final EventBus eventBus, final GitRepoCommitDialog view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
    }

    /// Asks for the commit message in a dialog, owned by `opener` until it is closed.
    ///
    /// @param opener What shows it.
    /// @param onOk   Given the message when OK is pressed with one.
    public void show(final HasControllerNode opener, final Consumer<String> onOk) {
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(DIALOG_WIDTH, DIALOG_HEIGHT))
                .caption("Git Push")
                .modal(true)
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final String message = view.getCommitMessage();
                        // A message of only spaces says nothing
                        if (NullSafe.isBlankString(message)) {
                            AlertEvent.fireWarn(this, "Please enter a commit message", e::reset);
                        } else {
                            e.hide();
                            onOk.accept(message);
                        }
                    } else {
                        e.hide();
                    }
                })
                .fire();
    }
}
