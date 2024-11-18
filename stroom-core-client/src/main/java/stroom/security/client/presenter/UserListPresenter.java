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

import stroom.cell.info.client.CommandLink;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.PageRequestUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.security.client.AppPermissionsPlugin;
import stroom.security.client.event.OpenAppPermissionsEvent;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.QuickFilterExpressionParser;
import stroom.security.shared.User;
import stroom.security.shared.UserFields;
import stroom.security.shared.UserResource;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.ResultPage;
import stroom.widget.button.client.ButtonView;
import stroom.widget.dropdowntree.client.view.QuickFilterPageView;
import stroom.widget.dropdowntree.client.view.QuickFilterTooltipUtil;
import stroom.widget.dropdowntree.client.view.QuickFilterUiHandlers;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.MultiSelectionModelImpl;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.DataGrid;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.function.Consumer;
import java.util.function.Function;

public class UserListPresenter
        extends MyPresenterWidget<QuickFilterPageView>
        implements QuickFilterUiHandlers {

    private static final UserResource USER_RESOURCE = GWT.create(UserResource.class);

    private final PagerView pagerView;
    private final MyDataGrid<User> dataGrid;
    private final MultiSelectionModelImpl<User> selectionModel;
    private final RestFactory restFactory;
    private final FindUserCriteria.Builder builder = new FindUserCriteria.Builder();
    private final Column<User, String> fullNameCol;
    private RestDataProvider<User, ResultPage<User>> dataProvider;
    private User selected;
    private ExpressionTerm additionalTerm;
    private String filter;
    private ResultPage<User> currentData;
    private Consumer<ResultPage<User>> resultPageConsumer;

    @Inject
    public UserListPresenter(final EventBus eventBus,
                             final QuickFilterPageView userListView,
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

        DataGridUtil.addColumnSortHandler(dataGrid, builder, this::refresh);

        // Icon
        dataGrid.addColumn(
                DataGridUtil.svgPresetColumnBuilder(false, (User row) ->
                                row.isGroup()
                                        ? SvgPresets.USER_GROUP
                                        : SvgPresets.USER)
                        .withSorting(UserFields.FIELD_IS_GROUP)
                        .centerAligned()
                        .build(),
                DataGridUtil.headingBuilder("")
                        .headingText(buildIconHeader())
                        .centerAligned()
                        .withToolTip("Whether this row is a single user or a named user group.")
                        .build(),
                (ColumnSizeConstants.ICON_COL * 2) + 20);

        // Display Name
        final Column<User, CommandLink> displayNameCol = DataGridUtil.commandLinkColumnBuilder(
                        buildOpenAppPermissionsCommandLink())
                .withSorting(UserFields.FIELD_DISPLAY_NAME, true)
                .build();
        dataGrid.addResizableColumn(
                displayNameCol,
                DataGridUtil.headingBuilder("Display Name")
                        .withToolTip("The name of the user or group.")
                        .build(),
                400);

        // Full name
        fullNameCol = DataGridUtil.textColumnBuilder(User::getFullName)
                .withSorting(UserFields.FIELD_FULL_NAME, true)
                .build();
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(User::getFullName)
                        .withSorting(UserFields.FIELD_FULL_NAME, true)
                        .build(),
                DataGridUtil.headingBuilder("Full Name")
                        .withToolTip("The full name of the user. Groups do not have a full name.")
                        .build(),
                400);

        DataGridUtil.addEndColumn(dataGrid);

        dataGrid.getColumnSortList().push(displayNameCol);
    }

    public void setIncludeFullNameCol(final boolean includeFullNameCol) {
        if (!includeFullNameCol) {
            dataGrid.removeColumn(dataGrid.getColumnCount() - 1);
        }
    }

    @Override
    public void onFilterChange(final String text) {
        filter = text;
        if (filter != null) {
            filter = filter.trim();
            if (filter.isEmpty()) {
                filter = null;
            }
        }
        refresh();
    }

    private SafeHtml buildIconHeader() {
        // TODO this is duplicated in AppUserPermissionsListPresenter
        final String iconClassName = "svgCell-icon";
        final Preset userPreset = SvgPresets.USER.title("");
        final Preset groupPreset = SvgPresets.USER_GROUP.title("");
        return HtmlBuilder.builder()
                .div(
                        divBuilder -> {
                            divBuilder.append(SvgImageUtil.toSafeHtml(
                                    userPreset.getTitle(),
                                    userPreset.getSvgImage(),
                                    iconClassName));
                            divBuilder.append("/");
                            divBuilder.append(SvgImageUtil.toSafeHtml(
                                    groupPreset.getTitle(),
                                    groupPreset.getSvgImage(),
                                    iconClassName));
                        },
                        Attribute.className("two-icon-column-header"))
                .toSafeHtml();
    }

    private Function<User, CommandLink> buildOpenAppPermissionsCommandLink() {
        return (User user) -> {
            if (user != null) {
                final String displayName = user.getDisplayName();
                return new CommandLink(
                        displayName,
                        "Open " + user.getType() + " '" + user.getDisplayName() + "' on the "
                        + AppPermissionsPlugin.SCREEN_NAME + " screen.",
                        () -> OpenAppPermissionsEvent.fire(
                                UserListPresenter.this, user.getSubjectId()));
            } else {
                return null;
            }
        };
    }

    public void setQuickFilterText(final String quickFilterText) {
        getView().setQuickFilterText(quickFilterText);
    }

    public void refresh() {
        if (dataProvider == null) {
            this.dataProvider = new RestDataProvider<User, ResultPage<User>>(getEventBus()) {
                @Override
                protected void exec(final Range range,
                                    final Consumer<ResultPage<User>> dataConsumer,
                                    final RestErrorHandler errorHandler) {
                    ExpressionOperator expression = QuickFilterExpressionParser
                            .parse(filter, UserFields.DEFAULT_FIELDS, UserFields.ALL_FIELD_MAP);
                    if (additionalTerm != null) {
                        expression = expression.copy().addTerm(additionalTerm).build();
                    }
                    builder.expression(expression);
                    builder.pageRequest(PageRequestUtil.createPageRequest(range));
                    restFactory
                            .create(USER_RESOURCE)
                            .method(res -> res.find(builder.build()))
                            .onSuccess(dataConsumer)
                            .onFailure(errorHandler)
                            .taskMonitorFactory(pagerView)
                            .exec();
                }

                @Override
                protected void changeData(final ResultPage<User> data) {
                    currentData = data;
                    super.changeData(data);
                    if (resultPageConsumer != null) {
                        resultPageConsumer.accept(data);
                    }
                }
            };
            dataProvider.addDataDisplay(dataGrid);

        } else {
            dataProvider.refresh();
        }
    }

    public MultiSelectionModelImpl<User> getSelectionModel() {
        return selectionModel;
    }

    public DataGrid<User> getDataGrid() {
        return dataGrid;
    }

    public ButtonView addButton(final Preset preset) {
        return pagerView.addButton(preset);
    }

    public void setAdditionalTerm(final ExpressionTerm additionalTerm) {
        this.additionalTerm = additionalTerm;
    }

    public PagerView getPagerView() {
        return pagerView;
    }

    public ResultPage<User> getCurrentData() {
        return currentData;
    }

    public void setResultPageConsumer(final Consumer<ResultPage<User>> resultPageConsumer) {
        this.resultPageConsumer = resultPageConsumer;
    }
}
