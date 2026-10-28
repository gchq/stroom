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

package stroom.document.asset.client.component;

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

/// The dialog that renames a file or folder in a document's assets ([DocumentAssetEditAssetDialog]),
/// made for one use. A name that is blank or holds a character a name can't is refused, saying why.
public class DocumentAssetEditAssetDialogController extends Controller {

    private static final int DIALOG_WIDTH = 300;
    private static final int DIALOG_HEIGHT = 300;

    private final DocumentAssetEditAssetDialog view;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     The content.
    @Inject
    public DocumentAssetEditAssetDialogController(final EventBus eventBus, final DocumentAssetEditAssetDialog view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
    }

    /// Asks for a file's or folder's new name in a dialog, owned by `opener` until it is closed.
    ///
    /// @param opener            What shows it.
    /// @param name              Its name.
    /// @param leaf              True for a file, false for a folder.
    /// @param illegalCharacters The characters a name can't hold.
    /// @param onOk              Given the name when OK is pressed with a valid one.
    public void edit(final HasControllerNode opener,
                     final String name,
                     final boolean leaf,
                     final String illegalCharacters,
                     final Consumer<String> onOk) {
        view.setName(name);
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(DIALOG_WIDTH, DIALOG_HEIGHT))
                .caption("Edit")
                .modal(true)
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final String problem = problemWith(view.getName(), leaf, illegalCharacters);
                        if (problem == null) {
                            onOk.accept(view.getName());
                            e.hide();
                        } else {
                            AlertEvent.fireWarn(this, problem, e::reset);
                        }
                    } else {
                        e.hide();
                    }
                })
                .fire();
    }

    /// @param name              The name entered.
    /// @param leaf              True for a file, false for a folder.
    /// @param illegalCharacters The characters a name can't hold, or null for none.
    /// @return What is wrong with the name, or null if nothing is.
    static String problemWith(final String name, final boolean leaf, final String illegalCharacters) {
        if (NullSafe.isBlankString(name)) {
            return leaf
                    ? "Please set the name of the file"
                    : "Please set the name of the folder";
        }
        if (illegalCharacters != null) {
            for (int i = 0; i < illegalCharacters.length(); ++i) {
                final CharSequence character = illegalCharacters.subSequence(i, i + 1);
                if (name.contains(character)) {
                    return "The name must not contain the character '" + character + "'";
                }
            }
        }
        return null;
    }
}
