/*
 * Copyright 2019 Crown Copyright
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

package stroom.explorer.client.component;

import stroom.explorer.shared.TabSession;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.DialogAction;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.function.Consumer;

/// A dialog, for one use, choosing one of the user's tab sessions ([TabSessionChooser]) to open or
/// delete.
public class TabSessionChooserController extends Controller {

    private final TabSessionChooser view;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     The content.
    @Inject
    public TabSessionChooserController(final EventBus eventBus,
                                       final TabSessionChooser view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        view.setOnConfirm(() -> ControllerDialog.pressAction(this, DialogAction.OK));
    }

    /// Shows the dialog.
    ///
    /// @param opener   What shows it, which owns it until it is hidden.
    /// @param caption  The dialog's caption, e.g. "Select Tab Session To Open:".
    /// @param sessions The sessions to choose from.
    /// @param onChosen Given the session chosen, if OK is pressed with one chosen.
    public void choose(final HasControllerNode opener,
                       final String caption,
                       final List<TabSession> sessions,
                       final Consumer<TabSession> onChosen) {
        view.setSessions(sessions);
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(650, 340))
                .caption(caption)
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    final TabSession session = view.getSelected();
                    if (e.isOk() && session != null) {
                        onChosen.accept(session);
                    }
                    e.hide();
                })
                .fire();
    }
}
