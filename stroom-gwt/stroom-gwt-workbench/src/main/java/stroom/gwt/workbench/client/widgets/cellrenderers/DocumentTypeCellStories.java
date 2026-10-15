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

package stroom.gwt.workbench.client.widgets.cellrenderers;

import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.table.client.MyCellTable;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeGroup;
import stroom.docstore.shared.DocumentTypeRegistry;
import stroom.explorer.client.presenter.DocumentTypeCell;
import stroom.explorer.client.presenter.DocumentTypeSelectionModel;
import stroom.gwt.workbench.framework.client.args.ArgType;
import stroom.gwt.workbench.framework.client.args.Args;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.svg.shared.SvgImage;
import stroom.widget.util.client.CheckListSelectionEventManager;
import stroom.widget.util.client.MySingleSelectionModel;

import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Stories for Stroom's [DocumentTypeCell].
///
/// The cell is shown as Stroom's type filter (`TypeFilterPresenter`) shows it: as the one column of
/// a [MyCellTable] with the `menuCellTable` class, one row per type, whose tick boxes are toggled by
/// a [CheckListSelectionEventManager]. The cell's [DocumentTypeSelectionModel] gives each type's
/// tick box state; a null state renders no tick box.
public final class DocumentTypeCellStories {

    // Arg names
    private static final String DOCUMENT_TYPE = "documentType";
    private static final String ON_TOGGLE = "onToggle";

    private DocumentTypeCellStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Cell Renderers/DocumentTypeCell", DocumentTypeCellStories.class)
                .layout(StoryLayout.PADDED)
                .argType(ArgType.text(DOCUMENT_TYPE)
                        .description("The document type's key, resolved with DocumentTypeRegistry.get(...).")
                        .typeName("DocumentType | string"))
                // No tick state, toggle or divider args: the tick box state comes from the selection
                // model (see WithTickBoxes), and the divider is only drawn
                // for TypeFilterPresenter's own 'Select all or none' type
                .args(Args.of(DOCUMENT_TYPE, "Feed"))
                // A single document type resolved from its API type-key string
                .story("SingleType", DocumentTypeCellStories::fromArgs)
                .withArgs(Args.of(DOCUMENT_TYPE, "Pipeline"))
                // Several document types rendered as they would appear in the picker list
                .story("SeveralTypes", context -> typeList(context, untickable(
                        "Feed", "Dictionary", "Pipeline", "Folder", "XSLT", "Index", "Dashboard", "Query")))
                // Passing a resolved DocumentType object rather than a string key
                .story("FromDocumentTypeObject", context -> {
                    final Map<DocumentType, TickBoxState> states = new LinkedHashMap<>();
                    states.put(DocumentTypeRegistry.ANALYTIC_RULE_DOCUMENT_TYPE, null);
                    return typeList(context, states);
                })
                // An unknown type-key falls back to the generic icon and the raw label
                .story("UnknownType", DocumentTypeCellStories::fromArgs)
                .withArgs(Args.of(DOCUMENT_TYPE, "SomethingUnregistered"))
                // With embedded tick-boxes (the type-filter picker's selection model)
                .story("WithTickBoxes", context -> {
                    final Map<DocumentType, TickBoxState> states = new LinkedHashMap<>();
                    states.put(resolve("Feed"), TickBoxState.TICK);
                    states.put(resolve("Pipeline"), TickBoxState.UNTICK);
                    states.put(resolve("Folder"), TickBoxState.UNTICK);
                    return typeList(context, states, true);
                });
    }

    /// A cell made from the story's args, so the Controls addon changes it.
    private static Widget fromArgs(final StoryContext context) {
        final String key = context.getArgs().getString(DOCUMENT_TYPE, "Feed");
        return typeList(context, untickable(key));
    }

    /// The types, in order, with no tick boxes.
    private static Map<DocumentType, TickBoxState> untickable(final String... keys) {
        final Map<DocumentType, TickBoxState> states = new LinkedHashMap<>();
        for (final String key : keys) {
            states.put(resolve(key), null);
        }
        return states;
    }

    private static Widget typeList(final StoryContext context, final Map<DocumentType, TickBoxState> states) {
        return typeList(context, states, false);
    }

    /// Resolves a type's key.
    private static DocumentType resolve(final String key) {
        final DocumentType documentType = DocumentTypeRegistry.get(key);
        if (documentType != null) {
            return documentType;
        }
        // DocumentTypeRegistry has no fallback for an unknown key (Stroom never shows one), so the
        // story makes one: the searchable icon and the key as its name
        return new DocumentType(DocumentTypeGroup.SEARCH, key, key, SvgImage.DOCUMENT_SEARCHABLE);
    }

    /// The types in a [MyCellTable], as `TypeFilterPresenter` shows them.
    ///
    /// @param states    Each type's tick box state, or null for no tick box.
    /// @param toggleable True if a click toggles a type's tick box, reporting it to `onToggle`.
    private static Widget typeList(final StoryContext context,
                                   final Map<DocumentType, TickBoxState> states,
                                   final boolean toggleable) {
        final Spy onToggle = context.fn(ON_TOGGLE);
        // In the order the types were given in
        final List<DocumentType> types = new ArrayList<>(states.keySet());

        final MyCellTable<DocumentType> cellTable = new MyCellTable<>(MyDataGrid.DEFAULT_LIST_PAGE_SIZE);
        cellTable.getElement().setClassName("menuCellTable");
        final DocumentTypeSelectionModel selectionModel = states::get;
        cellTable.addColumn(new Column<DocumentType, DocumentType>(new DocumentTypeCell(selectionModel)) {
            @Override
            public DocumentType getValue(final DocumentType documentType) {
                return documentType;
            }
        });
        cellTable.setSkipRowHoverCheck(true);
        cellTable.setSelectionModel(new MySingleSelectionModel<>(), new ToggleManager(
                cellTable,
                documentType -> {
                    if (toggleable) {
                        final TickBoxState state = TickBoxState.TICK.equals(states.get(documentType))
                                ? TickBoxState.UNTICK
                                : TickBoxState.TICK;
                        states.put(documentType, state);
                        onToggle.call(documentType.getType(), state.name());
                        cellTable.redraw();
                    }
                }));
        cellTable.setRowData(0, types);
        cellTable.setRowCount(types.size());
        return cellTable;
    }

    // --------------------------------------------------------------------------------

    /// Toggles a type's tick box on a click, as `TypeFilterPresenter`'s selection event manager does.
    private static final class ToggleManager extends CheckListSelectionEventManager<DocumentType> {

        private final ToggleHandler handler;

        private ToggleManager(final CellTable<DocumentType> cellTable, final ToggleHandler handler) {
            super(cellTable);
            this.handler = handler;
        }

        @Override
        protected void onToggle(final DocumentType item) {
            handler.onToggle(item);
        }
    }

    // --------------------------------------------------------------------------------

    /// Called when a type is toggled.
    private interface ToggleHandler {

        void onToggle(DocumentType documentType);
    }
}
