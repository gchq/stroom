package stroom.node.client;

import stroom.alert.client.event.ConfirmEvent;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.dispatch.client.RestFactory;
import stroom.job.shared.Job;
import stroom.job.shared.JobNode;
import stroom.job.shared.JobNode.JobType;
import stroom.job.shared.JobNodeInfo;
import stroom.job.shared.JobNodeResource;
import stroom.job.shared.JobNodeUtil;
import stroom.job.shared.ScheduleReferenceTime;
import stroom.preferences.client.DateTimeFormatter;
import stroom.schedule.client.SchedulePopup;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.task.client.TaskListener;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.client.DataGridUtil.BrowserEventHandler;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.scheduler.Schedule;

import com.google.gwt.cell.client.Cell.Context;
import com.google.gwt.cell.client.FieldUpdater;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.inject.Inject;

import java.util.HashMap;
import java.util.Map;

public class JobNodeListHelper {

    private static final JobNodeResource JOB_NODE_RESOURCE = GWT.create(JobNodeResource.class);

    private final DateTimeFormatter dateTimeFormatter;
    private final RestFactory restFactory;
    private final UiConfigCache clientPropertyCache;
    private final SchedulePopup schedulePresenter;

    private final Map<JobNode, JobNodeInfo> latestNodeInfo = new HashMap<>();

    @Inject
    public JobNodeListHelper(final DateTimeFormatter dateTimeFormatter,
                             final RestFactory restFactory,
                             final UiConfigCache clientPropertyCache,
                             final SchedulePopup schedulePresenter) {
        this.dateTimeFormatter = dateTimeFormatter;
        this.restFactory = restFactory;
        this.clientPropertyCache = clientPropertyCache;
        this.schedulePresenter = schedulePresenter;
    }

    public void putJobNodeInfo(final JobNode jobNode, final JobNodeInfo jobNodeInfo) {
        latestNodeInfo.put(jobNode, jobNodeInfo);
    }

    public void removeJobNodeInfo(final JobNode jobNode) {
        latestNodeInfo.remove(jobNode);
    }

    public BrowserEventHandler<JobNode> createExecuteJobNowHandler(final HasHandlers handlers,
                                                                   final TaskListener taskListener) {
        return (Context context, Element elem, JobNode jobNode, NativeEvent event) ->
                ConfirmEvent.fire(handlers,
                        "Are you sure you want to execute job '" + jobNode.getJobName()
                                + "' on node '" + jobNode.getNodeName() + "' now. " +
                                "\n\nThe job will execute shortly, likely within ten seconds. " +
                                "You can check it has run by refreshing the table until the 'Last Executed' " +
                                "column has been updated.",
                        ok -> {
                            restFactory.create(JOB_NODE_RESOURCE)
                                    .call(resource -> resource.execute(jobNode.getId()))
                                    .taskListener(taskListener)
                                    .exec();
                        });
    }

    public void showSchedule(final JobNode row,
                             final TaskListener taskListener,
                             final Runnable onSuccessHandler) {
        restFactory
                .create(JOB_NODE_RESOURCE)
                .method(res -> res.info(row.getJob().getName(), row.getNodeName()))
                .onSuccess(result ->
                        setSchedule(row, result, taskListener, onSuccessHandler))
                .onFailure(throwable ->
                        setSchedule(row, null, taskListener, onSuccessHandler))
                .taskListener(taskListener)
                .exec();
    }

    public FieldUpdater<JobNode, TickBoxState> createEnabledStateFieldUpdater(
            final TaskListener taskListener,
            final Runnable onSuccessHandler) {

        return (int rowIndex, JobNode jobNode, TickBoxState value) -> {
            final boolean isEnabled = GwtNullSafe.isTrue(value.toBoolean());
            jobNode.setEnabled(isEnabled);
            restFactory
                    .create(JOB_NODE_RESOURCE)
                    .call(jobNodeResource -> {
                        jobNodeResource.setEnabled(jobNode.getId(), isEnabled);
                    })
                    .onSuccess(aVoid -> {
                        GwtNullSafe.run(onSuccessHandler);
                    })
                    .taskListener(taskListener)
                    .exec();
        };
    }

    private void setSchedule(final JobNode jobNode,
                             final JobNodeInfo jobNodeInfo,
                             final TaskListener taskListener,
                             final Runnable onSuccessHandler) {
        final Schedule currentSchedule = JobNodeUtil.getSchedule(jobNode);
        JobNodeInfo info = jobNodeInfo;

        if (currentSchedule != null) {
            if (info == null) {
                info = JobNodeInfo.empty();
            }

            schedulePresenter.setSchedule(currentSchedule, new ScheduleReferenceTime(
                    info.getScheduleReferenceTime(),
                    info.getLastExecutedTime()));
            schedulePresenter.show(schedule -> {
                JobNodeUtil.setSchedule(jobNode, schedule);
                restFactory
                        .create(JOB_NODE_RESOURCE)
                        .call(res -> res.setSchedule(jobNode.getId(), schedule))
                        .onSuccess(result ->
                                GwtNullSafe.run(onSuccessHandler))
                        .taskListener(taskListener)
                        .exec();
            });
        }
    }

    public String getCurrentTaskCountAsStr(JobNode jobNode) {
        return GwtNullSafe.getOrElse(
                latestNodeInfo.get(jobNode),
                info -> ModelStringUtil.formatCsv(info.getCurrentTaskCount()),
                "?");
    }

    public String getLastExecutedTimeAsStr(JobNode jobNode) {
        if (GwtNullSafe.test(jobNode, jobNode2 ->
                jobNode2.getJobType() == JobType.CRON
                        || jobNode2.getJobType() == JobType.FREQUENCY)) {
            return GwtNullSafe.getOrElse(
                    latestNodeInfo.get(jobNode),
                    info -> dateTimeFormatter.formatWithDuration(info.getLastExecutedTime()),
                    "?");
        } else {
            return "N/A";
        }
    }

    public String getNextScheduledTimeAsStr(JobNode jobNode) {
        if (GwtNullSafe.test(jobNode, jobNode2 ->
                jobNode2.getJobType() == JobType.CRON
                        || jobNode2.getJobType() == JobType.FREQUENCY)) {
            return GwtNullSafe.getOrElse(
                    latestNodeInfo.get(jobNode),
                    info -> dateTimeFormatter.formatWithDuration(info.getNextScheduledTime()),
                    "?");
        } else {
            return "N/A";
        }
    }

    public static boolean isJobNodeEnabled(final JobNode jobNode) {
        if (jobNode == null) {
            return false;
        } else {
            // A job node is only enabled if the parent job is also enabled
            return jobNode.isEnabled()
                    && GwtNullSafe.isTrue(jobNode.getJob(), Job::isEnabled);
        }
    }

    public static Preset buildRunIconPreset(final JobNode jobNode) {
        if (jobNode != null
                && jobNode.getJobType() != JobType.DISTRIBUTED
                && jobNode.getJobType() != JobType.UNKNOWN) {
            return SvgPresets.RUN
                    .title("Execute job on node " + jobNode.getNodeName() + " now.");
        }
        return null;
    }

    public static String buildJobTypeStr(JobNode jobNode) {
        //noinspection EnhancedSwitchMigration // not in GWT
        switch (jobNode.getJobType()) {
            case CRON:
                return "Cron";
            case FREQUENCY:
                return "Frequency";
            case DISTRIBUTED:
                return "Distributed";
            default:
                return null;
        }
    }

}
