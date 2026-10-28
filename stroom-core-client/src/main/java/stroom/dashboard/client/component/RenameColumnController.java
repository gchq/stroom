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

import stroom.alert.client.event.AlertEvent;
import stroom.query.api.Column;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.DialogAction;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;
import stroom.widget.popup.client.event.HidePopupRequestEvent;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/// The Rename Field dialog ([RenameColumn]) of a table's column, made for one use. A blank name, or
/// the column's own, changes nothing; a name another column has is refused.
public class RenameColumnController extends Controller {

    private final RenameColumn view;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     The content.
    @Inject
    public RenameColumnController(final EventBus eventBus, final RenameColumn view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        view.setOnEnter(() -> ControllerDialog.pressAction(this, DialogAction.OK));
    }

    /// Asks for a column's new name in a dialog, owned by `opener` until it is closed.
    ///
    /// @param opener             What shows it.
    /// @param column             The column.
    /// @param renameInFormatting Told the old and new names, to rename the column in the table's
    ///                           conditional formatting.
    /// @param isNameInUse        Whether another column has a name.
    /// @param onRenamed          Given the column and the column renamed.
    public void rename(final HasControllerNode opener,
                       final Column column,
                       final BiConsumer<String, String> renameInFormatting,
                       final Predicate<String> isNameInUse,
                       final BiConsumer<Column, Column> onRenamed) {
        view.setName(column.getName());
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizableX())
                .caption("Rename Field")
                .onShow(e -> view.focus())
                .onHideRequest(e -> onHideRequest(e, column, renameInFormatting, isNameInUse, onRenamed))
                .fire();
    }

    private void onHideRequest(final HidePopupRequestEvent e,
                               final Column column,
                               final BiConsumer<String, String> renameInFormatting,
                               final Predicate<String> isNameInUse,
                               final BiConsumer<Column, Column> onRenamed) {
        final String newName = view.getName();
        if (e.isOk()
            && newName != null
            && !newName.trim().isEmpty()
            && !Objects.equals(newName, column.getName())) {
            // Any conditional formatting rules that use this field name are renamed too
            renameInFormatting.accept(column.getName(), newName);
            if (isNameInUse.test(newName)) {
                AlertEvent.fireError(this, "Field name \"" + newName + "\" is already in use", e::reset);
            } else {
                onRenamed.accept(column, column.copy().name(newName).build());
                e.hide();
            }
        } else {
            e.hide();
        }
    }
}
