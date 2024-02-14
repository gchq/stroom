package stroom.analytics.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.analytics.shared.ExecutionSchedule;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.explorer.client.presenter.EntityDropDownPresenter;
import stroom.job.client.presenter.SchedulePresenter;
import stroom.job.client.presenter.ScheduledTimeClient;
import stroom.node.client.NodeManager;
import stroom.util.shared.scheduler.Schedule;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.presenter.Size;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.function.Consumer;

public class ExecutionScheduleEditPresenter
        extends MyPresenterWidget<ExecutionScheduleEditView>
        implements ProcessingStatusUiHandlers, HasDirtyHandlers {

    private final EntityDropDownPresenter errorFeedPresenter;
    private final SchedulePresenter schedulePresenter;
    private final ScheduledTimeClient scheduledTimeClient;
    private ExecutionSchedule executionSchedule;
    private Schedule schedule;

    @Inject
    public ExecutionScheduleEditPresenter(final EventBus eventBus,
                                          final ExecutionScheduleEditView view,
                                          final EntityDropDownPresenter errorFeedPresenter,
                                          final NodeManager nodeManager,
                                          final SchedulePresenter schedulePresenter,
                                          final ScheduledTimeClient scheduledTimeClient) {
        super(eventBus, view);
        view.setUiHandlers(this);
        this.errorFeedPresenter = errorFeedPresenter;
        this.schedulePresenter = schedulePresenter;
        this.scheduledTimeClient = scheduledTimeClient;

        nodeManager.listAllNodes(
                list -> {
                    if (list != null && list.size() > 0) {
                        getView().setNodes(list);
                    }
                },
                throwable -> AlertEvent
                        .fireError(this,
                                "Error",
                                throwable.getMessage(),
                                null));
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(errorFeedPresenter.addDataSelectionHandler(e -> onDirty()));
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
                        final ExecutionSchedule written = write();
                        scheduledTimeClient.validate(written.getSchedule(), scheduledTimes -> {
                            consumer.accept(written);
                            e.hide();
                        });
                    } else {
                        e.hide();
                    }
                })
                .fire();
    }

    public void read(final ExecutionSchedule executionSchedule) {
        this.executionSchedule = executionSchedule;
        this.schedule = executionSchedule.getSchedule();
        getView().setName(executionSchedule.getName());
        getView().setEnabled(executionSchedule.isEnabled());
        getView().setNode(executionSchedule.getNodeName());
        getView().setScheduleBounds(executionSchedule.getScheduleBounds());
        updateScheduleLabel();
    }

    public ExecutionSchedule write() {
        return executionSchedule
                .copy()
                .name(getView().getName())
                .enabled(getView().isEnabled())
                .nodeName(getView().getNode())
                .schedule(schedule)
                .contiguous(true)
                .scheduleBounds(getView().getScheduleBounds())
                .build();
    }

    @Override
    public void onRefreshProcessingStatus() {
    }

    @Override
    public void onScheduleClick() {
        schedulePresenter.setSchedule(schedule, null, null);
        schedulePresenter.show(schedule -> {
            this.schedule = schedule;
            updateScheduleLabel();
            onDirty();
        });
    }

    private void updateScheduleLabel() {
        if (schedule == null) {
            getView().setScheduleText("Set schedule");
        } else {
            getView().setScheduleText(schedule.toString());
        }
    }

    @Override
    public void onDirty() {
        DirtyEvent.fire(this, true);
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }
}
