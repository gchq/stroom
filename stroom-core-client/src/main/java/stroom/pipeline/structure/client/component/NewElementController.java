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

package stroom.pipeline.structure.client.component;

import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.DialogAction;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.BiConsumer;

/// A pipeline element's name and description in a dialog ([NewElement]), made for one use.
public class NewElementController extends Controller {

    private final NewElement view;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     The content.
    @Inject
    public NewElementController(final EventBus eventBus, final NewElement view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        view.setOnEnter(() -> ControllerDialog.pressAction(this, DialogAction.OK));
    }

    /// Shows an element's name and description in a dialog, owned by `opener` until it is closed.
    ///
    /// @param opener      What shows it.
    /// @param caption     The dialog's caption, e.g. "Edit Element".
    /// @param name        The element's name, or null for none.
    /// @param description The element's description, or null for none.
    /// @param onOk        Given the name and description entered when OK is pressed.
    public void edit(final HasControllerNode opener,
                     final String caption,
                     final String name,
                     final String description,
                     final BiConsumer<String, String> onOk) {
        view.setName(name == null
                ? ""
                : name);
        view.setDescription(description == null
                ? ""
                : description);
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizableX())
                .caption(caption)
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        onOk.accept(view.getName(), view.getDescription());
                    }
                    e.hide();
                })
                .fire();
    }
}
