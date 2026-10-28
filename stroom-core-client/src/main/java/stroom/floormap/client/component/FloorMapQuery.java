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

import stroom.floormap.shared.FloorMapEventColumns;
import stroom.floormap.shared.FloorMapEventRole;
import stroom.item.client.SelectionBox;
import stroom.widget.component.client.Component;
import stroom.widget.text.client.Label;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/// A floor map's Events Query tab: the query (shown here by its controller), below the dropdowns
/// that choose which of its result columns hold each entity's id, location, location ref and type.
public class FloorMapQuery extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    /// The dropdown for each role.
    ///
    /// Built once from the `@UiField`s rather than looked up per call, so the mapping
    /// between a role and its control is stated in exactly one place. A role added to
    /// [FloorMapEventRole] without a field here fails on the first `get`, which is the
    /// failure mode to want: the alternative is a role that silently cannot be set.
    private final Map<FloorMapEventRole, SelectionBox<String>> boxesByRole =
            new EnumMap<>(FloorMapEventRole.class);

    @UiField
    SimplePanel queryEditContainer;
    @UiField
    FlowPanel columnMappingsContainer;
    @UiField
    SelectionBox<String> entityIdColumn;
    @UiField
    SelectionBox<String> locationColumn;
    @UiField
    SelectionBox<String> locationRefColumn;
    @UiField
    SelectionBox<String> typeColumn;
    @UiField
    Label columnsHint;

    private Runnable onColumnChange = () -> {
    };

    /// Makes the tab, with no query and no columns.
    public FloorMapQuery() {
        initWidget(BINDER.createAndBindUi(this));

        boxesByRole.put(FloorMapEventRole.ENTITY_ID, entityIdColumn);
        boxesByRole.put(FloorMapEventRole.LOCATION, locationColumn);
        boxesByRole.put(FloorMapEventRole.LOCATION_REF, locationRefColumn);
        boxesByRole.put(FloorMapEventRole.TYPE, typeColumn);

        boxesByRole.values().forEach(box -> box.addValueChangeHandler(e -> onColumnChange.run()));
    }

    /// @param queryEdit The query.
    public void setQueryEdit(final IsWidget queryEdit) {
        queryEditContainer.setWidget(queryEdit);
    }

    /// @param visible Whether the column dropdowns show.
    public void setColumnMappingsVisible(final boolean visible) {
        columnMappingsContainer.setVisible(visible);
    }

    /// @param visible Whether the hint to run the query, to list its columns, shows.
    public void setColumnsHintVisible(final boolean visible) {
        columnsHint.setVisible(visible);
    }

    /// Replaces the available items in every column-mapping dropdown with the given column names,
    /// preceded by an empty "none selected" entry.
    ///
    /// The empty entry is what makes a role *unmappable* as well as mappable, which
    /// matters for both location roles: a store whose events only carry fact keys has no coordinate
    /// column, and pointing the role at some other column would be worse than leaving it unset.
    ///
    /// @param columnNames The query's result columns.
    public void setAvailableColumns(final List<String> columnNames) {
        boxesByRole.values().forEach(box -> populateSelectionBox(box, columnNames));
    }

    private static void populateSelectionBox(final SelectionBox<String> box,
                                             final List<String> items) {
        box.clear();
        box.addItem("");
        if (items != null) {
            for (final String item : items) {
                box.addItem(item);
            }
        }
    }

    /// @param eventColumns Which columns hold each role, or null for none.
    public void setEventColumns(final FloorMapEventColumns eventColumns) {
        boxesByRole.forEach((role, box) -> {
            final String column = eventColumns == null ? null : eventColumns.getColumn(role);
            // The empty string, not null: it is the "none selected" item the box actually holds.
            box.setValue(column == null ? "" : column);
        });
    }

    /// Runs whenever the user changes any role's dropdown, so that the mapping marks the document
    /// changed on its own, not only with an edit to the query.
    ///
    /// @param onColumnChange What to run.
    public void setOnColumnChange(final Runnable onColumnChange) {
        this.onColumnChange = onColumnChange;
    }

    /// @return Which columns hold each role.
    public FloorMapEventColumns getEventColumns() {
        FloorMapEventColumns columns = new FloorMapEventColumns(null);
        for (final Map.Entry<FloorMapEventRole, SelectionBox<String>> entry : boxesByRole.entrySet()) {
            columns = columns.with(entry.getKey(), entry.getValue().getValue());
        }
        return columns;
    }



    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, FloorMapQuery> {

    }
}
