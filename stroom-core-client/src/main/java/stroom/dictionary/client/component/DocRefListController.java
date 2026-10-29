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

package stroom.dictionary.client.component;

import stroom.docref.DocRef;
import stroom.document.client.event.OpenDocumentEvent;
import stroom.widget.component.client.Controller;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.Objects;

/// A dictionary's imports ([DocRefList]), a row each; a name's link opens its document.
public class DocRefListController extends Controller {

    private DocRefList view;

    /// Made by GIN; [#attach(DocRefList)] gives it its list.
    ///
    /// @param eventBus The application's event bus.
    @Inject
    public DocRefListController(final EventBus eventBus) {
        super(eventBus);
    }

    /// Attaches this to a list, e.g. one a template declares. The list is then this controller's
    /// widget.
    ///
    /// @param view The list.
    /// @return This, for its owner to keep.
    public DocRefListController attach(final DocRefList view) {
        this.view = Objects.requireNonNull(view, "view");
        initWidget(view);
        view.setOnOpen(docRef -> OpenDocumentEvent.fire(this, docRef, true));
        return this;
    }

    /// Shows documents, the first selected.
    ///
    /// @param docRefs The documents.
    public void setData(final List<DocRef> docRefs) {
        view.setData(docRefs);
    }

    /// @return The selection.
    public MultiSelectionModel<DocRef> getSelectionModel() {
        return view.getSelectionModel();
    }

    /// @param tableName The list's name, for assistive technology.
    public void setTableName(final String tableName) {
        view.setTableName(tableName);
    }

    /// Brings Add and Remove up to date with the selection.
    ///
    /// @param readOnly Whether the dictionary is read only.
    public void updateActions(final boolean readOnly) {
        view.updateActions(readOnly);
    }

    /// @param onAdd Run when Add is pressed.
    public void setOnAdd(final Runnable onAdd) {
        view.setOnAdd(onAdd);
    }

    /// @param onRemove Run when Remove is pressed.
    public void setOnRemove(final Runnable onRemove) {
        view.setOnRemove(onRemove);
    }
}
