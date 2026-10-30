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

package stroom.widget.popup.client.component;

import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;
import stroom.widget.popup.client.event.HidePopupRequestEvent;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// Asks for a name in a dialog ([NameDialog]), e.g. to save a tab session under. OK with no name
/// says so under the field and keeps the dialog open.
public class NameDialogController extends Controller {

    static final String NO_NAME_MESSAGE = "You must provide a name";

    private final NameDialog view;

    @Inject
    public NameDialogController(final EventBus eventBus, final NameDialog view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
    }

    /// Shows the dialog, owned by `opener` until it is closed.
    ///
    /// @param opener  What shows it.
    /// @param caption The dialog's caption, e.g. "Save New Tab Session".
    /// @param name    The name to start with, or null for none.
    /// @param onOk    Given the name when OK is pressed with one.
    public void show(final HasControllerNode opener,
                     final String caption,
                     final String name,
                     final Consumer<String> onOk) {
        view.setName(name == null
                ? ""
                : name);
        view.setValid();
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizableX())
                .caption(caption)
                .onShow(e -> view.focus())
                .onHideRequest(e -> onHideRequest(e, view.getName(), view::setInvalid, view::setValid, onOk))
                .fire();
    }

    // OK with a name gives it to onOk and closes the dialog; OK with none says why under the field
    // and keeps the dialog open; Cancel closes it
    static void onHideRequest(final HidePopupRequestEvent event,
                              final String name,
                              final Consumer<String> onInvalid,
                              final Runnable onValid,
                              final Consumer<String> onOk) {
        if (event.isOk()) {
            final String problem = problemWith(name);
            if (problem == null) {
                onValid.run();
                onOk.accept(name);
                event.hide();
            } else {
                onInvalid.accept(problem);
                event.reset();
            }
        } else {
            event.hide();
        }
    }

    /// @param name A name typed.
    /// @return What is wrong with the name, or null if nothing is.
    static String problemWith(final String name) {
        return name == null || name.isBlank()
                ? NO_NAME_MESSAGE
                : null;
    }
}
