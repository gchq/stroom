/*
 * Copyright 2023 Crown Copyright
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

package stroom.importexport.client.component;

import stroom.docref.DocRef;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// The Dependency Information dialog ([DependenciesInfo]), made for one use: a document's type,
/// UUID and name.
public class DependenciesInfoController extends Controller {

    private final DependenciesInfo view;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     The content.
    @Inject
    public DependenciesInfoController(final EventBus eventBus, final DependenciesInfo view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
    }

    /// Shows a document's details in a dialog, owned by `opener` until it is closed.
    ///
    /// @param opener What shows it.
    /// @param docRef The document.
    public void show(final HasControllerNode opener, final DocRef docRef) {
        view.setInfo("Type: " + docRef.getType()
                     + "\nUUID: " + docRef.getUuid()
                     + "\nName: " + docRef.getName());
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.CLOSE_DIALOG)
                .popupSize(PopupSize.resizable(400, 200))
                .caption("Dependency Information")
                .fire();
    }
}
