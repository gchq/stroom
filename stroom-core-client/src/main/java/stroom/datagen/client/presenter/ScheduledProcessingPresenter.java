/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.datagen.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.analytics.shared.ExecutionHistory;
import stroom.analytics.shared.ExecutionSchedule;
import stroom.analytics.shared.ExecutionScheduleResource;
import stroom.analytics.shared.ScheduleBounds;
import stroom.datagen.client.presenter.ScheduledProcessingPresenter.ScheduledProcessingView;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.util.shared.scheduler.CronExpressions;
import stroom.util.shared.scheduler.Schedule;
import stroom.util.shared.scheduler.ScheduleType;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class ScheduledProcessingPresenter
        extends MyPresenterWidget<ScheduledProcessingView> {

    private static final ExecutionScheduleResource EXECUTION_SCHEDULE_RESOURCE =
            GWT.create(ExecutionScheduleResource.class);

    private final RestFactory restFactory;
    private final ScheduledProcessListPresenter scheduledProcessListPresenter;
    private final ScheduledProcessHistoryListPresenter scheduledProcessHistoryListPresenter;
    private final ScheduledProcessEditPresenter scheduledProcessEditPresenter;
    private DocumentEditPresenter<?, ?> documentEditPresenter;
    private DocRef ownerDocRef;

    @Inject
    public ScheduledProcessingPresenter(final EventBus eventBus,
                                        final ScheduledProcessingView view,
                                        final RestFactory restFactory,
                                        final ScheduledProcessListPresenter scheduledProcessListPresenter,
                                        final ScheduledProcessHistoryListPresenter scheduledProcessHistoryListPresenter,
                                        final ScheduledProcessEditPresenter scheduledProcessEditPresenter) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.scheduledProcessListPresenter = scheduledProcessListPresenter;
        this.scheduledProcessHistoryListPresenter = scheduledProcessHistoryListPresenter;
        this.scheduledProcessEditPresenter = scheduledProcessEditPresenter;
        view.setScheduleList(scheduledProcessListPresenter.getView());
        view.setHistoryList(scheduledProcessHistoryListPresenter.getView());
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(scheduledProcessListPresenter.addSelectionHandler(e -> {
            scheduledProcessHistoryListPresenter.setExecutionSchedule(scheduledProcessListPresenter.getSelected());
        }));
    }

    public void read(final DocRef ownerDocRef) {
        this.ownerDocRef = ownerDocRef;
        scheduledProcessListPresenter.read(ownerDocRef);
    }

    public void setDocumentEditPresenter(final DocumentEditPresenter<?, ?> documentEditPresenter) {
        this.documentEditPresenter = documentEditPresenter;
        scheduledProcessListPresenter.setScheduledProcessingPresenter(this);
        scheduledProcessHistoryListPresenter.setScheduledProcessingPresenter(this);
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
        if (documentEditPresenter != null && documentEditPresenter.isDirty()) {
            AlertEvent.fireWarn(
                    this,
                    "Please ensure all settings are correct and save before adding executions",
                    null);

        } else {
            scheduledProcessEditPresenter.setTaskMonitorFactory(this);
            scheduledProcessEditPresenter.show(newSchedule, executionSchedule -> {
                if (executionSchedule != null) {
                    restFactory
                            .create(EXECUTION_SCHEDULE_RESOURCE)
                            .method(res -> res.createExecutionSchedule(executionSchedule))
                            .onSuccess(created -> {
                                scheduledProcessListPresenter.refresh();
                                scheduledProcessListPresenter.setSelected(created);
                            })
                            .taskMonitorFactory(this)
                            .exec();
                }
            });
        }
    }

    public void edit() {
        final ExecutionSchedule selected = scheduledProcessListPresenter.getSelected();
        if (selected != null) {
            scheduledProcessEditPresenter.setTaskMonitorFactory(this);
            scheduledProcessEditPresenter.show(selected, executionSchedule -> {
                if (executionSchedule != null) {
                    restFactory
                            .create(EXECUTION_SCHEDULE_RESOURCE)
                            .method(res -> res.updateExecutionSchedule(executionSchedule))
                            .onSuccess(updated -> {
                                scheduledProcessListPresenter.refresh();
                                scheduledProcessListPresenter.setSelected(updated);
                            })
                            .taskMonitorFactory(this)
                            .exec();
                }
            });
        }
    }

    public void remove() {
        final ExecutionSchedule selected = scheduledProcessListPresenter.getSelected();
        if (selected != null) {
            restFactory
                    .create(EXECUTION_SCHEDULE_RESOURCE)
                    .method(res -> res.deleteExecutionSchedule(selected))
                    .onSuccess(success -> scheduledProcessListPresenter.refresh())
                    .taskMonitorFactory(this)
                    .exec();
        }
    }

    public interface ScheduledProcessingView extends View {


        void setScheduleList(View view);

        void setHistoryList(View view);
    }
}
