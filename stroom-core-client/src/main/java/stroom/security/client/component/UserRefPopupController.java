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

package stroom.security.client.component;

import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.RestDataProvider;
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
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.TokenError;
import stroom.util.shared.UserRef;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.DialogAction;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.quickfilter.client.QuickFilterTooltipUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// The user picker ([UserRefPopup]): for choosing a user or group, e.g. to add to a group or give
/// permissions to. For the internal identity provider it lists the accounts and Stroom's users
/// together; for an external one, Stroom's users only, and users the identity provider has that
/// Stroom doesn't yet have to be added by hand.
///
/// Its owner either keeps it and shows it again ([#show(String, Consumer)], e.g. a selection box,
/// which keeps what was chosen), or makes one for each use ([#showOnce]).
public class UserRefPopupController extends Controller {

    private static final UserRefResource RESOURCE = GWT.create(UserRefResource.class);

    private final UserRefPopup view;
    private final RestFactory restFactory;
    private final FindUserCriteria.Builder criteriaBuilder = new FindUserCriteria.Builder();
    private RestDataProvider<UserRef, ResultPage<UserRef>> dataProvider;
    private UserRef initialSelection;
    private ExpressionTerm additionalTerm;
    private String filter;
    private FindUserContext context = FindUserContext.RUN_AS;
    private Consumer<UserRef> selectionChangeConsumer = userRef -> {
    };

    /// Made by GIN, with its picker.
    ///
    /// @param eventBus                         The application's event bus.
    /// @param view                             The picker.
    /// @param restFactory                      Finds the users and groups.
    /// @param uiConfigCache                    Gives the filter's help link.
    /// @param quickFilterContextHandlerFactory Makes the filter's recent filters.
    @Inject
    public UserRefPopupController(final EventBus eventBus,
                                  final UserRefPopup view,
                                  final RestFactory restFactory,
                                  final UiConfigCache uiConfigCache,
                                  final QuickFilterContextHandlerFactory quickFilterContextHandlerFactory) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        initWidget(view);

