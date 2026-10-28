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

package stroom.security.client.component;

import stroom.cell.info.client.CellIcon;
import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.client.ColumnSizeConstants;
import stroom.docstore.shared.DocumentType;
import stroom.util.client.DataGridUtil;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.DataGridSelectionEventManager;
import stroom.widget.text.client.Html;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.Header;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/// The document types a user or group may create in a folder, a row each with a tick box (ticked
/// for an explicit permission, half ticked for one implied or inherited), and below them how the
/// selected type's permission is held.
public class DocumentCreatePermissionsList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    DataGrid<DocumentType> dataGrid;
    @UiField
    Html details;

    private final MultiSelectionModelImpl<DocumentType> selectionModel;
    private Function<DocumentType, TickBoxState> permissionState = documentType -> TickBoxState.UNTICK;
    private Supplier<TickBoxState> allState = () -> TickBoxState.UNTICK;
    private BiConsumer<DocumentType, Boolean> onTick = (documentType, ticked) -> {
    };
    private Consumer<Boolean> onTickAll = ticked -> {
    };

    /// Makes an empty list, with no columns until [#addColumns(List, boolean)].
    public DocumentCreatePermissionsList() {
        initWidget(BINDER.createAndBindUi(this));
        selectionModel = new MultiSelectionModelImpl<>();
        dataGrid.setSelectionModel(selectionModel,
                new DataGridSelectionEventManager<>(dataGrid, selectionModel, false));
    }

    /// Adds the columns: a tick box, the type's icon and its name.
    ///
    /// @param documentTypes The types, a row each, to name each row's tick box.
    /// @param updateable    Whether the tick boxes can be changed; if so the header has one too, for
    ///                      'Create All'.
    public void addColumns(final List<DocumentType> documentTypes, final boolean updateable) {
        final TickBoxCell.Appearance appearance = updateable
                ? new TickBoxCell.DefaultAppearance()
                : new TickBoxCell.NoBorderAppearance();

        // Each tick box is named for its row's permission, e.g. "Create Pipeline"
        final TickBoxCell tickBoxCell = TickBoxCell.create(appearance, true, true, updateable)
                .withNameForRow(index -> index < documentTypes.size()
                        ? "Create " + documentTypes.get(index).getDisplayType()
                        : null);
        final Column<DocumentType, TickBoxState> selectionColumn = new Column<DocumentType, TickBoxState>(
                tickBoxCell) {
            @Override
            public TickBoxState getValue(final DocumentType documentType) {
                return permissionState.apply(documentType);
            }
        };

        if (updateable) {
            final Header<TickBoxState> header = new Header<TickBoxState>(TickBoxCell.create(false, false)) {
                @Override
                public TickBoxState getValue() {
                    return allState.get();
                }
            };
            header.setUpdater(value -> {
                if (TickBoxState.UNTICK.equals(value)) {
                    onTickAll.accept(false);
                } else if (TickBoxState.TICK.equals(value)) {
                    onTickAll.accept(true);
                }
            });
            selectionColumn.setFieldUpdater((index, documentType, value) ->
                    onTick.accept(documentType, TickBoxState.TICK.equals(value)));
            dataGrid.addColumn(selectionColumn, header, ColumnSizeConstants.CHECKBOX_COL);
        } else {
            dataGrid.addColumn(selectionColumn, "<br/>", ColumnSizeConstants.ICON_COL);
        }

        final Column<DocumentType, CellIcon> iconColumn = DataGridUtil.cellIconColumnBuilder(
                false,
                DocumentCreatePermissionsList::getDocTypeIcon).build();
        iconColumn.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_CENTER);
        dataGrid.addColumn(iconColumn, "<br/>", ColumnSizeConstants.ICON_COL);

        dataGrid.addAutoResizableColumn(new Column<DocumentType, String>(new TextCell()) {
            @Override
            public String getValue(final DocumentType documentType) {
                return documentType.getDisplayType();
            }
        }, "Document Type", 200);
    }

    /// @param documentTypes The rows, drawn again; none if `null`.
    public void setRows(final List<DocumentType> documentTypes) {
        if (documentTypes != null) {
            dataGrid.setRowData(0, documentTypes);
            dataGrid.setRowCount(documentTypes.size());
        } else {
            dataGrid.setRowCount(0);
        }
    }

    /// @param details How the selected type's permission is held.
    public void setDetails(final SafeHtml details) {
        this.details.setHTML(details);
    }

    /// @return The selected type, or `null`.
    public DocumentType getSelected() {
        return selectionModel.getSelected();
    }

    /// @param permissionState Gives a row's tick box state.
    public void setPermissionState(final Function<DocumentType, TickBoxState> permissionState) {
        this.permissionState = permissionState;
    }

    /// @param allState Gives the header's tick box state.
    public void setAllState(final Supplier<TickBoxState> allState) {
        this.allState = allState;
    }

    /// @param onTick Given a row's type and whether its box is now ticked, when it is pressed.
    public void setOnTick(final BiConsumer<DocumentType, Boolean> onTick) {
        this.onTick = onTick;
    }

    /// @param onTickAll Given whether the header's box is now ticked, when it is pressed.
    public void setOnTickAll(final Consumer<Boolean> onTickAll) {
        this.onTickAll = onTickAll;
    }

    /// @param onSelectionChange Run when another type is selected.
    public void setOnSelectionChange(final Runnable onSelectionChange) {
        selectionModel.addSelectionHandler(event -> onSelectionChange.run());
    }

    private static CellIcon getDocTypeIcon(final DocumentType documentType) {
        if (documentType != null) {
            return CellIcon.of(documentType.getIcon(), documentType.getDisplayType(), true);
        }
        return null;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, DocumentCreatePermissionsList> {

    }
}
