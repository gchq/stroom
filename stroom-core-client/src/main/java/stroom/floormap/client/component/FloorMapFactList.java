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

package stroom.floormap.client.component;

import stroom.floormap.client.FloorMapAria;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.button.client.ToolbarToggleButton;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.TextColumn;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.ListDataProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/// The floor map editor's Fact List panel: a row per fact (its key, type and name), which can be
/// several selected, and the actions on them. The grid is the editor canvas's text alternative, so
/// it has an id of its own ([#getGridElementId()]) for the canvas to name it by.
public class FloorMapFactList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    ToolbarButton addButton;
    @UiField
    ToolbarButton endButton;
    @UiField
    ToolbarButton deleteButton;
    @UiField
    ToolbarToggleButton showAllButton;
    @UiField
    ToolbarButton helpButton;
    @UiField
    DataGrid<FactListRow> dataGrid;

    private final ListDataProvider<FactListRow> dataProvider = new ListDataProvider<>();
    private final MultiSelectionModelImpl<FactListRow> selectionModel;
    // Minted for each panel: two floor maps open at once mustn't share an id, or each canvas's
    // aria-describedby would name whichever came first in the page
    private final String gridElementId = FloorMapAria.uniqueId("floormap-fact-list-grid");

    private Consumer<List<FactListRow>> onSelectionChange = selected -> {
    };
    private Runnable onAdd = () -> {
    };
    private Consumer<String> onEnd = key -> {
    };
    private Consumer<String> onDelete = key -> {
    };
    private Consumer<Boolean> onShowAll = showAll -> {
    };

    /// Makes an empty panel.
    public FloorMapFactList() {
        initWidget(BINDER.createAndBindUi(this));
        // Several can be selected: ctrl/shift-click extends the selection
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        dataGrid.getElement().setId(gridElementId);
        addColumns();
        dataProvider.addDataDisplay(dataGrid);
        addButton.setOnClick(() -> onAdd.run());
        endButton.setOnClick(() -> withSelectedKey(onEnd));
        deleteButton.setOnClick(() -> withSelectedKey(onDelete));
        showAllButton.setOnToggle(showAll -> onShowAll.accept(showAll));
        selectionModel.addSelectionHandler(e -> onSelection());
    }

    /// @return The grid's element id, for the canvas to point `aria-describedby` at.
    public String getGridElementId() {
        return gridElementId;
    }

    /// @return The help button, for its controller to show the help from.
    public ToolbarButton getHelpButton() {
        return helpButton;
    }

    /// Shows the facts in place of those shown. The selection isn't changed: follow with
    /// [#setSelectedKeys(Collection)] as needed.
    ///
    /// @param facts The facts.
    public void setFacts(final List<FactListRow> facts) {
        dataProvider.setList(facts);
        dataGrid.setRowData(0, facts);
    }

    /// Selects the facts with these keys, in place of those selected, without telling
    /// [#setOnSelectionChange(Consumer)] (a selection made elsewhere, e.g. on the canvas). Keys not
    /// listed are ignored; none clears the selection.
    ///
    /// @param keys The keys, or null for none.
    public void setSelectedKeys(final Collection<String> keys) {
        final List<FactListRow> list = dataProvider.getList();
        final List<FactListRow> toSelect = new ArrayList<>();
        if (list != null && keys != null && !keys.isEmpty()) {
            for (final FactListRow fact : list) {
                if (keys.contains(fact.getKey())) {
                    toSelect.add(fact);
                }
            }
        }
        selectionModel.setSelectedItems(toSelect);
    }

    /// @return The first fact selected, or null if none is.
    public FactListRow getSelectedFact() {
        return selectionModel.getSelected();
    }

    /// @param onSelectionChange Given every fact selected, each time the user changes the selection.
    public void setOnSelectionChange(final Consumer<List<FactListRow>> onSelectionChange) {
        this.onSelectionChange = onSelectionChange;
    }

    /// @param onAdd Run when Add New Object is pressed.
    public void setOnAdd(final Runnable onAdd) {
        this.onAdd = onAdd;
    }

    /// @param onEnd Given the selected fact's key when Delete From This Time is pressed.
    public void setOnEnd(final Consumer<String> onEnd) {
        this.onEnd = onEnd;
    }

    /// @param onDelete Given the selected fact's key when Erase History is pressed.
    public void setOnDelete(final Consumer<String> onDelete) {
        this.onDelete = onDelete;
    }

    /// @param onShowAll Told whether every fact is shown (Show All on) or only those at the time.
    public void setOnShowAll(final Consumer<Boolean> onShowAll) {
        this.onShowAll = onShowAll;
    }

    private void withSelectedKey(final Consumer<String> action) {
        final FactListRow selected = selectionModel.getSelected();
        if (selected != null) {
            action.accept(selected.getKey());
        }
    }

    private void onSelection() {
        final List<FactListRow> selected = selectionModel.getSelectedItems();
        final FactListRow primary = selectionModel.getSelected();
        // Delete From This Time acts on the first selected, which mustn't already be deleted (Show
        // All lists deleted facts so they can be found and restored, not deleted again)
        endButton.setEnabled(primary != null && !primary.isDeleted());
        deleteButton.setEnabled(!selected.isEmpty());
        onSelectionChange.accept(selected);
        if (primary != null) {
            scrollIntoView(primary);
        }
    }

    // The first selected row is scrolled into view once it is drawn
    private void scrollIntoView(final FactListRow fact) {
        final List<FactListRow> list = dataProvider.getList();
        final int index = list == null
                ? -1
                : list.indexOf(fact);
        if (index >= 0) {
            Scheduler.get().scheduleDeferred(() -> {
                if (index < dataGrid.getVisibleItemCount()) {
                    try {
                        final Element row = dataGrid.getRowElement(index);
                        if (row != null) {
                            ElementUtil.scrollIntoViewNearest(row);
                        }
                    } catch (final RuntimeException e) {
                        // Not scrolling into view does no harm
                    }
                }
            });
        }
    }

    private void addColumns() {
        dataGrid.addColumn(new TextColumn<>() {
            @Override
            public String getValue(final FactListRow fact) {
                return fact.getKey();
            }
        }, "Key");
        dataGrid.addColumn(new TextColumn<>() {
            @Override
            public String getValue(final FactListRow fact) {
                return fact.getType();
            }
        }, "Type");
        dataGrid.addColumn(new TextColumn<>() {
            @Override
            public String getValue(final FactListRow fact) {
                return fact.isDeleted()
                        ? fact.getName() + " (deleted)"
                        : fact.getName();
            }
        }, "Name");
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, FloorMapFactList> {

    }
}
