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

import stroom.data.client.ColumnSizeConstants;
import stroom.data.client.component.UserRefCell;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeRegistry;
import stroom.security.client.UsersAndGroupsHelper;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.DocumentPermissionFields;
import stroom.security.shared.DocumentUserPermissions;
import stroom.security.shared.UserFields;
import stroom.svg.shared.SvgImage;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;
import stroom.util.shared.UserRef.DisplayType;
import stroom.widget.button.client.Toolbar;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.ColumnBuilder;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.quickfilter.client.QuickFilterContextHandler;
import stroom.widget.quickfilter.client.QuickFilterPage;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.Cell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.ColumnSortList;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.AbstractDataProvider;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/// The users and groups with permissions on a document, a row each (whether it is a user or a group,
/// its name, its explicit and effective permissions and, for a folder, the document types it may
/// create), sorted by a column; one can be selected. A quick filter above them, and a pager with a
/// toolbar for the screen's own buttons.
public class DocumentUserPermissionsList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    QuickFilterPage page;
    @UiField
    PagerPanel pager;
    @UiField
    DataGrid<DocumentUserPermissions> dataGrid;

    private final MultiSelectionModelImpl<DocumentUserPermissions> selectionModel;
    private Runnable onSortChange = () -> {
    };

    /// Makes an empty list, with no columns until [#addColumns].
    public DocumentUserPermissionsList() {
        initWidget(BINDER.createAndBindUi(this));
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        dataGrid.addColumnSortHandler(event -> onSortChange.run());
    }

    /// Adds the columns.
    ///
    /// @param userCell        The user cell's builder, configured to open its users.
    /// @param showCreateTypes Whether to show the document types each may create (for a folder).
    /// @param docTypeCount    How many document types there are, to show 'ALL' for all of them.
    public void addColumns(final UserRefCell.Builder<DocumentUserPermissions> userCell,
                           final boolean showCreateTypes,
                           final int docTypeCount) {
        // Icon
        dataGrid.addColumn(
                DataGridUtil.cellIconColumnBuilder(false, (DocumentUserPermissions row) ->
                                UsersAndGroupsHelper.mapUserRefTypeToIcon(row.getUserRef()))
                        .withSorting(UserFields.FIELD_IS_GROUP)
                        .centerAligned()
                        .enabledWhen(this::isUserEnabled)
                        .build(),
                DataGridUtil.headingBuilder("")
                        .headingText(UsersAndGroupsHelper.buildUserAndGroupIconHeader())
                        .centerAligned()
                        .withToolTip("Whether this row is a single user or a named user group.")
                        .build(),
                (ColumnSizeConstants.ICON_COL * 2) + 20);

        // User Or Group Name
        final Column<DocumentUserPermissions, DocumentUserPermissions> displayNameCol =
                new ColumnBuilder<DocumentUserPermissions, DocumentUserPermissions, Cell<DocumentUserPermissions>>(
                        Function.identity(),
                        () -> userCell
                                .showIcon(false)
                                .displayType(DisplayType.DISPLAY_NAME)
                                .userRefFunction(DocumentUserPermissions::getUserRef)
                                .build())
                        .withSorting(UserFields.FIELD_DISPLAY_NAME, true)
                        .enabledWhen(this::isUserEnabled)
                        .build();
        dataGrid.sort(displayNameCol);

        dataGrid.addResizableColumn(
                displayNameCol,
                DataGridUtil.headingBuilder(UsersAndGroupsHelper.COL_NAME_DISPLAY_NAME)
                        .withToolTip("The name of the user or group.")
                        .build(),
                ColumnSizeConstants.USER_DISPLAY_NAME_COL);

        // Explicit Permission
        dataGrid.addColumn(
                DataGridUtil.textColumnBuilder((DocumentUserPermissions row) ->
                                NullSafe.get(
                                        row,
                                        DocumentUserPermissions::getPermission,
                                        DocumentPermission::getDisplayValue))
                        .withSorting(DocumentPermissionFields.FIELD_EXPLICIT_DOC_PERMISSION)
                        .build(),
                DataGridUtil.headingBuilder("Explicit Permission")
                        .withToolTip("The explicit permission held on this document by the user/group. "
                                     + "Ignores any inherited permissions.")
                        .build(),
                150);

        // Effective Permission
        dataGrid.addColumn(
                DataGridUtil.textColumnBuilder(
                                (final DocumentUserPermissions row) -> {
                                    final DocumentPermission explicit = row.getPermission();
                                    final DocumentPermission inherited = row.getInheritedPermission();
                                    return NullSafe.get(DocumentPermission.highest(explicit, inherited),
                                            DocumentPermission::getDisplayValue);
                                })
                        .withSorting(DocumentPermissionFields.FIELD_EFFECTIVE_DOC_PERMISSION)
                        .build(),
                DataGridUtil.headingBuilder("Effective Permission")
                        .withToolTip("The highest effective permission held on this document by the user/group. "
                                     + "The permission may be explicit or inherited.")
                        .build(),
                160);

        if (showCreateTypes) {
            // Explicit doc create types
            dataGrid.addResizableColumn(
                    DataGridUtil.safeHtmlColumn((DocumentUserPermissions row) ->
                            permissionsToExplicitTypeIcons(row, docTypeCount)),
                    DataGridUtil.headingBuilder("Explicit Create Document Types")
                            .withToolTip("The document types that this user/group has explicit permission to create. " +
                                         "Ignores inherited permissions. 'ALL' will be displayed if the user/group " +
                                         "has permission on all document types.")
                            .build(),
                    400);

            // Effective doc create types
            dataGrid.addResizableColumn(
                    DataGridUtil.safeHtmlColumn((DocumentUserPermissions row) ->
                            permissionsToEffectiveTypeIcons(row, docTypeCount)),
                    DataGridUtil.headingBuilder("Effective Create Document Types")
                            .withToolTip("The document types that this user/group has permission to create. " +
                                         "Includes both explicit and inherited permissions. 'ALL' will be " +
                                         "displayed if the user/group has permission on all document types.")
                            .build(),
                    400);
        }
    }

    private boolean isUserEnabled(final DocumentUserPermissions documentUserPermissions) {
        return NullSafe.get(documentUserPermissions, DocumentUserPermissions::getUserRef, UserRef::isEnabled);
    }

    private static SafeHtml permissionsToExplicitTypeIcons(final DocumentUserPermissions row,
                                                   final int docTypeCount) {
        final Set<String> explicit = row
                .getDocumentCreatePermissions();
        return permissionsToTypeIcons(explicit, docTypeCount);
    }

    private static SafeHtml permissionsToEffectiveTypeIcons(final DocumentUserPermissions row,
                                                    final int docTypeCount) {
        final Set<String> effective = new HashSet<>(NullSafe.set(row
                .getDocumentCreatePermissions()));
        effective.addAll(NullSafe.set(row.getInheritedDocumentCreatePermissions()));

        return permissionsToTypeIcons(effective, docTypeCount);
    }

    private static SafeHtml permissionsToTypeIcons(final Set<String> createTypes,
                                           final int docTypeCount) {
        if ((NullSafe.hasItems(createTypes))) {
            if (NullSafe.size(createTypes) == docTypeCount) {
                return SafeHtmlUtils.fromTrustedString("ALL");
            } else {
                //noinspection SimplifyStreamApiCallChains // Cos GWT
                final List<DocumentType> typeIcons = DocumentTypeRegistry.getTypes()
                        .stream()
                        .filter(docType -> createTypes.contains(docType.getType()))
                        .sorted(Comparator.comparing(DocumentType::getType))
                        .collect(Collectors.toList());

                return HtmlBuilder.builder()
                        .div(
                                divBuilder -> {
                                    typeIcons.forEach(type -> {
                                        final SvgImage typeIcon = type.getIcon();
                                        divBuilder.div(divBuilder2 ->
                                                        divBuilder2.append(
                                                                SafeHtmlUtils.fromTrustedString(typeIcon.getSvg())),
                                                Attribute.title(type.getDisplayType()),
                                                Attribute.className(
                                                        "icon-cell create-document-types__icon "
                                                        + typeIcon.getClassName()));
                                    });
                                },
                                Attribute.className("create-document-types"))
                        .toSafeHtml();
            }
        } else {
            return SafeHtmlUtils.EMPTY_SAFE_HTML;
        }
    }

    /// @param dataProvider Gives the list its rows, a page at a time.
    public void setDataProvider(final AbstractDataProvider<DocumentUserPermissions> dataProvider) {
        dataProvider.addDataDisplay(dataGrid);
    }

    /// @return The task monitor that spins the pager's refresh button.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }

    /// @return The selection, one row at most.
    public MultiSelectionModel<DocumentUserPermissions> getSelectionModel() {
        return selectionModel;
    }

    /// @return How the rows are sorted, for the request for them.
    public ColumnSortList getSortList() {
        return dataGrid.getColumnSortList();
    }

    /// Empties the list.
    public void clearRows() {
        dataGrid.setRowData(0, Collections.emptyList());
        dataGrid.setRowCount(0, true);
    }

    /// @return The toolbar beside the pager, for the screen's own buttons.
    public Toolbar getToolbar() {
        return pager.getToolbar();
    }

    /// @param filterError Why the server rejected the filter, or `null` to clear it.
    public void setFilterError(final String filterError) {
        page.setFilterError(filterError);
    }

    /// @param popupTextSupplier Gives the filter's help popup its text.
    public void registerPopupTextProvider(final Supplier<SafeHtml> popupTextSupplier) {
        page.registerPopupTextProvider(popupTextSupplier);
    }

    /// @param contextHandler Gives the filter its recent filters and remembers the ones used.
    public void setQuickFilterContextHandler(final QuickFilterContextHandler contextHandler) {
        page.setQuickFilterContextHandler(contextHandler);
    }

    /// @param onFilterChange Given the filter's text when it changes.
    public void setOnFilterChange(final Consumer<String> onFilterChange) {
        page.setOnFilterChange(onFilterChange);
    }

    /// @param onSortChange Run when the user sorts by another column.
    public void setOnSortChange(final Runnable onSortChange) {
        this.onSortChange = onSortChange;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, DocumentUserPermissionsList> {

    }
}
