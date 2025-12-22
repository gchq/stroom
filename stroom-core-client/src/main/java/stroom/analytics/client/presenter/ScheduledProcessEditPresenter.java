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
import stroom.analytics.shared.ExecutionSchedule;
import stroom.analytics.shared.ExecutionScheduleResource;
import stroom.analytics.shared.ScheduleBounds;
import stroom.dispatch.client.RestFactory;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.job.shared.ScheduleReferenceTime;
import stroom.job.shared.ScheduleRestriction;
import stroom.node.client.NodeManager;
import stroom.schedule.client.SchedulePopup;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.presenter.UserRefSelectionBoxPresenter;
import stroom.security.shared.FindUserContext;
import stroom.util.shared.NullSafe;
import stroom.widget.datepicker.client.DateTimePopup;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.presenter.Size;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.function.Consumer;

public class ScheduledProcessEditPresenter
        extends MyPresenterWidget<ScheduledProcessEditView>
        implements ProcessingStatusUiHandlers, HasDirtyHandlers {

    private static final ExecutionScheduleResource EXECUTION_SCHEDULE_RESOURCE =
            GWT.create(ExecutionScheduleResource.class);

    private final DocSelectionBoxPresenter errorFeedPresenter;
    private final UserRefSelectionBoxPresenter userRefSelectionBoxPresenter;
    private final ClientSecurityContext clientSecurityContext;
    private ExecutionSchedule executionSchedule;

    @Inject
    public ScheduledProcessEditPresenter(final EventBus eventBus,
                                         final ScheduledProcessEditView view,
                                         final DocSelectionBoxPresenter errorFeedPresenter,
                                         final NodeManager nodeManager,
                                         final Provider<SchedulePopup> schedulePresenterProvider,
                                         final Provider<DateTimePopup> dateTimePopupProvider,
                                         final RestFactory restFactory,
                                         final UserRefSelectionBoxPresenter userRefSelectionBoxPresenter,
                                         final ClientSecurityContext clientSecurityContext) {
        super(eventBus, view);
        this.userRefSelectionBoxPresenter = userRefSelectionBoxPresenter;
        this.clientSecurityContext = clientSecurityContext;
        view.setRunAsUserView(userRefSelectionBoxPresenter.getView());
        userRefSelectionBoxPresenter.setContext(FindUserContext.RUN_AS);

        view.setUiHandlers(this);
        view.getStartTime().setPopupProvider(dateTimePopupProvider);
        view.getEndTime().setPopupProvider(dateTimePopupProvider);
        this.errorFeedPresenter = errorFeedPresenter;

        view.getScheduleBox().setSchedulePresenterProvider(schedulePresenterProvider);
        view.getScheduleBox().setScheduleRestriction(new ScheduleRestriction(false, false, true));
        view.getScheduleBox().setScheduleReferenceTimeConsumer(scheduleReferenceTimeConsumer -> restFactory
                .create(EXECUTION_SCHEDULE_RESOURCE)
                .method(res -> res.fetchTracker(executionSchedule))
                .onSuccess(tracker -> {
                    Long lastExecuted = null;
                    if (tracker != null) {
                        lastExecuted = tracker.getLastEffectiveExecutionTimeMs();
                    }
                    Long referenceTime = lastExecuted;
                    if (referenceTime == null) {
                        referenceTime = getView().getStartTime().getValue();
                    }
                    if (referenceTime == null) {
                        referenceTime = System.currentTimeMillis();
                    }

                    scheduleReferenceTimeConsumer.accept(new ScheduleReferenceTime(referenceTime,
                            lastExecuted));
                })
                .taskMonitorFactory(this)
                .exec());

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
                this);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(errorFeedPresenter.addDataSelectionHandler(e -> onDirty()));
        registerHandler(getView().getScheduleBox().addValueChangeHandler(e -> onDirty()));
        registerHandler(userRefSelectionBoxPresenter.addDataSelectionHandler(e -> onDirty()));
    }

    public void show(final ExecutionSchedule executionSchedule,
                     final Consumer<ExecutionSchedule> consumer) {
        read(executionSchedule);

        final Size width = Size.builder().max(1000).resizable(true).build();
        final Size height = Size.builder().build();
        final PopupSize popupSize = PopupSize.builder().width(width).height(height).build();

        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption(executionSchedule.getId() == null
                        ? "Create Schedule"
                        : "Edit Schedule")
                .onShow(e -> getView().focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        write(written -> {
                            consumer.accept(written);
                            e.hide();
                        }, e);
                    } else {
                        e.hide();
                    }
                })
                .fire();
    }

    public void read(final ExecutionSchedule executionSchedule) {
        this.executionSchedule = executionSchedule;
        getView().setName(executionSchedule.getName());
        getView().setEnabled(executionSchedule.isEnabled());
        getView().setNode(executionSchedule.getNodeName());
        setScheduleBounds(executionSchedule.getScheduleBounds());
        getView().getScheduleBox().setValue(executionSchedule.getSchedule());

        if (executionSchedule.getRunAsUser() == null) {
            userRefSelectionBoxPresenter.setSelected(clientSecurityContext.getUserRef());
        } else {
            userRefSelectionBoxPresenter.setSelected(executionSchedule.getRunAsUser());
        }
    }

    public void write(final Consumer<ExecutionSchedule> consumer,
                      final HidePopupRequestEvent event) {
        getView().getScheduleBox().validate(scheduledTimes -> {
            if (scheduledTimes == null) {
                event.reset();
            } else if (scheduledTimes.isError()) {
                AlertEvent.fireWarn(this, scheduledTimes.getError(), event::reset);
            } else {
                if (!getView().getStartTime().isValid()) {
                    AlertEvent.fireWarn(this, "Invalid start time", event::reset);
                } else if (!getView().getEndTime().isValid()) {
                    AlertEvent.fireWarn(this, "Invalid end time", event::reset);
                } else {
                    final ScheduleBounds scheduleBounds = new ScheduleBounds(
                            getView().getStartTime().getValue(),
                            getView().getEndTime().getValue());
                    final ExecutionSchedule schedule = executionSchedule
                            .copy()
                            .name(getView().getName())
                            .enabled(getView().isEnabled())
                            .nodeName(getView().getNode())
                            .schedule(scheduledTimes.getSchedule())
                            .contiguous(true)
                            .scheduleBounds(scheduleBounds)
                            .runAsUser(userRefSelectionBoxPresenter.getSelected())
                            .build();
                    consumer.accept(schedule);
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

    private void setScheduleBounds(final ScheduleBounds scheduleBounds) {
        if (scheduleBounds == null) {
            getView().getStartTime().setValue(null);
            getView().getEndTime().setValue(null);
        } else {
            getView().getStartTime().setValue(scheduleBounds.getStartTimeMs());
            getView().getEndTime().setValue(scheduleBounds.getEndTimeMs());
        }
    }
}
