/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.analytics.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.analytics.client.presenter.BatchExecutionScheduleEditPresenter.BatchExecutionScheduleEditView;
import stroom.analytics.shared.ExecutionSchedule;
import stroom.analytics.shared.ExecutionScheduleResource;
import stroom.analytics.shared.ScheduleBounds;
import stroom.dispatch.client.RestFactory;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.job.shared.ScheduleRestriction;
import stroom.node.client.NodeManager;
import stroom.schedule.client.ScheduleBox;
import stroom.schedule.client.SchedulePopup;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.presenter.UserRefSelectionBoxPresenter;
import stroom.util.shared.NullSafe;
import stroom.widget.datepicker.client.DateTimeBox;
import stroom.widget.datepicker.client.DateTimePopup;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.presenter.Size;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.List;
import java.util.function.Consumer;
import javax.inject.Inject;

public class BatchExecutionScheduleEditPresenter
        extends MyPresenterWidget<BatchExecutionScheduleEditView>
        implements ProcessingStatusUiHandlers, HasDirtyHandlers {

    private static final ExecutionScheduleResource EXECUTION_SCHEDULE_RESOURCE =
            GWT.create(ExecutionScheduleResource.class);

    private final UserRefSelectionBoxPresenter userRefSelectionBoxPresenter;
    private final DocSelectionBoxPresenter errorFeedPresenter;

    @Inject
    public BatchExecutionScheduleEditPresenter(final EventBus eventBus,
                                               final BatchExecutionScheduleEditView view,
                                               final DocSelectionBoxPresenter errorFeedPresenter,
                                               final NodeManager nodeManager,
                                               final Provider<SchedulePopup> schedulePresenterProvider,
                                               final Provider<DateTimePopup> dateTimePopupProvider,
                                               final UserRefSelectionBoxPresenter userRefSelectionBoxPresenter) {
        super(eventBus, view);
        this.userRefSelectionBoxPresenter = userRefSelectionBoxPresenter;
        this.errorFeedPresenter = errorFeedPresenter;

        view.setUiHandlers(this);
        view.getStartTime().setPopupProvider(dateTimePopupProvider);
        view.getEndTime().setPopupProvider(dateTimePopupProvider);

        view.getScheduleBox().setSchedulePresenterProvider(schedulePresenterProvider);
        view.getScheduleBox().setScheduleRestriction(new ScheduleRestriction(false, false, true));


        nodeManager.listAllNodes(
            list -> {
                if (NullSafe.hasItems(list)) {
                    getView().setNodes(list);
                }
            },
            throwable -> AlertEvent
                .fireError(this,
                        "Error",
                        throwable.getMessage(),
                        null),
            this
        );

    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(errorFeedPresenter.addDataSelectionHandler(e -> onDirty()));
        registerHandler(getView().getScheduleBox().addValueChangeHandler(e -> onDirty()));
        registerHandler(userRefSelectionBoxPresenter.addDataSelectionHandler(e -> onDirty()));
    }

    public void show(final Consumer<Boolean> consumer) {
        final PopupSize popupSize = PopupSize.builder()
                .width(Size
                        .builder()
                        .resizable(true)
                        .build())
                .height(Size
                        .builder()
                        .resizable(true)
                        .build())
                .build();

        ShowPopupEvent.builder(this)
                .popupType(PopupType.CLOSE_DIALOG)
                .popupSize(popupSize)
                .onShow(e -> {
                    getView().focus();
//                    selectedAndFilteredConfirmationDialog.temp_test();
                })
                .caption("Batch Change Selected Schedules")
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        write(applyToFiltered -> {
                            consumer.accept(applyToFiltered);
                            e.hide();
                        }, e);
                    } else {
                        e.hide();
                    }
                })
                .fire();
    }

    public void write(final Consumer<Boolean> consumer,
                      final HidePopupRequestEvent event) {
        getView().getScheduleBox().validate(scheduledTimes -> {
            if (scheduledTimes == null) {
                event.reset();
            } else if (getView().getScheduleBox().isEnabled() && scheduledTimes.isError()) {
                AlertEvent.fireWarn(this, scheduledTimes.getError(), event::reset);
            } else {
                if (getView().getStartTime().isEnabled() && !getView().getStartTime().isValid()) {
                    AlertEvent.fireWarn(this, "Invalid start time", event::reset);
                } else if (getView().getEndTime().isEnabled() && !getView().getEndTime().isValid()) {
                    AlertEvent.fireWarn(this, "Invalid end time", event::reset);
                } else {
                    consumer.accept(false);
                }
            }
        });
    }

    @Override
    public void onRefreshProcessingStatus() {
    }

    @Override
    public void onDirty() {
        DirtyEvent.fire(this, true);
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }

    public interface BatchExecutionScheduleEditView
            extends View,
            HasUiHandlers<ProcessingStatusUiHandlers>,
            Focus {

        ExecutionSchedule getUpdatedExecutionSchedule(ExecutionSchedule  executionSchedule);



        String getName();

        void setName(String name);

        boolean isEnabled();

        void setEnabled(final boolean enabled);

        void setNodes(List<String> nodes);

        String getNode();

        void setNode(String node);

        ScheduleBox getScheduleBox();

        DateTimeBox getStartTime();

        DateTimeBox getEndTime();

        void setRunAsUserView();

        boolean isAnyBoxEnabled();

        String getEditSummary();
    }
}
