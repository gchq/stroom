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

import stroom.cell.info.client.SvgCell;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.explorer.client.presenter.DocumentTypeCache;
import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.DocumentTypes;
import stroom.query.api.v2.ExpressionOperator;
import stroom.security.shared.DocPermissionResource;
import stroom.security.shared.DocumentUserPermissions;
import stroom.security.shared.FetchDocumentUserPermissionsRequest;
import stroom.security.shared.QuickFilterExpressionParser;
import stroom.security.shared.User;
import stroom.security.shared.UserFields;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;
import stroom.widget.button.client.ButtonView;
import stroom.widget.dropdowntree.client.view.QuickFilterTooltipUtil;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class DocumentUserPermissionsListPresenter
        extends MyPresenterWidget<UserListView>
        implements UserListUiHandlers {

    private static final DocPermissionResource DOC_PERMISSION_RESOURCE = GWT.create(DocPermissionResource.class);

    private final RestFactory restFactory;
    private FetchDocumentUserPermissionsRequest criteria;
    private final MyDataGrid<DocumentUserPermissions> dataGrid;
//    private final MultiSelectionModelImpl<DocumentUserPermissions> selectionModel;
    private final PagerView pagerView;
    private RestDataProvider<DocumentUserPermissions, ResultPage<DocumentUserPermissions>> dataProvider;
    private DocumentTypes documentTypes;


    @Inject
    public DocumentUserPermissionsListPresenter(final EventBus eventBus,
                                                final UserListView userListView,
                                                final PagerView pagerView,
                                                final RestFactory restFactory,
                                                final UiConfigCache uiConfigCache,
                                                final DocumentTypeCache documentTypeCache) {
        super(eventBus, userListView);
        this.restFactory = restFactory;
        this.pagerView = pagerView;
        documentTypeCache.fetch(dt -> this.documentTypes = dt, this);

        dataGrid = new MyDataGrid<>();
//        selectionModel = dataGrid.addDefaultSelectionModel(false);
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

        userListView.setDatGridView(pagerView);
        userListView.setUiHandlers(this);
    }

    @Override
    public void changeNameFilter(String name) {
        if (name != null) {
            name = name.trim();
            if (name.isEmpty()) {
                name = null;
            }
        }

        final ExpressionOperator expression = QuickFilterExpressionParser
                .parse(name, UserFields.DEFAULT_FIELDS, UserFields.ALL_FIELD_MAP);
        criteria.setExpression(expression);
        refresh();
    }

    public void refresh() {
        if (criteria != null) {
            if (dataProvider == null) {
                dataProvider =
                        new RestDataProvider<DocumentUserPermissions, ResultPage<DocumentUserPermissions>>(
                                getEventBus()) {
                            @Override
                            protected void exec(final Range range,
                                                final Consumer<ResultPage<DocumentUserPermissions>> dataConsumer,
                                                final RestErrorHandler errorHandler) {
                                CriteriaUtil.setRange(criteria, range);
                                restFactory
                                        .create(DOC_PERMISSION_RESOURCE)
                                        .method(res -> res.fetchDocumentUserPermissions(criteria))
                                        .onSuccess(dataConsumer)
                                        .onFailure(errorHandler)
                                        .taskListener(pagerView)
                                        .exec();
                            }
                        };
                dataProvider.addDataDisplay(dataGrid);
            } else {
                dataProvider.refresh();
            }
        }
    }

    public void setDocRef(final DocRef docRef) {
        criteria = new FetchDocumentUserPermissionsRequest();
        criteria.setDocRef(docRef);
        setupColumns();
        refresh();
    }

    private void setupColumns() {
        // Icon
        final Column<DocumentUserPermissions, Preset> iconCol =
                new Column<DocumentUserPermissions, Preset>(new SvgCell()) {
                    @Override
                    public Preset getValue(final DocumentUserPermissions documentUserPermissions) {
                        final UserRef userRef = documentUserPermissions.getUserRef();
                        if (!userRef.isGroup()) {
                            return SvgPresets.USER;
                        }

                        return SvgPresets.USER_GROUP;
                    }
                };
        dataGrid.addColumn(iconCol, "</br>", ColumnSizeConstants.ICON_COL);

        // User Or Group Name
        final Column<DocumentUserPermissions, String> userCol = new Column<DocumentUserPermissions, String>(new TextCell()) {
            @Override
            public String getValue(final DocumentUserPermissions documentUserPermissions) {
                final UserRef userRef = documentUserPermissions.getUserRef();
                if (userRef.getDisplayName() != null) {
                    if (!Objects.equals(userRef.getDisplayName(), userRef.getSubjectId())) {
                        return userRef.getDisplayName() + " (" + userRef.getSubjectId() + ")";
                    } else {
                        return userRef.getDisplayName();
                    }
                }
                return userRef.getSubjectId();
            }
        };
        dataGrid.addResizableColumn(userCol, "User or Group", 400);

        // Permission
        final Column<DocumentUserPermissions, String> permissionCol =
                new Column<DocumentUserPermissions, String>(new TextCell()) {
                    @Override
                    public String getValue(final DocumentUserPermissions documentUserPermissions) {
                        return documentUserPermissions.getPermission().getDisplayValue();
                    }
                };
        dataGrid.addResizableColumn(permissionCol, "Permission", 100);

        if (DocumentTypes.isFolder(criteria.getDocRef())) {
            // Document Create Permissions.
            final Column<DocumentUserPermissions, String> documentCreateTypeCol =
                    new Column<DocumentUserPermissions, String>(new TextCell()) {
                        @Override
                        public String getValue(final DocumentUserPermissions documentUserPermissions) {
                            final Set<String> createTypes = documentUserPermissions.getDocumentCreatePermissions();
                            if (createTypes == null || createTypes.size() == 0) {
                                return "[ none ]";
                            } else if (createTypes.size() == documentTypes.getTypes().size()) {
                                return "All";
                            } else if (createTypes.size() + 3 < documentTypes.getTypes().size()) {
                                return createTypes.stream().sorted().collect(Collectors.joining(", "));
                            } else {
                                final String except = documentTypes
                                        .getTypes()
                                        .stream()
                                        .map(DocumentType::getType)
                                        .filter(name -> !createTypes.contains(name))
                                        .sorted()
                                        .collect(Collectors.joining(", "));
                                return "All (except " + except + ")";
                            }
                        }
                    };
            dataGrid.addAutoResizableColumn(documentCreateTypeCol, "Create Document Types", 100);
        }

        dataGrid.addEndColumn(new EndColumn<DocumentUserPermissions>());
    }

    public ButtonView addButton(final Preset preset) {
        return pagerView.addButton(preset);
    }

//    public MultiSelectionModel<DocumentUserPermissions> getSelectionModel() {
//        return selectionModel;
//    }

    public PagerView getPagerView() {
        return pagerView;
    }
}
