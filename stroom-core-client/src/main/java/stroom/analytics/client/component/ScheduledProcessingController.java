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

package stroom.analytics.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.analytics.shared.ExecutionHistory;
import stroom.analytics.shared.ExecutionSchedule;
import stroom.analytics.shared.ExecutionScheduleResource;
import stroom.analytics.shared.ScheduleBounds;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.util.shared.scheduler.CronExpressions;
import stroom.util.shared.scheduler.Schedule;
import stroom.util.shared.scheduler.ScheduleType;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.BooleanSupplier;

/// A document's scheduled executions ([ScheduledProcessing]): its schedules, which can be added
/// (also by replaying an execution), edited and deleted unless it is read only, and the history of
/// the one selected. A data generator's Execution tab, and a rule's or report's when scheduled.
public class ScheduledProcessingController extends Controller {

    private static final ExecutionScheduleResource EXECUTION_SCHEDULE_RESOURCE =
            GWT.create(ExecutionScheduleResource.class);

    private final RestFactory restFactory;
    private final ScheduledProcessListController scheduleList;
    private final ScheduledProcessHistoryListController historyList;
    private final ScheduledProcessEditorController scheduledProcessEditor;
    // Whether the document has unsaved changes, which must be saved before a schedule is added
    private BooleanSupplier isDocumentDirty = () -> false;
    private DocRef ownerDocRef;
    private boolean readOnly;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus             The application's event bus.
    /// @param view                 The content.
    /// @param restFactory          Creates, changes and deletes schedules.
    /// @param scheduleList         The schedules.
    /// @param historyList          The history of the schedule selected.
    /// @param scheduledProcessEditor The dialog that edits a schedule.
    @Inject
    public ScheduledProcessingController(final EventBus eventBus,
                                         final ScheduledProcessing view,
                                         final RestFactory restFactory,
                                         final ScheduledProcessListController scheduleList,
                                         final ScheduledProcessHistoryListController historyList,
                                         final ScheduledProcessEditorController scheduledProcessEditor) {
        super(eventBus);
        initWidget(view);
        own(scheduledProcessEditor);
        this.restFactory = restFactory;
        this.scheduleList = own(scheduleList).attach(view.getScheduleList());
        this.historyList = own(historyList).attach(view.getHistoryList());
        this.scheduledProcessEditor = scheduledProcessEditor;
        scheduleList.setOnAdd(this::add);
        scheduleList.setOnEdit(this::edit);
        scheduleList.setOnDelete(this::remove);
        historyList.setOnReplay(this::replay);

        own(scheduleList.addSelectionHandler(e -> {
            historyList.setExecutionSchedule(scheduleList.getSelected());
        }));
        // Read only as its document is, following a lock change while it is open, as the list's
        // buttons do
        watch(Contexts.READ_ONLY, readOnly -> this.readOnly = readOnly);
    }

    /// Shows the execution schedules of a document.
    ///
    /// @param ownerDocRef The document that owns the execution schedules.
    public void read(final DocRef ownerDocRef) {
        this.ownerDocRef = ownerDocRef;
        scheduleList.read(ownerDocRef);
    }

    /// @param isDocumentDirty Whether the document has unsaved changes, which must be saved before a
    ///                        schedule is added.
    public void setIsDocumentDirty(final BooleanSupplier isDocumentDirty) {
        this.isDocumentDirty = isDocumentDirty;
    }

    private void add() {
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

    private void replay(final ExecutionHistory executionHistory) {
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
        if (isDocumentDirty.getAsBoolean()) {
            AlertEvent.fireWarn(
                    this,
                    "Please ensure all settings are correct and save before adding executions",
                    null);

        } else {
            scheduledProcessEditor.show(newSchedule, executionSchedule -> {
                if (executionSchedule != null) {
                    restFactory
                            .create(EXECUTION_SCHEDULE_RESOURCE)
                            .method(res -> res.createExecutionSchedule(executionSchedule))
                            .onSuccess(created -> {
                                scheduleList.refresh();
                                scheduleList.setSelected(created);
                            })
                            .owner(this)
                            .taskMonitorFactory(taskMonitor())
                            .exec();
                }
            });
        }
    }

    private void edit() {
        final ExecutionSchedule selected = scheduleList.getSelected();
        if (selected != null && !readOnly) {
            scheduledProcessEditor.show(selected, executionSchedule -> {
                if (executionSchedule != null) {
                    restFactory
                            .create(EXECUTION_SCHEDULE_RESOURCE)
                            .method(res -> res.updateExecutionSchedule(executionSchedule))
                            .onSuccess(updated -> {
                                scheduleList.refresh();
                                scheduleList.setSelected(updated);
                            })
                            .owner(this)
                            .taskMonitorFactory(taskMonitor())
                            .exec();
                }
            });
        }
    }

    private void remove() {
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
                                    .owner(this)
                                    .taskMonitorFactory(taskMonitor())
                                    .exec();
                        }
                    });
        }
    }


}
