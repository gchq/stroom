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

package stroom.pipeline.client.component;

import stroom.data.client.component.DocRefCell;
import stroom.data.client.component.RefCellsController;
import stroom.docref.DocRef;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.function.Consumer;

/// A dialog, for one use, choosing which of a pipeline's changed documents ([DocRefSelection]) to
/// save.
public class DocRefSelectionController extends Controller {

    private final DocRefSelection view;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     The content.
    /// @param refCells Lets the document cells open their documents.
    @Inject
    public DocRefSelectionController(final EventBus eventBus,
                                     final DocRefSelection view,
                                     final RefCellsController refCells) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        own(refCells);
        view.addDocumentColumn(refCells.configure(new DocRefCell.Builder<DocRef>()));
    }

    /// Shows the dialog, taller for more documents.
    ///
    /// @param opener     What shows it, which owns it until it is hidden.
    /// @param caption    The dialog's caption, e.g. "Save Pipeline: Pipe A".
    /// @param docRefs    The documents changed, all ticked at first.
    /// @param onSelected Given the documents ticked when OK is pressed.
    public void show(final HasControllerNode opener,
                     final String caption,
                     final List<DocRef> docRefs,
                     final Consumer<List<DocRef>> onSelected) {
        view.setDocRefs(docRefs);
        final int popupHeight = 200 + docRefs.size() * 23;
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .modal()
                .popupSize(PopupSize.resizable(650, popupHeight))
                .caption(caption)
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        onSelected.accept(view.getSelectedItems());
                    }
                    e.hide();
                })
                .fire();
    }
}
