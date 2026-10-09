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

package stroom.floormap.client.view;

import stroom.floormap.client.FloorMapAria;
import stroom.floormap.client.presenter.FloorMapQueryPresenter.FloorMapQueryView;
import stroom.floormap.shared.FloorMapEventColumns;
import stroom.floormap.shared.FloorMapEventRole;
import stroom.item.client.SelectionBox;
import stroom.util.client.Console;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

/// View implementation for the floor map query configuration panel.
///
/// Embeds the standard query editor and exposes column-mapping dropdowns that
/// let the user select which result columns should be used as the entity ID and
/// location ID when plotting facts on the floor map.
public class FloorMapQueryViewImpl extends ViewImpl implements FloorMapQueryView {

    private final Widget widget;

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

    @Inject
    public FloorMapQueryViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        boxesByRole.put(FloorMapEventRole.ENTITY_ID, entityIdColumn);
        boxesByRole.put(FloorMapEventRole.LOCATION, locationColumn);
        boxesByRole.put(FloorMapEventRole.LOCATION_REF, locationRefColumn);
        boxesByRole.put(FloorMapEventRole.TYPE, typeColumn);

        // The FormGroups around these carry identity="..." and a visible label, but SelectionBox is
        // a composite whose root is a wrapper div - so setIdentity() puts the id on the wrapper and
        // the <label for> resolves to a non-labelable element, naming nothing. Every box announced
        // as unnamed. Name the inner input directly instead; the label text is duplicated here
        // deliberately, since the FormGroup's copy cannot reach it.
        boxesByRole.forEach((role, box) -> {
            final String label = role.getDisplayName() + " Column";
            if (!FloorMapAria.labelInnerControl(box, label)) {
                Console.error("Floor map: could not find the input inside the '" + label
                              + "' selection box to give it an accessible name.");
            }
        });
    }

    @Override
    public void setQueryEditView(final View view) {
        queryEditContainer.setWidget(view.asWidget());
    }

    @Override
    public void setColumnMappingsVisible(final boolean visible) {
        columnMappingsContainer.setVisible(visible);
    }

    @Override
    public void setColumnsHintVisible(final boolean visible) {
        columnsHint.setVisible(visible);
    }

    /// Replaces the available items in every column-mapping dropdown with the given column names,
    /// preceded by an empty "none selected" entry.
    ///
    /// The empty entry is what makes a role *unmappable* as well as mappable, which
    /// matters for both location roles: a store whose events only carry fact keys has no coordinate
    /// column, and pointing the role at some other column would be worse than leaving it unset.
    @Override
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

    @Override
    public void setEventColumns(final FloorMapEventColumns eventColumns) {
        boxesByRole.forEach((role, box) -> {
            final String column = eventColumns == null ? null : eventColumns.getColumn(role);
            // The empty string, not null: it is the "none selected" item the box actually holds.
            box.setValue(column == null ? "" : column);
        });
    }

    /// Registers a handler notified whenever the user changes any role's dropdown.
    ///
    /// Without this the mapping was **unsaveable on its own**: the tab marks the document
    /// dirty from `addChangeHandler`, which only tracks the *query editor*, so changing
    /// a dropdown left the save icon disabled and the edit was lost on the next tab switch. It
    /// persisted only as a passenger on an unrelated query-text edit, which is worse than not
    /// working — it worked sometimes.
    ///
    /// @param handler run on each change; `null` to remove
    @Override
    public void setColumnChangeHandler(final Runnable handler) {
        //noinspection unused e
        boxesByRole.values().forEach(box -> box.addValueChangeHandler(e -> {
            if (handler != null) {
                handler.run();
            }
        }));
    }

    @Override
    public FloorMapEventColumns getEventColumns() {
        FloorMapEventColumns columns = new FloorMapEventColumns(null);
        for (final Map.Entry<FloorMapEventRole, SelectionBox<String>> entry : boxesByRole.entrySet()) {
            columns = columns.with(entry.getKey(), entry.getValue().getValue());
        }
        return columns;
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    public interface Binder extends UiBinder<Widget, FloorMapQueryViewImpl> {}
}
