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

package stroom.search.elastic.client.component;

import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/// A dialog asking for the name of a virtual field ([ElasticVirtualFieldName]), made for one use,
/// both to add a virtual field and to rename one. OK refuses while the name can't be used, saying
/// why under it; nothing changes unless OK is pressed.
public class ElasticVirtualFieldNameController extends Controller {

    private final ElasticVirtualFieldName view;

    /// Made by GIN for one use, with the dialog's content.
    ///
    /// @param eventBus The application's event bus, which the dialog is shown on.
    /// @param view     The dialog's content.
    @Inject
    public ElasticVirtualFieldNameController(final EventBus eventBus, final ElasticVirtualFieldName view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
    }

    /// Shows the dialog, owned by `opener` until it is closed.
    ///
    /// @param opener        What shows it.
    /// @param caption       The dialog's caption, e.g. "New Virtual Field".
    /// @param initialName   The name to start with, or null for none.
    /// @param invalidReason Given a name, says why it can't be used, or gives null if it can.
    /// @param onOk          Given the name, without the space round it, when OK is pressed.
    public void show(final HasControllerNode opener,
                     final String caption,
                     final String initialName,
                     final Function<String, String> invalidReason,
                     final Consumer<String> onOk) {
        Objects.requireNonNull(onOk, "onOk");
        view.setName(initialName);
        view.setInvalidReason(invalidReason);
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .caption(caption)
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    e.hide();
                    if (e.isOk()) {
                        onOk.accept(view.getName());
                    }
                })
                .fire();
    }
}
