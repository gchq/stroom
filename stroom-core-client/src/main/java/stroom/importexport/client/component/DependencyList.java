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

package stroom.importexport.client.component;

import stroom.cell.info.client.ActionCell;
import stroom.cell.info.client.CellIcon;
import stroom.cell.info.client.CommandLink;
import stroom.data.client.ColumnSizeConstants;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeRegistry;
import stroom.importexport.shared.Dependency;
import stroom.importexport.shared.DependencyCriteria;
import stroom.svg.shared.SvgImage;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.popup.client.PopupPosition;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.ColumnSortList;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.AbstractDataProvider;
import com.google.gwt.view.client.Range;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/// The dependencies between documents, a row each (the document that depends and the one it depends
/// on, each with its type, a link to open it and an action menu, and whether the one depended on is
/// there), sorted by a column.
public class DependencyList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final Template TEMPLATE = GWT.create(Template.class);
    private static final int COL_WIDTH_TYPE = 120;
    private static final int COL_WIDTH_NAME = 300;

    @UiField
    PagerPanel pager;
    @UiField
    DataGrid<Dependency> dataGrid;

    private Set<String> openableTypes = new HashSet<>();
    private Consumer<DocRef> onOpen = docRef -> {
    };
    private ActionMenuHandler onShowActionMenu = (docRef, popupPosition, opener) -> {
    };
    private Runnable onSortChange = () -> {
    };

    /// Makes an empty list.
    public DependencyList() {
        initWidget(BINDER.createAndBindUi(this));
        // Rows keyed by the documents they link
        dataGrid.setKeyProvider(row -> NullSafe.get(row.getFrom(), DocRef::getUuid)
                                       + ">"
                                       + NullSafe.get(row.getTo(), DocRef::getUuid));
        addColumns();
        dataGrid.addColumnSortHandler(event -> onSortChange.run());
    }

    /// @param dataProvider Gives the list its rows, a page at a time.
    public void setDataProvider(final AbstractDataProvider<Dependency> dataProvider) {
        dataProvider.addDataDisplay(dataGrid);
    }

    /// @return The task monitor that spins the pager's refresh button.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }

    /// @return How the rows are sorted, for the request for them.
    public ColumnSortList getSortList() {
        return dataGrid.getColumnSortList();
    }

    /// Goes back to the first page.
    public void resetRange() {
        dataGrid.setVisibleRange(new Range(0, PageRequest.DEFAULT_PAGE_LENGTH));
    }

    /// @param openableTypes The document types that can be opened, so a link to one depended on.
    public void setOpenableTypes(final Set<String> openableTypes) {
        this.openableTypes = openableTypes;
    }

    /// @param onOpen Given a document to open, when its link is pressed.
    public void setOnOpen(final Consumer<DocRef> onOpen) {
        this.onOpen = onOpen;
    }

    /// @param onShowActionMenu Shows a document's action menu, when its action button is pressed.
    public void setOnShowActionMenu(final ActionMenuHandler onShowActionMenu) {
        this.onShowActionMenu = onShowActionMenu;
    }

    /// @param onSortChange Run when the user sorts by another column.
    public void setOnSortChange(final Runnable onSortChange) {
        this.onSortChange = onSortChange;
    }

    private void addColumns() {

        // From (Icon)
        dataGrid.addColumn(DataGridUtil.cellIconColumnBuilder(false, (Dependency row) ->
                                getDocTypeIcon(row.getFrom()))
                        .build(),
                "<br/>",
                ColumnSizeConstants.ICON_COL);

        // From (Type)
        dataGrid.addAutoResizableColumn(DataGridUtil.textColumnBuilder((Dependency row) ->
                                getValue(row, Dependency::getFrom, DocRef::getType))
                        .withSorting(DependencyCriteria.FIELD_FROM_TYPE, true)
                        .build(),
                DependencyCriteria.FIELD_FROM_TYPE,
                COL_WIDTH_TYPE);

        // From (Name)
        final Column<Dependency, CommandLink> fromNameColumn = DataGridUtil.commandLinkColumnBuilder(
                        (Dependency row) ->
                                getName(row, Dependency::getFrom, true))
                .withSorting(DependencyCriteria.FIELD_FROM_NAME, true)
                .build();
        DataGridUtil.addCommandLinkFieldUpdater(fromNameColumn);
        dataGrid.addAutoResizableColumn(fromNameColumn,
                DependencyCriteria.FIELD_FROM_NAME,
                COL_WIDTH_NAME);

        // From (action menu))
        addActionButtonColumn(Dependency::getFrom, "", 40);

        // To (Icon)
        dataGrid.addColumn(DataGridUtil.cellIconColumnBuilder(false, (Dependency row) ->
                                getDocTypeIcon(row.getTo()))
                        .build(),
                "<br/>",
                ColumnSizeConstants.ICON_COL);

        // To (Type)
        dataGrid.addAutoResizableColumn(DataGridUtil.textColumnBuilder((Dependency row) ->
                                getValue(row, Dependency::getTo, DocRef::getType))
                        .withSorting(DependencyCriteria.FIELD_TO_TYPE, true)
                        .build(),
                DependencyCriteria.FIELD_TO_TYPE,
                COL_WIDTH_TYPE);

        // To (Name)
        final Column<Dependency, CommandLink> toNameColumn = DataGridUtil.commandLinkColumnBuilder((Dependency row) ->
                        getName(row, Dependency::getTo, false))
                .withSorting(DependencyCriteria.FIELD_TO_NAME, true)
                .build();
        DataGridUtil.addCommandLinkFieldUpdater(toNameColumn);
        dataGrid.addAutoResizableColumn(toNameColumn,
                DependencyCriteria.FIELD_TO_NAME,
                COL_WIDTH_NAME);

        // To (action menu)
        addActionButtonColumn(Dependency::getTo, "", 40);

        // Status
        dataGrid.addResizableColumn(DataGridUtil.htmlColumnBuilder(this::getStatusValue)
                        .withSorting(DependencyCriteria.FIELD_STATUS, false)
                        .centerAligned()
                        .build(),
                DataGridUtil.createCenterAlignedHeader(DependencyCriteria.FIELD_STATUS),
                60);
    }

    private void addActionButtonColumn(final Function<Dependency, DocRef> docRefSelector,
                                       final String name,
                                       final int width) {
        final ActionCell<DocRef> actionCell = new ActionCell<>(this::showActionMenu,
                DependencyList::getActionMenuName);
        final Column<Dependency, DocRef> actionColumn = DataGridUtil.columnBuilder(
                docRefSelector,
                () -> actionCell
        ).build();
        dataGrid.addColumn(actionColumn, name, width);
    }

    // Shown as Stroom's other menus are, so that it is named and takes the focus
    private void showActionMenu(final DocRef docRef, final NativeEvent event) {
        final PopupPosition popupPosition = new PopupPosition(event.getClientX() + 10, event.getClientY());
        final Element opener = Element.is(event.getEventTarget())
                ? Element.as(event.getEventTarget())
                : null;
        onShowActionMenu.show(docRef, popupPosition, opener);
    }

    /// @param docRef A document.
    /// @return The name of its actions (its button's and its menu's).
    public static String getActionMenuName(final DocRef docRef) {
        return "Actions for " + getDisplayType(docRef) + " '" + docRef.getName() + "'";
    }

    // A document type as users see it, e.g. 'XSLT Translation'
    private static String getDisplayType(final DocRef docRef) {
        final DocumentType documentType = DocumentTypeRegistry.get(docRef.getType());
        return NullSafe.getOrElse(documentType, DocumentType::getDisplayType, docRef.getType());
    }

    private CommandLink getName(final Dependency row,
                                final Function<Dependency, DocRef> docRefExtractor,
                                final boolean from) {
        final DocRef docRef = docRefExtractor.apply(row);
        if (docRef != null) {
            if (from || (openableTypes.contains(docRef.getType()) && row.isOk())) {
                final String name = Objects.requireNonNullElseGet(docRef.getName(),
                        docRef::getUuid);
                return new CommandLink(
                        docRef.getName(),
                        "Open " + docRef.getType() + " '" + name + "'",
                        () -> onOpen.accept(docRef));
            } else {
                return CommandLink.withoutCommand(docRef.getName());
            }
        } else {
            return null;
        }
    }

    private String getValue(final Dependency row,
                            final Function<Dependency, DocRef> docRefExtractor,
                            final Function<DocRef, String> valueExtractor) {
        final DocRef docRef = docRefExtractor.apply(row);
        if (docRef != null) {
            return valueExtractor.apply(docRef);
        } else {
            return null;
        }
    }

    private CellIcon getDocTypeIcon(final DocRef docRef) {
        if (docRef != null && docRef.getType() != null && !docRef.getType().isEmpty()) {
            final SvgImage icon = DocumentTypeRegistry.getIcon(docRef.getType());
            if (icon != null) {
                return CellIcon.of(icon, docRef.getType(), true);
            } else {
                return CellIcon.of(SvgImage.ALERT, "Unknown Document Type");
            }
        } else {
            return CellIcon.of(SvgImage.ALERT, "Unknown Document Type");
        }
    }

    private SafeHtml getStatusValue(final Dependency row) {
        return row.isOk()
                ? TEMPLATE.status("dependency-list__status dependency-list__status--ok", "OK")
                : TEMPLATE.status("dependency-list__status dependency-list__status--missing", "Missing");
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, DependencyList> {

    }

    // A dependency's status, OK or missing
    interface Template extends SafeHtmlTemplates {

        @Template("<span class=\"{0}\">{1}</span>")
        SafeHtml status(String className, String value);
    }

    /// Shows a document's action menu.
    public interface ActionMenuHandler {

        /// @param docRef        The document.
        /// @param popupPosition Where to show it.
        /// @param opener        The action button pressed, or `null`.
        void show(DocRef docRef, PopupPosition popupPosition, Element opener);
    }
}
