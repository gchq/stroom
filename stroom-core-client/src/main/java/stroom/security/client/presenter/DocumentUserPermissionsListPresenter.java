/*
 * Copyright 2017 Crown Copyright
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

package stroom.security.client.presenter;

import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.PageRequestUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.client.presenter.UserRefCell.UserRefProvider;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.explorer.client.presenter.DocumentTypeCache;
import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.DocumentTypes;
import stroom.query.api.v2.ExpressionOperator;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.DocPermissionResource;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.DocumentPermissionFields;
import stroom.security.shared.DocumentUserPermissions;
import stroom.security.shared.FetchDocumentUserPermissionsRequest;
import stroom.security.shared.PermissionShowLevel;
import stroom.security.shared.QuickFilterExpressionParser;
import stroom.security.shared.UserFields;
import stroom.svg.client.Preset;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef.DisplayType;
import stroom.widget.button.client.ButtonView;
import stroom.widget.dropdowntree.client.view.QuickFilterPageView;
import stroom.widget.dropdowntree.client.view.QuickFilterTooltipUtil;
import stroom.widget.dropdowntree.client.view.QuickFilterUiHandlers;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.ColumnSortList.ColumnSortInfo;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class DocumentUserPermissionsListPresenter
        extends MyPresenterWidget<QuickFilterPageView>
        implements QuickFilterUiHandlers {

    private static final DocPermissionResource DOC_PERMISSION_RESOURCE = GWT.create(DocPermissionResource.class);

    private final ClientSecurityContext securityContext;
    private final RestFactory restFactory;
    private final FetchDocumentUserPermissionsRequest.Builder criteriaBuilder =
            new FetchDocumentUserPermissionsRequest.Builder();
    private final MyDataGrid<DocumentUserPermissions> dataGrid;
    private final MultiSelectionModelImpl<DocumentUserPermissions> selectionModel;
    private final PagerView pagerView;
    private RestDataProvider<DocumentUserPermissions, ResultPage<DocumentUserPermissions>> dataProvider;
    private DocRef docRef;

    @Inject
    public DocumentUserPermissionsListPresenter(final EventBus eventBus,
                                                final QuickFilterPageView userListView,
                                                final PagerView pagerView,
                                                final RestFactory restFactory,
                                                final UiConfigCache uiConfigCache,
                                                final DocumentTypeCache documentTypeCache,
                                                final ClientSecurityContext securityContext) {
        super(eventBus, userListView);
        this.restFactory = restFactory;
        this.pagerView = pagerView;
        this.securityContext = securityContext;
        documentTypeCache.fetch(this::setupColumns, this);

        dataGrid = new MyDataGrid<>();
        dataGrid.setMultiLine(true);
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        pagerView.setDataWidget(dataGrid);

        // Not easy to determine if we are dealing in users or groups at this point so just
        // call it Quick Filter
        uiConfigCache.get(uiConfig -> {
            if (uiConfig != null) {
                userListView.registerPopupTextProvider(() -> QuickFilterTooltipUtil.createTooltip(
                        "Quick Filter",
                        UserFields.FILTER_FIELD_DEFINITIONS,
                        uiConfig.getHelpUrlQuickFilter()));
            }
        }, this);

        userListView.setDataView(pagerView);
        userListView.setUiHandlers(this);

//        criteriaBuilder.sortList(List.of(
//                new CriteriaFieldSort(
//                        UserFields.DISPLAY_NAME.getFldName(),
//                        false,
//                        true),
//                new CriteriaFieldSort(
//                        UserFields.UNIQUE_ID.getFldName(),
//                        false,
//                        true)));
    }

    @Override
    public void onFilterChange(String text) {
        text = GwtNullSafe.trim(text);
        if (text.isEmpty()) {
            text = null;
        }
        // Clear to ensure we go back to page one
        clear();
        final ExpressionOperator expression = QuickFilterExpressionParser
                .parse(text, UserFields.DEFAULT_FIELDS, UserFields.ALL_FIELDS_MAP);
        criteriaBuilder.expression(expression);
        refresh();
    }

    public void refresh() {
        if (dataProvider == null) {
            dataProvider =
                    new RestDataProvider<DocumentUserPermissions, ResultPage<DocumentUserPermissions>>(
                            getEventBus()) {
                        @Override
                        protected void exec(final Range range,
                                            final Consumer<ResultPage<DocumentUserPermissions>> dataConsumer,
                                            final RestErrorHandler errorHandler) {
                            criteriaBuilder.pageRequest(PageRequestUtil.createPageRequest(range));
                            final FetchDocumentUserPermissionsRequest request = criteriaBuilder.build();
                            restFactory
                                    .create(DOC_PERMISSION_RESOURCE)
                                    .method(res -> res.fetchDocumentUserPermissions(request))
                                    .onSuccess(response -> {
                                        GWT.log("Fetched " + response.size() + " records");
                                        dataConsumer.accept(response);
                                    })
                                    .onFailure(errorHandler)
                                    .taskMonitorFactory(pagerView)
                                    .exec();
                        }
                    };
            dataProvider.addDataDisplay(dataGrid);
        } else {
            dataProvider.refresh();
        }
    }

    public void clear() {
//        GWT.log(name + " - clear");
        selectionModel.clear();
        if (dataProvider != null) {
            dataProvider.getDataDisplays().forEach(hasData -> {
                hasData.setRowData(0, Collections.emptyList());
                hasData.setRowCount(0, true);
            });
        }
    }

    public void setDocRef(final DocRef docRef) {
        this.docRef = docRef;
        criteriaBuilder.docRef(docRef);
    }

    private void setupColumns(final DocumentTypes documentTypes) {
        DataGridUtil.addColumnSortHandler(dataGrid, criteriaBuilder, this::refresh);

        // Icon
        dataGrid.addColumn(
                DataGridUtil.svgPresetColumnBuilder(false, (DocumentUserPermissions row) ->
                                UserAndGroupHelper.mapUserRefTypeToIcon(row.getUserRef()))
                        .withSorting(UserFields.FIELD_IS_GROUP)
                        .centerAligned()
                        .build(),
                DataGridUtil.headingBuilder("")
                        .headingText(UserAndGroupHelper.buildUserAndGroupIconHeader())
                        .centerAligned()
                        .withToolTip("Whether this row is a single user or a named user group.")
                        .build(),
                (ColumnSizeConstants.ICON_COL * 2) + 20);

        // User Or Group Name
        final Column<DocumentUserPermissions, UserRefProvider<DocumentUserPermissions>> displayNameCol =
                DataGridUtil.userRefColumnBuilder(
                                DocumentUserPermissions::getUserRef,
                                getEventBus(),
                                securityContext,
                                false,
                                DisplayType.DISPLAY_NAME)
                        .withSorting(UserFields.FIELD_DISPLAY_NAME, true)
                        .build();

        dataGrid.addResizableColumn(
                displayNameCol,
                DataGridUtil.headingBuilder(UserAndGroupHelper.COL_NAME_DISPLAY_NAME)
                        .withToolTip("The name of the user or group.")
                        .build(),
                ColumnSizeConstants.USER_DISPLAY_NAME_COL);

        // Explicit Permission
        dataGrid.addColumn(
                DataGridUtil.textColumnBuilder((DocumentUserPermissions row) ->
                                GwtNullSafe.get(
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
                                (DocumentUserPermissions row) -> {
                                    final DocumentPermission explicit = row.getPermission();
                                    final DocumentPermission inherited = row.getInheritedPermission();
                                    return GwtNullSafe.get(DocumentPermission.highest(explicit, inherited),
                                            DocumentPermission::getDisplayValue);
                                })
                        .withSorting(DocumentPermissionFields.FIELD_EFFECTIVE_DOC_PERMISSION)
                        .build(),
                DataGridUtil.headingBuilder("Effective Permission")
                        .withToolTip("The highest effective permission held on this document by the user/group. "
                                     + "The permission may be explicit or inherited.")
                        .build(),
                160);

        if (DocumentTypes.isFolder(docRef)) {
            final int docTypeCount = documentTypes.getTypes().size();

            // Explicit doc create types
            dataGrid.addAutoResizableColumn(
                    DataGridUtil.textColumnBuilder(
                                    (DocumentUserPermissions row) -> {
                                        final Set<String> explicit = row
                                                .getDocumentCreatePermissions();
                                        if ((GwtNullSafe.hasItems(explicit))) {
                                            if (GwtNullSafe.size(explicit) == docTypeCount) {
                                                return "ALL";
                                            } else {
                                                return documentTypes.getTypes()
                                                        .stream()
                                                        .filter(docType -> explicit.contains(docType.getType()))
                                                        .map(DocumentType::getDisplayType)
                                                        .sorted()
                                                        .collect(Collectors.joining(", "));
                                            }
                                        } else {
                                            return null;
                                        }
                                    })
                            .build(),
                    DataGridUtil.headingBuilder("Explicit Create Document Types")
                            .withToolTip("The document types that this user/group has explicit permission to create. " +
                                         "Ignores inherited permissions.")
                            .build(),
                    300);

            dataGrid.addAutoResizableColumn(
                    DataGridUtil.textColumnBuilder(
                                    (DocumentUserPermissions row) -> {
                                        final Set<String> effective = new HashSet<>(
                                                GwtNullSafe.set(row.getDocumentCreatePermissions()));
                                        effective.addAll(row.getInheritedDocumentCreatePermissions());
                                        if ((!effective.isEmpty())) {
                                            if (effective.size() == docTypeCount) {
                                                return "ALL";
                                            } else {
                                                return documentTypes.getTypes()
                                                        .stream()
                                                        .filter(docType -> effective.contains(docType.getType()))
                                                        .map(DocumentType::getDisplayType)
                                                        .sorted()
                                                        .collect(Collectors.joining(", "));
                                            }
                                        } else {
                                            return null;
                                        }
                                    })
                            .build(),
                    DataGridUtil.headingBuilder("Effective Create Document Types")
                            .withToolTip("The document types that this user/group has explicit or inherited " +
                                         "permission to create.")
                            .build(),
                    300);

            // Document Create Permissions.
//            final Column<DocumentUserPermissions, SafeHtml> documentCreateTypeCol =
//                    new Column<DocumentUserPermissions, SafeHtml>(new SafeHtmlCell()) {
//                        @Override
//                        public SafeHtml getValue(final DocumentUserPermissions documentUserPermissions) {
//                            final DescriptionBuilder sb = new DescriptionBuilder();
//                            final Set<String> explicit = documentUserPermissions
//                                    .getDocumentCreatePermissions();
//                            final Set<String> inherited = documentUserPermissions
//                                    .getInheritedDocumentCreatePermissions();
//                            if ((GwtNullSafe.hasItems(explicit)) || (GwtNullSafe.hasItems(inherited))) {
//                                if (GwtNullSafe.size(explicit) == docTypeCount) {
//                                    sb.addLine(true, false, "ALL");
//                                } else if (GwtNullSafe.size(inherited) == docTypeCount) {
//                                    sb.addLine(true, true, "ALL");
//                                } else {
//                                    boolean notEmpty = false;
//                                    boolean lastInherited = false;
//                                    //noinspection SimplifyStreamApiCallChains // Cos GWT
//                                    final List<DocumentType> types = documentTypes.getTypes()
//                                            .stream()
//                                            .sorted(Comparator.comparing(DocumentType::getDisplayType))
//                                            .collect(Collectors.toList());
//                                    for (final DocumentType documentType : types) {
//                                        if (GwtNullSafe.collectionContains(explicit, documentType.getType())) {
//                                            if (notEmpty) {
//                                                sb.addLine(false, lastInherited, ", ");
//                                            }
//                                            sb.addLine(documentType.getDisplayType());
//                                            lastInherited = false;
//                                            notEmpty = true;
//                                        } else if (GwtNullSafe.collectionContains(inherited, documentType.getType())) {
//                                            if (notEmpty) {
//                                                sb.addLine(false, lastInherited, ", ");
//                                            }
//                                            sb.addLine(false, true, documentType.getDisplayType());
//                                            lastInherited = true;
//                                            notEmpty = true;
//                                        }
//                                    }
//                                }
//                            }
//
//                            return sb.toSafeHtml();
//                        }
//                    };
//            dataGrid.addAutoResizableColumn(documentCreateTypeCol,
//                    "Effective Create Document Types",
//                    300);
        }

        DataGridUtil.addEndColumn(dataGrid);

        dataGrid.getColumnSortList().push(new ColumnSortInfo(displayNameCol, true));
    }

    public ButtonView addButton(final Preset preset) {
        return pagerView.addButton(preset);
    }

    public void addButton(final ButtonView buttonView) {
        pagerView.addButton(buttonView);
    }

    public MultiSelectionModel<DocumentUserPermissions> getSelectionModel() {
        return selectionModel;
    }

    public PagerView getPagerView() {
        return pagerView;
    }

    public void setShowLevel(final PermissionShowLevel showLevel) {
        if (!Objects.equals(showLevel, criteriaBuilder.getShowLevel())) {
            criteriaBuilder.showLevel(showLevel);
            // We may be on page two so clear everything, so we go back to page one
            clear();
        }
    }
}
