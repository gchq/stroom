/*
 * Copyright 2020 Crown Copyright
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

import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.DialogAction;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.function.Consumer;

/// The Rename Tab dialog ([RenameTab]) of a dashboard's component, made for one use. A blank name,
/// or the tab's own, changes nothing.
public class RenameTabController extends Controller {

    private final RenameTab view;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     The content.
    @Inject
    public RenameTabController(final EventBus eventBus, final RenameTab view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        view.setOnEnter(() -> ControllerDialog.pressAction(this, DialogAction.OK));
    }

    /// Asks for a tab's new name in a dialog, owned by `opener` until it is closed.
    ///
    /// @param opener    What shows it.
    /// @param name      The tab's name.
    /// @param onRenamed Given the new name, if it is changed.
    public void rename(final HasControllerNode opener,
                       final String name,
                       final Consumer<String> onRenamed) {
        view.setName(name);
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizableX())
                .caption("Rename Tab")
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    final String newName = view.getName();
                    if (e.isOk()
                        && newName != null
                        && !newName.trim().isEmpty()
                        && !Objects.equals(newName, name)) {
                        onRenamed.accept(newName);
                    }
                    e.hide();
                })
                .fire();
    }
}
