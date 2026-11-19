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

package stroom.data.client.component;

import stroom.util.shared.Count;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// Shows the item selection dialog ([ItemSelection]) for one use, e.g. to go to a record of the
/// data viewer's stream.
public class ItemSelectionController extends Controller {

    private final ItemSelection view;

    /// Made by GIN for one use, with the dialog's content.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     Its component.
    @Inject
    public ItemSelectionController(final EventBus eventBus, final ItemSelection view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
    }

    /// Shows the dialog, owned by `opener` until it is closed. OK gives the item chosen to `onOk`.
    ///
    /// @param opener          What shows it.
    /// @param name            What an item is, e.g. `Record`, for the caption ("Select Record").
    /// @param itemNo          The item to start on, zero based.
    /// @param totalItemsCount The number of items.
    /// @param onOk            Given the item chosen, zero based.
    public void edit(final HasControllerNode opener,
                     final String name,
                     final long itemNo,
                     final Count<Long> totalItemsCount,
                     final Consumer<Long> onOk) {
        view.setItemNo(itemNo);
        view.setTotalItemsCount(totalItemsCount);
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .modal()
                .caption("Select " + name)
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        onOk.accept(view.getItemNo());
                    }
                    e.hide();
                })
                .fire();
    }
}
