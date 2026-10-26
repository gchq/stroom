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

package stroom.analytics.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.analytics.client.component.ScheduledProcessHistoryListController;
import stroom.analytics.client.component.ScheduledProcessListController;
import stroom.analytics.client.presenter.ScheduledProcessingPresenter.ScheduledProcessingView;
import stroom.analytics.shared.ExecutionHistory;
import stroom.analytics.shared.ExecutionSchedule;
import stroom.analytics.shared.ExecutionScheduleResource;
import stroom.analytics.shared.ScheduleBounds;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocPresenter;
import stroom.util.shared.scheduler.CronExpressions;
import stroom.util.shared.scheduler.Schedule;
import stroom.util.shared.scheduler.ScheduleType;
import stroom.widget.component.client.Contexts;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.presenter.client.View;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

public class ScheduledProcessingPresenter
        extends MyPresenterWidget<ScheduledProcessingView> {

    private static final ExecutionScheduleResource EXECUTION_SCHEDULE_RESOURCE =
            GWT.create(ExecutionScheduleResource.class);

    private final RestFactory restFactory;
    private final ScheduledProcessListController scheduleList;
    private final ScheduledProcessHistoryListController historyList;
    private final ScheduledProcessEditPresenter scheduledProcessEditPresenter;
    private DocPresenter<?, ?> documentEditPresenter;
    private DocRef ownerDocRef;
    private boolean readOnly;

    @Inject
    public ScheduledProcessingPresenter(final EventBus eventBus,
                                        final ScheduledProcessingView view,
                                        final RestFactory restFactory,
                                        final ScheduledProcessListController scheduleList,
                                        final ScheduledProcessHistoryListController historyList,
                                        final ScheduledProcessEditPresenter scheduledProcessEditPresenter) {
        super(eventBus, view);
        own(scheduleList);
        own(historyList);
        own(scheduledProcessEditPresenter);
        this.restFactory = restFactory;
        this.scheduleList = scheduleList;
        this.historyList = historyList;
        this.scheduledProcessEditPresenter = scheduledProcessEditPresenter;
        view.setScheduleList(scheduleList);
        view.setHistoryList(historyList);
        scheduleList.setOnAdd(this::add);
        scheduleList.setOnEdit(this::edit);
        scheduleList.setOnDelete(this::remove);
        historyList.setOnReplay(this::replay);

        own(scheduleList.addSelectionHandler(e -> {
            historyList.setExecutionSchedule(scheduleList.getSelected());
        }));
    }

    /// Shows the execution schedules of a document.
    ///
    /// @param ownerDocRef The document that owns the execution schedules.
    public void read(final DocRef ownerDocRef) {
        // Read only as its document is
        final boolean readOnly = context(Contexts.READ_ONLY);
        this.ownerDocRef = ownerDocRef;
        this.readOnly = readOnly;
        scheduleList.read(ownerDocRef);
    }

    public void setDocumentEditPresenter(final DocPresenter<?, ?> documentEditPresenter) {
        this.documentEditPresenter = documentEditPresenter;
    }

    public void add() {
        final ExecutionSchedule newSchedule = ExecutionSchedule
                .builder()
                .name("Daily")
                .enabled(false)
                .schedule(Schedule
                        .builder()
                        .type(ScheduleType.CRON)
                        .expression(CronExpressions.EVERY_DAY_AT_MIDNIGHT.getExpression())
                        .build())
                .scheduleBounds(ScheduleBounds
                        .builder()
                        .startTimeMs(System.currentTimeMillis())
                        .build())
                .owningDoc(ownerDocRef)
                .build();
        add(newSchedule);
    }

    public void replay(final ExecutionHistory executionHistory) {
        if (executionHistory != null && executionHistory.getExecutionSchedule() != null) {
            final ExecutionSchedule newSchedule = executionHistory
                    .getExecutionSchedule()
                    .copy()
                    .name(executionHistory.getExecutionSchedule().getName() + " (replay)")
                    .enabled(false)
                    .scheduleBounds(ScheduleBounds
                            .builder()
                            .startTimeMs(executionHistory.getEffectiveExecutionTimeMs())
                            .endTimeMs(executionHistory.getEffectiveExecutionTimeMs())
                            .build())
                    .build();
            add(newSchedule);
        }
    }

    private void add(final ExecutionSchedule newSchedule) {
        if (readOnly) {
            return;
        }
        if (documentEditPresenter != null && documentEditPresenter.isDirty()) {
            AlertEvent.fireWarn(
                    this,
                    "Please ensure all settings are correct and save before adding executions",
                    null);

        } else {
            scheduledProcessEditPresenter.show(newSchedule, executionSchedule -> {
                if (executionSchedule != null) {
                    restFactory
                            .create(EXECUTION_SCHEDULE_RESOURCE)
                            .method(res -> res.createExecutionSchedule(executionSchedule))
                            .onSuccess(created -> {
                                scheduleList.refresh();
                                scheduleList.setSelected(created);
                            })
                            .taskMonitorFactory(this)
                            .exec();
                }
            });
        }
    }

    public void edit() {
        final ExecutionSchedule selected = scheduleList.getSelected();
        if (selected != null && !readOnly) {
            scheduledProcessEditPresenter.show(selected, executionSchedule -> {
                if (executionSchedule != null) {
                    restFactory
                            .create(EXECUTION_SCHEDULE_RESOURCE)
                            .method(res -> res.updateExecutionSchedule(executionSchedule))
                            .onSuccess(updated -> {
                                scheduleList.refresh();
                                scheduleList.setSelected(updated);
                            })
                            .taskMonitorFactory(this)
                            .exec();
                }
            });
        }
    }

    public void remove() {
        final ExecutionSchedule selected = scheduleList.getSelected();
        if (selected != null && !readOnly) {
            // Deleting a schedule can't be undone, so ask first
            ConfirmEvent.fire(this,
                    "Are you sure you want to delete the execution schedule '" + selected.getName() + "'?",
                    ok -> {
                        if (ok) {
                            restFactory
                                    .create(EXECUTION_SCHEDULE_RESOURCE)
                                    .method(res -> res.deleteExecutionSchedule(selected))
                                    .onSuccess(success -> scheduleList.refresh())
                                    .taskMonitorFactory(this)
                                    .exec();
                        }
                    });
        }
    }


    // --------------------------------------------------------------------------------


    public interface ScheduledProcessingView extends View {

        void setScheduleList(IsWidget view);

        void setHistoryList(IsWidget view);
    }
}
