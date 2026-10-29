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
import stroom.widget.button.client.Toolbar;
import stroom.widget.component.client.Controller;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;

/// Documents ([DocRefList]), a row each; a name's link opens its document.
public class DocRefListController extends Controller {

    private final DocRefList view;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     The content.
    @Inject
    public DocRefListController(final EventBus eventBus,
                                final DocRefList view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        view.setOnOpen(docRef -> OpenDocumentEvent.fire(this, docRef, true));
    }

    /// Adds the list's one column, of the documents' names.
    ///
    /// @param columnName  The column's heading.
    /// @param hasOpenLink Whether a name is a link that opens its document.
    public void initTableColumns(final String columnName, final boolean hasOpenLink) {
        view.addColumn(columnName, hasOpenLink);
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

    /// @return The toolbar beside the pager, for the owner's buttons.
    public Toolbar getToolbar() {
        return view.getToolbar();
    }
}
