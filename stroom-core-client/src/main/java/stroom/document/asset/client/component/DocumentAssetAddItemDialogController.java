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

/// The dialog that adds a new, empty file or folder to a document's assets
/// ([DocumentAssetAddItemDialog]), made for one use. A name that is blank or holds a character a
/// name can't is refused, saying why.
public class DocumentAssetAddItemDialogController extends Controller {

    private static final int DIALOG_WIDTH = 300;
    private static final int DIALOG_HEIGHT = 300;

    private final DocumentAssetAddItemDialog view;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     The content.
    @Inject
    public DocumentAssetAddItemDialogController(final EventBus eventBus, final DocumentAssetAddItemDialog view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
    }

    /// Asks for the new file's or folder's name in a dialog, owned by `opener` until it is closed.
    ///
    /// @param opener            What shows it.
    /// @param path              The folder it goes in.
    /// @param illegalCharacters The characters a name can't hold.
    /// @param dialogType        Whether a file or a folder is added.
    /// @param onOk              Given the name when OK is pressed with a valid one.
    public void show(final HasControllerNode opener,
                     final String path,
                     final String illegalCharacters,
                     final DialogType dialogType,
                     final Consumer<String> onOk) {
        view.setPath(path);
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(DIALOG_WIDTH, DIALOG_HEIGHT))
                .caption("New " + dialogType.toTitleCaseString())
                .modal(true)
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final String problem = problemWith(view.getName(), illegalCharacters, dialogType);
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
    /// @param illegalCharacters The characters a name can't hold, or null for none.
    /// @param dialogType        Whether a file or a folder is added.
    /// @return What is wrong with the name, or null if nothing is.
    static String problemWith(final String name, final String illegalCharacters, final DialogType dialogType) {
        if (NullSafe.isBlankString(name)) {
            return "Please set the name of the " + dialogType + " you wish to create";
        }
        if (illegalCharacters != null) {
            for (int i = 0; i < illegalCharacters.length(); ++i) {
                final CharSequence character = illegalCharacters.subSequence(i, i + 1);
                if (name.contains(character)) {
                    return dialogType.toTitleCaseString() + " names must not contain the character '"
                           + character + "'";
                }
            }
        }
        return null;
    }


    // --------------------------------------------------------------------------------


    /// Whether a file or a folder is added.
    public enum DialogType {

        FOLDER_DIALOG("folder"),
        FILE_DIALOG("file");

        private final String name;

        DialogType(final String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }

        /// @return The name with a capital first letter, e.g. "File".
        public String toTitleCaseString() {
            return name.substring(0, 1).toUpperCase() + name.substring(1);
        }
    }
}
