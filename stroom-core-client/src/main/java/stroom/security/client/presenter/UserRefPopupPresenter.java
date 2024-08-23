/*
 * Copyright 2016 Crown Copyright
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
import stroom.data.client.presenter.PageRequestUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.QuickFilterExpressionParser;
import stroom.security.shared.UserFields;
import stroom.security.shared.UserRefResource;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;
import stroom.widget.dropdowntree.client.view.QuickFilterDialogView;
import stroom.widget.dropdowntree.client.view.QuickFilterTooltipUtil;
import stroom.widget.dropdowntree.client.view.QuickFilterUiHandlers;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.ColumnSortEvent;
import com.google.gwt.user.cellview.client.ColumnSortList;
import com.google.gwt.user.cellview.client.ColumnSortList.ColumnSortInfo;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * For selecting a username to add a user to a permissions set or selecting a user to add to a group.
 * For internal IDP this will list users from account and stroom_user combined.
 * For external IDP this will list users from stroom_user only and users on the IDP but not in
 * stroom_user will have to be manually added to the list.
 */
public class UserRefPopupPresenter
        extends MyPresenterWidget<QuickFilterDialogView>
        implements QuickFilterUiHandlers {

    private static final UserRefResource RESOURCE = GWT.create(UserRefResource.class);

    private final PagerView pagerView;
    private final MyDataGrid<UserRef> dataGrid;
    private RestDataProvider<UserRef, ResultPage<UserRef>> dataProvider;
    private final MultiSelectionModelImpl<UserRef> selectionModel;
    private final RestFactory restFactory;
    private final FindUserCriteria.Builder builder = new FindUserCriteria.Builder();
    private UserRef initialSelection;
    private ExpressionTerm additionalTerm;
    private String filter;

    private Consumer<UserRef> selectionChangeConsumer = e -> {

    };

    @Inject
    public UserRefPopupPresenter(final EventBus eventBus,
                                 final QuickFilterDialogView userListView,
                                 final PagerView pagerView,
                                 final RestFactory restFactory,
                                 final UiConfigCache uiConfigCache) {
        super(eventBus, userListView);
        this.pagerView = pagerView;
        this.restFactory = restFactory;

        dataGrid = new MyDataGrid<>();
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

        // Icon
        final Column<UserRef, Preset> iconCol = new Column<UserRef, Preset>(new SvgCell()) {
            @Override
            public Preset getValue(final UserRef user) {
                if (!user.isGroup()) {
                    return SvgPresets.USER;
                }

                return SvgPresets.USER_GROUP;
            }
        };
        iconCol.setSortable(true);
        dataGrid.addColumn(iconCol, "</br>", ColumnSizeConstants.ICON_COL);

        // Display Name
        final Column<UserRef, String> displayNameCol = new Column<UserRef, String>(new TextCell()) {
            @Override
            public String getValue(final UserRef userRef) {
                return GwtNullSafe.requireNonNullElse(userRef.getDisplayName(), userRef.getSubjectId());
            }
        };
        iconCol.setSortable(true);
        dataGrid.addResizableColumn(displayNameCol, "Display Name", 200);

        // Full name
        final Column<UserRef, String> fullNameCol = new Column<UserRef, String>(new TextCell()) {
            @Override
            public String getValue(final UserRef userRef) {
                return userRef.getFullName();
            }
        };
        dataGrid.addResizableColumn(fullNameCol, "Full Name", 350);

        // Subject ID (aka the unique ID for the user)
        dataGrid.addResizableColumn(new Column<UserRef, String>(new TextCell()) {
            @Override
            public String getValue(final UserRef userRef) {
                return userRef.getSubjectId();
            }
        }, "Unique User Identity", 300);
        dataGrid.addEndColumn(new EndColumn<>());

        final ColumnSortEvent.Handler columnSortHandler = event -> {
            final List<CriteriaFieldSort> sortList = new ArrayList<>();
            if (event != null) {
                final ColumnSortList columnSortList = event.getColumnSortList();
                if (columnSortList != null) {
                    for (int i = 0; i < columnSortList.size(); i++) {
                        final ColumnSortInfo columnSortInfo = columnSortList.get(i);
                        final Column<?, ?> column = columnSortInfo.getColumn();
                        final boolean isAscending = columnSortInfo.isAscending();

                        if (column.equals(iconCol)) {
                            sortList.add(new CriteriaFieldSort(
                                    UserFields.IS_GROUP.getFldName(),
                                    !isAscending,
                                    true));
                        } else if (column.equals(displayNameCol)) {
                            sortList.add(new CriteriaFieldSort(
                                    UserFields.DISPLAY_NAME.getFldName(),
                                    !isAscending,
                                    true));
                            sortList.add(new CriteriaFieldSort(
                                    UserFields.NAME.getFldName(),
                                    !isAscending,
                                    true));
                        }
                    }
                }
            }
            builder.sortList(sortList);
            refresh();
        };
        dataGrid.addColumnSortHandler(columnSortHandler);
        dataGrid.getColumnSortList().push(displayNameCol);

        builder.sortList(List.of(
                new CriteriaFieldSort(
                        UserFields.DISPLAY_NAME.getFldName(),
                        false,
                        true),
                new CriteriaFieldSort(
                        UserFields.NAME.getFldName(),
                        false,
                        true)));
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(selectionModel.addSelectionHandler(event -> {
            if (event.getSelectionType().isDoubleSelect()) {
                if (selectionModel.getSelected() != null) {
                    HidePopupRequestEvent.builder(this).fire();
                }
            }
        }));
    }

    public void setSelectionChangeConsumer(final Consumer<UserRef> selectionChangeConsumer) {
        this.selectionChangeConsumer = selectionChangeConsumer;
    }

    @Override
    public void onFilterChange(final String text) {
        filter = text;
        if (filter != null) {
            filter = filter.trim();
            if (filter.length() == 0) {
                filter = null;
            }
        }
        refresh();
    }

    public void show(final Consumer<UserRef> userConsumer) {
        show("Select User Or Group", userConsumer);
    }

    public void show(final String caption, final Consumer<UserRef> userConsumer) {
        initialSelection = getSelected();
        refresh();

        final PopupSize popupSize = PopupSize.resizable(800, 600);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption(caption)
                .onShow(e -> getView().focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final UserRef selected = getSelected();
                        selectionChangeConsumer.accept(selected);
                        userConsumer.accept(selected);
                        e.hide();
                    } else {
                        selectionChangeConsumer.accept(initialSelection);
                        e.hide();
                    }
                })
                .fire();
    }

    public UserRef getSelected() {
        return selectionModel.getSelected();
    }

    public void setSelected(final UserRef userRef) {
        selectionModel.setSelected(userRef);
    }

    public void refresh() {
        if (dataProvider == null) {
            this.dataProvider = new RestDataProvider<UserRef, ResultPage<UserRef>>(getEventBus()) {
                @Override
                protected void exec(final Range range,
                                    final Consumer<ResultPage<UserRef>> dataConsumer,
                                    final RestErrorHandler errorHandler) {
                    ExpressionOperator expression = QuickFilterExpressionParser
                            .parse(filter, UserFields.DEFAULT_FIELDS, UserFields.ALL_FIELD_MAP);
                    if (additionalTerm != null) {
                        expression = expression.copy().addTerm(additionalTerm).build();
                    }
                    builder.expression(expression);
                    builder.pageRequest(PageRequestUtil.createPageRequest(range));
                    restFactory
                            .create(RESOURCE)
                            .method(res -> res.find(builder.build()))
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

    public void setAdditionalTerm(final ExpressionTerm additionalTerm) {
        this.additionalTerm = additionalTerm;
    }
}
