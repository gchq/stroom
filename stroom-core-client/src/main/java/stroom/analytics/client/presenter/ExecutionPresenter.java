package stroom.analytics.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.analytics.client.presenter.ExecutionPresenter.ExecutionView;
import stroom.analytics.shared.ExecutionHistory;
import stroom.analytics.shared.ExecutionSchedule;
import stroom.analytics.shared.ExecutionScheduleResource;
import stroom.analytics.shared.ScheduleBounds;
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

public class ExecutionPresenter extends MyPresenterWidget<ExecutionView> {

    private static final ExecutionScheduleResource EXECUTION_SCHEDULE_RESOURCE =
            GWT.create(ExecutionScheduleResource.class);

    private final RestFactory restFactory;
    private final ExecutionScheduleListPresenter executionScheduleListPresenter;
    private final ExecutionHistoryListPresenter executionHistoryListPresenter;
    private final ExecutionScheduleEditPresenter executionScheduleEditPresenter;
    private DocumentEditPresenter<?, ?> documentEditPresenter;
    private DocRef ownerDocRef;

    @Inject
    public ExecutionPresenter(final EventBus eventBus,
                              final ExecutionView view,
                              final RestFactory restFactory,
                              final ExecutionScheduleListPresenter executionScheduleListPresenter,
                              final ExecutionHistoryListPresenter executionHistoryListPresenter,
                              final ExecutionScheduleEditPresenter executionScheduleEditPresenter) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.executionScheduleListPresenter = executionScheduleListPresenter;
        this.executionHistoryListPresenter = executionHistoryListPresenter;
        this.executionScheduleEditPresenter = executionScheduleEditPresenter;
        view.setScheduleList(executionScheduleListPresenter.getView());
        view.setHistoryList(executionHistoryListPresenter.getView());
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(executionScheduleListPresenter.addSelectionHandler(e -> {
            executionHistoryListPresenter.setExecutionSchedule(executionScheduleListPresenter.getSelected());
        }));
    }

    public void read(final DocRef ownerDocRef) {
        this.ownerDocRef = ownerDocRef;
        executionScheduleListPresenter.read(ownerDocRef);
    }

    public void setDocumentEditPresenter(final DocumentEditPresenter<?, ?> documentEditPresenter) {
        this.documentEditPresenter = documentEditPresenter;
        executionScheduleListPresenter.setExecutionPresenter(this);
        executionHistoryListPresenter.setExecutionPresenter(this);
    }

    public void add() {
        final ExecutionSchedule newSchedule = ExecutionSchedule
                .builder()
                .name("Daily")
                .enabled(false)
                .schedule(Schedule
                        .builder()
                        .type(ScheduleType.CRON)
                        .expression(CronExpressions.EVERY_DAY.getExpression())
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
                    "Please save the rule and ensure all settings are correct before adding executions",
                    null);

        } else {
            executionScheduleEditPresenter.show(newSchedule, executionSchedule -> {
                if (executionSchedule != null) {
                    restFactory
                            .builder()
                            .forType(ExecutionSchedule.class)
                            .onSuccess(created -> {
                                executionScheduleListPresenter.refresh();
                                executionScheduleListPresenter.setSelected(created);
                            })
                            .call(EXECUTION_SCHEDULE_RESOURCE)
                            .createExecutionSchedule(executionSchedule);
                }
            });
        }
    }

    public void edit() {
        final ExecutionSchedule selected = executionScheduleListPresenter.getSelected();
        if (selected != null) {
            executionScheduleEditPresenter.show(selected, executionSchedule -> {
                if (executionSchedule != null) {
                    restFactory
                            .builder()
                            .forType(ExecutionSchedule.class)
                            .onSuccess(updated -> {
                                executionScheduleListPresenter.refresh();
                                executionScheduleListPresenter.setSelected(updated);
                            })
                            .call(EXECUTION_SCHEDULE_RESOURCE)
                            .updateExecutionSchedule(executionSchedule);
                }
            });
        }
    }

    public void remove() {
        final ExecutionSchedule selected = executionScheduleListPresenter.getSelected();
        if (selected != null) {
            restFactory
                    .builder()
                    .forType(Boolean.class)
                    .onSuccess(success -> executionScheduleListPresenter.refresh())
                    .call(EXECUTION_SCHEDULE_RESOURCE)
                    .deleteExecutionSchedule(selected);
        }
    }

    public interface ExecutionView extends View {


        void setScheduleList(View view);

        void setHistoryList(View view);
    }
}
