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

package stroom.annotation.client.component;

import stroom.editor.client.component.CodeEditorController;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;
import stroom.widget.popup.client.event.HidePopupRequestEvent;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.BiConsumer;

/// The Edit Comment dialog ([CommentEdit]), made for one use.
public class CommentEditController extends Controller {

    private final CodeEditorController editor;

    /// Made by GIN, with its comment.
    ///
    /// @param eventBus                     The application's event bus.
    /// @param view                         The comment.
    /// @param codeEditorControllerProvider Makes the code editor's controller.
    @Inject
    public CommentEditController(final EventBus eventBus,
                                 final CommentEdit view,
                                 final Provider<CodeEditorController> codeEditorControllerProvider) {
        super(eventBus);
        initWidget(view);
        editor = make(codeEditorControllerProvider).attach(view.getEditor());
    }

    /// Shows a comment in a dialog, owned by `opener` until it is closed. OK gives the text and
    /// the hide request to `onOk`, which hides the dialog once the comment is saved, or resets it
    /// to stay open.
    ///
    /// @param opener What shows it.
    /// @param text   The comment.
    /// @param onOk   What to do with the text entered when OK is pressed.
    public void edit(final HasControllerNode opener,
                     final String text,
                     final BiConsumer<String, HidePopupRequestEvent> onOk) {
        editor.setText(text);
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(600, 600))
                .caption("Edit Comment")
                .onShow(e -> editor.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        onOk.accept(editor.getText(), e);
                    } else {
                        e.hide();
                    }
                })
                .fire();
    }
}
