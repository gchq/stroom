/*
 * Copyright 2024 Crown Copyright
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
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.DefaultErrorHandler;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.quickfilter.client.QuickFilterContextHandlerFactory;
import stroom.security.shared.FindUserContext;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.GetUserRequest;
import stroom.security.shared.UserFields;
import stroom.security.shared.UserRefResource;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.TokenError;
import stroom.util.shared.UserRef;
import stroom.widget.datagrid.client.DataGridWidget;
import stroom.widget.datagrid.client.DataGridWidgetSelectionEventManager;
import stroom.widget.dropdowntree.client.view.QuickFilterDialogView;
import stroom.widget.dropdowntree.client.view.QuickFilterTooltipUtil;
import stroom.widget.dropdowntree.client.view.QuickFilterUiHandlers;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.List;
import java.util.function.Consumer;

// TODO I think this is not really needed as AccountService will create/update stroom_user
//  records so we can just re-use UserListPresenter
//

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
    private final DataGridWidget<UserRef> dataGrid;
    private RestDataProvider<UserRef, ResultPage<UserRef>> dataProvider;
    private final MultiSelectionModelImpl<UserRef> selectionModel;
    private final RestFactory restFactory;
    private final FindUserCriteria.Builder criteriaBuilder = new FindUserCriteria.Builder();
    private UserRef initialSelection;
    private ExpressionTerm additionalTerm;
    private String filter;
    private FindUserContext context = FindUserContext.RUN_AS;

    private Consumer<UserRef> selectionChangeConsumer = e -> {

    };

    private Consumer<UserRef> userConsumer = userRef -> {};

    @Inject
    public UserRefPopupPresenter(final EventBus eventBus,
                                 final QuickFilterDialogView userListView,
                                 final PagerView pagerView,
                                 final RestFactory restFactory,
                                 final UiConfigCache uiConfigCache,
                                 final QuickFilterContextHandlerFactory quickFilterContextHandlerFactory) {
        super(eventBus, userListView);
        getView().setAccessibleName("Filter users and groups");
        this.pagerView = pagerView;
        this.restFactory = restFactory;

        dataGrid = new DataGridWidget<>(this);
        dataGrid.setTableName("Users");
        dataGrid.setAccessibleName("Users and groups");
        selectionModel = new MultiSelectionModelImpl<>();
        // A picker's keys: Down in the filter goes to the chosen user, Up from the first row back
        // to the filter, and typing in the grid goes to the filter; moving chooses (as in every
        // single-choice grid)
        final DataGridWidgetSelectionEventManager<UserRef> selectionEventManager =
                new DataGridWidgetSelectionEventManager<>(dataGrid, selectionModel, false);
        selectionEventManager.setMoveUpFromFirst(userListView::focus);
        selectionEventManager.setTypedHandler(userListView::typeInFilter);
        dataGrid.setSelectionModel(selectionModel, selectionEventManager);
        userListView.setFilterMoveDown(this::focusSelected);
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
        userListView.setQuickFilterContextHandler(quickFilterContextHandlerFactory.create(
                UserFields.QUICK_FILTER_CONTEXT, this, this));

        setupColumns();
    }

    private void setupColumns() {
        // Icon
        dataGrid.addColumn(
                DataGridUtil.cellIconColumnBuilder(false, UserAndGroupHelper::mapUserRefTypeToIcon)
                        .withSorting(UserFields.FIELD_IS_GROUP)
//                .enabledWhen(UserRef::isEnabled)
                        .centerAligned()
                        .build(),
                DataGridUtil.headingBuilder()
                        .headingText(UserAndGroupHelper.buildUserAndGroupIconHeader())
                        .centerAligned()
                        .withToolTip("Whether this row is a single user or a named user group.")
                        .build(),
                (ColumnSizeConstants.ICON_COL * 2) + 20);

        // Display Name
        final Column<UserRef, String> displayNameCol = DataGridUtil.textColumnBuilder(UserRef::getDisplayName)
//                .enabledWhen(UserRef::isEnabled)
                .withSorting(UserFields.FIELD_DISPLAY_NAME, true)
                .build();
        dataGrid.addResizableColumn(
                displayNameCol,
                DataGridUtil.headingBuilder(UserAndGroupHelper.COL_NAME_DISPLAY_NAME)
                        .withToolTip("The name of the user or group.")
                        .build(),
                300);
        dataGrid.sort(displayNameCol);

        // Full name
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(UserRef::getFullName)
//                        .enabledWhen(UserRef::isEnabled)
                        .withSorting(UserFields.FIELD_FULL_NAME, true)
                        .build(),
                DataGridUtil.headingBuilder(UserAndGroupHelper.COL_NAME_FULL_NAME)
                        .withToolTip("The full name of the user. Groups do not have a full name.")
                        .build(),
                350);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(selectionModel.addSelectionHandler(event -> {
            if (event.getSelectionType().isDoubleSelect()) {
                if (selectionModel.getSelected() != null) {
                    HidePopupRequestEvent.builder(this).fire();
                }
            } else {
                NullSafe.consume(selectionModel.getSelected(), userRef ->
                        getView().announce(userRef.getDisplayName() + " chosen"));
            }
        }));
        // Enter on a row chooses that row (the one with the focus, which may not be chosen yet, e.g.
        // after filtering) and closes the dialog, as OK does
        registerHandler(dataGrid.addDomHandler(event -> {
            final int row = dataGrid.getKeyboardSelectedRow();
            final List<UserRef> rows = dataGrid.getVisibleItems();
            if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER
                && isInRows(event.getNativeEvent())
                && row >= 0 && row < rows.size()) {
                event.preventDefault();
                selectionModel.setSelected(rows.get(row));
                HidePopupRequestEvent.builder(this).fire();
            }
        }, KeyDownEvent.getType()));
        registerHandler(dataGrid.addColumnSortHandler(event -> refresh()));
    }

    public void setSelectionChangeConsumer(final Consumer<UserRef> selectionChangeConsumer) {
        this.selectionChangeConsumer = selectionChangeConsumer;
    }

    public void setUserConsumer(final Consumer<UserRef> userConsumer) {
        this.userConsumer = userConsumer;
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

    public void show(final String caption) {
        show(caption, userConsumer);
    }

    public void show(final Consumer<UserRef> consumer) {
        show("Select User Or Group", consumer);
    }

    public void show(final String caption, final Consumer<UserRef> consumer) {
        initialSelection = getSelected();
        refresh();

        final PopupSize popupSize = PopupSize.resizable(800, 600);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption(caption)
                // Modal, as it asks for a choice: the page behind can't be used, and Tab stays in it
                .modal(true)
                .onShow(e -> getView().focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final UserRef selected = getSelected();
                        selectionChangeConsumer.accept(selected);
                        consumer.accept(selected);
                        e.hide();
                    } else {
                        // Put back what was chosen when it opened (moving through the grid chooses)
                        setSelected(initialSelection);
                        selectionChangeConsumer.accept(initialSelection);
                        e.hide();
                    }
                })
                .fire();
    }

    public UserRef getSelected() {
        return selectionModel.getSelected();
    }

    // Moves the focus to the chosen user's row (or the first, if none is), from the filter above
    private void focusSelected() {
        dataGrid.setKeyboardSelectedRow(Math.max(0, dataGrid.getVisibleItems().indexOf(getSelected())), true);
    }

    // Makes the chosen user's row the grid's tab stop, so tabbing into it lands there
    private void showSelectedAsTabStop() {
        final int index = dataGrid.getVisibleItems().indexOf(getSelected());
        if (index >= 0) {
            dataGrid.setKeyboardSelectedRow(index, false);
        }
    }

    // Whether a key was pressed in the grid's rows (not, e.g., in its column headers)
    private boolean isInRows(final NativeEvent event) {
        final EventTarget target = event.getEventTarget();
        return Element.is(target) && dataGrid.isInRows(Element.as(target));
    }

    public void setSelected(final UserRef userRef) {
        selectionModel.setSelected(userRef);
    }

    public void resolve(final UserRef userRef, final Consumer<UserRef> consumer) {
        if (userRef == null || userRef.getUuid() == null) {
            consumer.accept(userRef);
        } else {
            final GetUserRequest request = new GetUserRequest(userRef.getUuid(), context);
            restFactory
                    .create(RESOURCE)
                    .method(res -> res.getUserByUuid(request))
                    .onSuccess(consumer)
                    .onFailure(new DefaultErrorHandler(this, () -> consumer.accept(userRef)))
                    .taskMonitorFactory(pagerView)
                    .exec();
        }
    }

    public void refresh() {
        if (dataProvider == null) {
            //noinspection Convert2Diamond // GWT
            this.dataProvider = new RestDataProvider<UserRef, ResultPage<UserRef>>(getEventBus()) {
                @Override
                protected void exec(final Range range,
                                    final Consumer<ResultPage<UserRef>> dataConsumer,
                                    final RestErrorHandler errorHandler) {
                    // The filter text goes to the server verbatim; only the structural term this
                    // screen adds on the user's behalf is composed here. See FindUserCriteria.
                    ExpressionOperator expression = ExpressionOperator.builder().build();
                    if (additionalTerm != null) {
                        expression = expression.copy().addTerm(additionalTerm).build();
                    }
                    criteriaBuilder.expression(expression);
                    criteriaBuilder.quickFilter(filter);
                    criteriaBuilder.pageRequest(CriteriaUtil.createPageRequest(range));
                    criteriaBuilder.sortList(CriteriaUtil.createSortList(dataGrid.getColumnSortList()));
                    restFactory
                            .create(RESOURCE)
                            .method(res -> res.find(criteriaBuilder.build()))
                            .onSuccess(resultPage -> {
                                getView().setFilterError(NullSafe.get(
                                        resultPage.getFilterError(), TokenError::getText));
                                dataConsumer.accept(resultPage);
                                Scheduler.get().scheduleDeferred(UserRefPopupPresenter.this::showSelectedAsTabStop);
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

    public void setAdditionalTerm(final ExpressionTerm additionalTerm) {
        this.additionalTerm = additionalTerm;
    }

    public void setContext(final FindUserContext context) {
        this.context = context;
        criteriaBuilder.context(context);
    }
}