        // Not easy to determine if we are dealing in users or groups at this point so just call it
        // Quick Filter
        uiConfigCache.get(uiConfig -> {
            if (uiConfig != null && !isDisposed()) {
                view.registerPopupTextProvider(() -> QuickFilterTooltipUtil.createTooltip(
                        "Quick Filter",
                        UserFields.FILTER_FIELD_DEFINITIONS,
                        uiConfig.getHelpUrlQuickFilter()));
            }
        });
        view.setQuickFilterContextHandler(quickFilterContextHandlerFactory.create(
                UserFields.QUICK_FILTER_CONTEXT, this, () -> taskMonitor().createTaskMonitor()));
        view.setOnFilterChange(this::onFilterChange);
        view.setOnChoose(() -> ControllerDialog.pressAction(this, DialogAction.OK));
        view.setOnSortChange(this::refresh);
    }

    /// @param selectionChangeConsumer Given what is chosen when the picker closes: the choice on
    ///                                OK, or what was chosen when it opened on Cancel.
    public void setSelectionChangeConsumer(final Consumer<UserRef> selectionChangeConsumer) {
        this.selectionChangeConsumer = selectionChangeConsumer;
    }

    /// Shows the picker, again and again, owned by what owns this, as "Select User Or Group".
    ///
    /// @param consumer Given the user or group chosen on OK.
    public void show(final Consumer<UserRef> consumer) {
        show("Select User Or Group", consumer);
    }

    /// Shows the picker, again and again, owned by what owns this.
    ///
    /// @param caption  The dialog's caption.
    /// @param consumer Given the user or group chosen on OK.
    public void show(final String caption, final Consumer<UserRef> consumer) {
        show(ControllerDialog.show(this), caption, consumer);
    }

    /// Shows the picker in a dialog made for one use, owned by `opener` until it is closed.
    ///
    /// @param opener   What shows it.
    /// @param caption  The dialog's caption.
    /// @param consumer Given the user or group chosen on OK.
    public void showOnce(final HasControllerNode opener, final String caption, final Consumer<UserRef> consumer) {
        show(ControllerDialog.showOnce(opener, this), caption, consumer);
    }

    private void show(final ShowPopupEvent.Builder builder, final String caption, final Consumer<UserRef> consumer) {
        initialSelection = getSelected();
        refresh();
        builder.popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(800, 600))
                .caption(caption)
                // Modal, as it asks for a choice: the page behind can't be used, and Tab stays in it
                .modal(true)
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final UserRef selected = getSelected();
                        selectionChangeConsumer.accept(selected);
                        consumer.accept(selected);
                    } else {
                        // Put back what was chosen when it opened (moving through the grid chooses)
                        setSelected(initialSelection);
                        selectionChangeConsumer.accept(initialSelection);
                    }
                    e.hide();
                })
                .fire();
    }

    /// @return The user or group chosen, or null for none.
    public UserRef getSelected() {
        return view.getSelectionModel().getSelected();
    }

    /// @param userRef The user or group to show as chosen, or null for none.
    public void setSelected(final UserRef userRef) {
        view.getSelectionModel().setSelected(userRef);
    }

    /// Fetches a user's or group's current names, as the picker lists them.
    ///
    /// @param userRef  The user or group, or null.
    /// @param consumer Given it with its current names, or as it was if it can't be fetched.
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
                    .owner(this)
                    .taskMonitorFactory(view.getTaskMonitor())
                    .exec();
        }
    }

    /// Fetches the users and groups again.
    public void refresh() {
        if (dataProvider == null) {
            //noinspection Convert2Diamond // GWT
            dataProvider = new RestDataProvider<UserRef, ResultPage<UserRef>>(getEventBus()) {
                @Override
                protected void exec(final Range range,
                                    final Consumer<ResultPage<UserRef>> dataConsumer,
                                    final RestErrorHandler errorHandler) {
                    find(range, dataConsumer, errorHandler);
                }
            };
            view.setDataProvider(dataProvider);
        } else {
            dataProvider.refresh();
        }
    }

    /// @param additionalTerm A term every user or group listed must match, e.g. only groups.
    public void setAdditionalTerm(final ExpressionTerm additionalTerm) {
        this.additionalTerm = additionalTerm;
    }

    /// @param context What the user or group is chosen for, which decides who is listed.
    public void setContext(final FindUserContext context) {
        this.context = context;
        criteriaBuilder.context(context);
    }

    private void onFilterChange(final String text) {
        filter = text;
        if (filter != null) {
            filter = filter.trim();
            if (filter.isEmpty()) {
                filter = null;
            }
        }
        refresh();
    }

    private void find(final Range range,
                      final Consumer<ResultPage<UserRef>> dataConsumer,
                      final RestErrorHandler errorHandler) {
        // The filter text goes to the server verbatim; only the structural term this screen adds on
        // the user's behalf is composed here. See FindUserCriteria.
        ExpressionOperator expression = ExpressionOperator.builder().build();
        if (additionalTerm != null) {
            expression = expression.copy().addTerm(additionalTerm).build();
        }
        criteriaBuilder.expression(expression);
        criteriaBuilder.quickFilter(filter);
        criteriaBuilder.pageRequest(CriteriaUtil.createPageRequest(range));
        criteriaBuilder.sortList(CriteriaUtil.createSortList(view.getSortList()));
        restFactory
                .create(RESOURCE)
                .method(res -> res.find(criteriaBuilder.build()))
                .onSuccess(resultPage -> {
                    view.setFilterError(NullSafe.get(resultPage.getFilterError(), TokenError::getText));
                    dataConsumer.accept(resultPage);
                    Scheduler.get().scheduleDeferred(view::showSelectedAsTabStop);
                })
                .onFailure(errorHandler)
                .owner(this)
                .taskMonitorFactory(view.getTaskMonitor())
                .exec();
    }

}
