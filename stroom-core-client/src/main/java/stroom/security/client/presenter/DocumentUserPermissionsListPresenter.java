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
import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeRegistry;
import stroom.explorer.client.presenter.DocumentTypeCache;
import stroom.explorer.shared.DocumentTypes;
import stroom.query.api.ExpressionOperator;
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
import stroom.svg.shared.SvgImage;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;
import stroom.util.shared.UserRef.DisplayType;
import stroom.widget.button.client.ButtonView;
import stroom.widget.dropdowntree.client.view.QuickFilterPageView;
import stroom.widget.dropdowntree.client.view.QuickFilterTooltipUtil;
import stroom.widget.dropdowntree.client.view.QuickFilterUiHandlers;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
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

        dataGrid = new MyDataGrid<>(this);
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
    protected void onBind() {
        super.onBind();
        registerHandler(dataGrid.addColumnSortHandler(event -> refresh()));
    }

    @Override
    public void onFilterChange(String text) {
        text = NullSafe.trim(text);
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
                            criteriaBuilder.pageRequest(CriteriaUtil.createPageRequest(range));
                            criteriaBuilder.sortList(CriteriaUtil.createSortList(dataGrid.getColumnSortList()));
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
        // Icon
        dataGrid.addColumn(
                DataGridUtil.svgPresetColumnBuilder(false, (DocumentUserPermissions row) ->
                                UserAndGroupHelper.mapUserRefTypeToIcon(row.getUserRef()))
                        .withSorting(UserFields.FIELD_IS_GROUP)
                        .centerAligned()
                        .enabledWhen(this::isUserEnabled)
                        .build(),
                DataGridUtil.headingBuilder("")
                        .headingText(UserAndGroupHelper.buildUserAndGroupIconHeader())
                        .centerAligned()
                        .withToolTip("Whether this row is a single user or a named user group.")
                        .build(),
                (ColumnSizeConstants.ICON_COL * 2) + 20);

        // User Or Group Name
        final Column<DocumentUserPermissions, DocumentUserPermissions> displayNameCol =
                DataGridUtil.userRefColumnBuilder(
                                DocumentUserPermissions::getUserRef,
                                getEventBus(),
                                securityContext,
                                false,
                                DisplayType.DISPLAY_NAME)
                        .withSorting(UserFields.FIELD_DISPLAY_NAME, true)
                        .enabledWhen(this::isUserEnabled)
                        .build();
        dataGrid.sort(displayNameCol);

        dataGrid.addResizableColumn(
                displayNameCol,
                DataGridUtil.headingBuilder(UserAndGroupHelper.COL_NAME_DISPLAY_NAME)
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

        if (docRef != null && DocumentTypes.isFolder(docRef)) {
            final int docTypeCount = documentTypes.getTypes().size();

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

        DataGridUtil.addEndColumn(dataGrid);
    }

    private boolean isUserEnabled(final DocumentUserPermissions documentUserPermissions) {
        return NullSafe.get(documentUserPermissions, DocumentUserPermissions::getUserRef, UserRef::isEnabled);
    }

    public SafeHtml permissionsToExplicitTypeIcons(final DocumentUserPermissions row,
                                                   final int docTypeCount) {
        final Set<String> explicit = row
                .getDocumentCreatePermissions();
        return permissionsToTypeIcons(explicit, docTypeCount);
    }

    public SafeHtml permissionsToEffectiveTypeIcons(final DocumentUserPermissions row,
                                                    final int docTypeCount) {
        final Set<String> effective = new HashSet<>(NullSafe.set(row
                .getDocumentCreatePermissions()));
        effective.addAll(NullSafe.set(row.getInheritedDocumentCreatePermissions()));

        return permissionsToTypeIcons(effective, docTypeCount);
    }

    public SafeHtml permissionsToTypeIcons(final Set<String> createTypes,
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
                                                        "svg-icon svgCell-icon create-document-types-icon "
                                                        + typeIcon.getClassName()));
                                    });
                                },
                                Attribute.className("create-document-types-container"))
                        .toSafeHtml();
            }
        } else {
            return SafeHtmlUtils.EMPTY_SAFE_HTML;
        }
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
