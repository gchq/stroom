package stroom.node.client;

import stroom.alert.client.event.ConfirmEvent;
import stroom.cell.info.client.CommandLink;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.dispatch.client.RestFactory;
import stroom.job.shared.Job;
import stroom.job.shared.JobNode;
import stroom.job.shared.JobNode.JobType;
import stroom.job.shared.JobNodeAndInfo;
import stroom.job.shared.JobNodeInfo;
import stroom.job.shared.JobNodeResource;
import stroom.job.shared.JobNodeUtil;
import stroom.job.shared.ScheduleReferenceTime;
import stroom.preferences.client.DateTimeFormatter;
import stroom.schedule.client.SchedulePopup;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.svg.shared.SvgImage;
import stroom.task.client.TaskListener;
import stroom.util.client.DataGridUtil.BrowserEventHandler;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.scheduler.Schedule;
import stroom.widget.button.client.InlineSvgToggleButton;

import com.google.gwt.cell.client.Cell.Context;
import com.google.gwt.cell.client.FieldUpdater;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.inject.Inject;

import java.util.function.Function;

public class JobNodeListHelper {

    private static final JobNodeResource JOB_NODE_RESOURCE = GWT.create(JobNodeResource.class);
    private static final String FILTER_BTN_TITLE_ON = "Click to show all jobs";
    private static final String FILTER_BTN_TITLE_OFF = "Click to show only enabled jobs";

    private final DateTimeFormatter dateTimeFormatter;
    private final RestFactory restFactory;
    private final SchedulePopup schedulePresenter;

    // nodeName => jobName => jobNodeInfo
//    private final Map<String, Map<String, JobNodeInfo>> latestNodeInfo = new HashMap<>();

    @Inject
    public JobNodeListHelper(final DateTimeFormatter dateTimeFormatter,
                             final RestFactory restFactory,
                             final SchedulePopup schedulePresenter) {
        this.dateTimeFormatter = dateTimeFormatter;
        this.restFactory = restFactory;
        this.schedulePresenter = schedulePresenter;
    }

//    public void putJobNodeInfo(final JobNode jobNode, final JobNodeInfo jobNodeInfo) {
//        if (jobNode != null) {
//            putJobNodeInfo(jobNode.getNodeName(), jobNode.getJobName(), jobNodeInfo);
//        }
//    }
//
//    public void putJobNodeInfo(final String nodeName,
//                               final String jobName,
//                               final JobNodeInfo jobNodeInfo) {
//        latestNodeInfo.computeIfAbsent(nodeName, k -> new HashMap<>())
//                .put(jobName, jobNodeInfo);
//    }
//
//    public void removeJobNodeInfo(final String nodeName,
//                                  final String jobName) {
//        GwtNullSafe.consume(latestNodeInfo.get(nodeName), map -> map.remove(jobName));
//    }
//
//    public void removeJobNodeInfo(final JobNode jobNode) {
//        if (jobNode != null) {
//            GwtNullSafe.consume(latestNodeInfo.get(
//                            jobNode.getNodeName()),
//                    map -> map.remove(jobNode.getJobName()));
//        }
//    }
//
//    public void removeJobNodeInfo(final String nodeName) {
//        if (nodeName != null) {
//            latestNodeInfo.remove(nodeName);
//        }
//    }
//
//    public JobNodeInfo getJobNodeInfo(final JobNode jobNode) {
//        if (jobNode == null) {
//            return null;
//        } else {
//            return GwtNullSafe.get(
//                    latestNodeInfo.get(jobNode.getNodeName()),
//                    map -> map.get(jobNode.getJobName()));
//        }
//    }

    public BrowserEventHandler<JobNodeAndInfo> createExecuteJobNowHandler(final HasHandlers handlers,
                                                                          final TaskListener taskListener) {
        return (Context context, Element elem, JobNodeAndInfo jobNodeAndInfo, NativeEvent event) ->
                ConfirmEvent.fire(handlers,
                        "Are you sure you want to execute job '" + jobNodeAndInfo.getJobName()
                                + "' on node '" + jobNodeAndInfo.getNodeName() + "' now. " +
                                "\n\nThe job will execute shortly, likely within ten seconds. " +
                                "You can check it has run by refreshing the table until the 'Last Executed' " +
                                "column has been updated.",
                        ok -> {
                            restFactory.create(JOB_NODE_RESOURCE)
                                    .call(resource -> resource.execute(jobNodeAndInfo.getId()))
                                    .taskListener(taskListener)
                                    .exec();
                        });
    }

    public void showSchedule(final JobNodeAndInfo jobNodeAndInfo,
                             final TaskListener taskListener,
                             final Runnable onSuccessHandler) {
        restFactory
                .create(JOB_NODE_RESOURCE)
                .method(resource ->
                        resource.info(jobNodeAndInfo.getJob().getName(), jobNodeAndInfo.getNodeName()))
                .onSuccess(jobNodeInfo ->
                        setSchedule(jobNodeAndInfo, jobNodeInfo, taskListener, onSuccessHandler))
                .onFailure(throwable ->
                        setSchedule(jobNodeAndInfo, null, taskListener, onSuccessHandler))
                .taskListener(taskListener)
                .exec();
    }

    public FieldUpdater<JobNodeAndInfo, TickBoxState> createEnabledStateFieldUpdater(
            final TaskListener taskListener,
            final Runnable onSuccessHandler) {

        return (int rowIndex, JobNodeAndInfo jobNodeAndInfo, TickBoxState value) -> {
            if (jobNodeAndInfo != null) {
                final boolean isEnabled = GwtNullSafe.isTrue(value.toBoolean());
                jobNodeAndInfo.getJobNode().setEnabled(isEnabled);
                restFactory
                        .create(JOB_NODE_RESOURCE)
                        .call(jobNodeResource ->
                                jobNodeResource.setEnabled(jobNodeAndInfo.getId(), isEnabled))
                        .onSuccess(aVoid ->
                                GwtNullSafe.run(onSuccessHandler))
                        .taskListener(taskListener)
                        .exec();
            }
        };
    }

    private void setSchedule(final JobNodeAndInfo jobNodeAndInfo,
                             final JobNodeInfo jobNodeInfo,
                             final TaskListener taskListener,
                             final Runnable onSuccessHandler) {
        final JobNode jobNode = jobNodeAndInfo.getJobNode();
        final Schedule currentSchedule = JobNodeUtil.getSchedule(jobNode);
        JobNodeInfo info = jobNodeInfo;

        if (currentSchedule != null) {
            if (info == null) {
                info = JobNodeInfo.empty();
            }

            final ScheduleReferenceTime referenceTime = new ScheduleReferenceTime(
                    info.getScheduleReferenceTime(),
                    info.getLastExecutedTime());
            schedulePresenter.setSchedule(currentSchedule, referenceTime);

            schedulePresenter.show(schedule -> {
                JobNodeUtil.setSchedule(jobNode, schedule);
                restFactory
                        .create(JOB_NODE_RESOURCE)
                        .call(resource ->
                                resource.setSchedule(jobNodeAndInfo.getId(), schedule))
                        .onSuccess(result ->
                                GwtNullSafe.run(onSuccessHandler))
                        .taskListener(taskListener)
                        .exec();
            });
        }
    }

    public String getCurrentTaskCountAsStr(JobNodeAndInfo jobNodeAndInfo) {
        return GwtNullSafe.getOrElse(
                jobNodeAndInfo,
                JobNodeAndInfo::getJobNodeInfo,
                info -> ModelStringUtil.formatCsv(info.getCurrentTaskCount()),
                "?");
    }

    public String getLastExecutedTimeAsStr(JobNodeAndInfo jobNodeAndInfo) {
        if (GwtNullSafe.test(jobNodeAndInfo, jobNode2 ->
                jobNode2.getJobType() == JobType.CRON
                        || jobNode2.getJobType() == JobType.FREQUENCY)) {
            return GwtNullSafe.getOrElse(
                    jobNodeAndInfo,
                    JobNodeAndInfo::getJobNodeInfo,
                    info -> dateTimeFormatter.formatWithDuration(info.getLastExecutedTime()),
                    "?");
        } else {
            return "N/A";
        }
    }

    public String getNextScheduledTimeAsStr(JobNodeAndInfo jobNodeAndInfo) {
        final JobType jobType = GwtNullSafe.get(jobNodeAndInfo, JobNodeAndInfo::getJobType);
        final boolean isJobNodeEnabled = isJobNodeEnabled(jobNodeAndInfo);

        if (isJobNodeEnabled
                && (jobType == JobType.CRON || jobType == JobType.FREQUENCY)) {
            return GwtNullSafe.getOrElse(
                    jobNodeAndInfo,
                    JobNodeAndInfo::getJobNodeInfo,
                    info -> dateTimeFormatter.formatWithDuration(info.getNextScheduledTime()),
                    "?");
        } else {
            return "N/A";
        }
    }

    public static boolean isJobNodeEnabled(final JobNodeAndInfo jobNodeAndInfo) {
        if (jobNodeAndInfo == null) {
            return false;
        } else {
            // A job node is only enabled if the parent job is also enabled
            return jobNodeAndInfo.isEnabled()
                    && GwtNullSafe.isTrue(jobNodeAndInfo.getJob(), Job::isEnabled);
        }
    }

    public static String buildParentJobEnabledStr(final JobNodeAndInfo jobNodeAndInfo) {
        return GwtNullSafe.get(jobNodeAndInfo,
                JobNodeAndInfo::getJob,
                Job::isEnabled,
                isEnabled -> isEnabled
                        ? "Enabled"
                        : "Disabled");
    }

    public static Preset buildRunIconPreset(final JobNodeAndInfo jobNodeAndInfo) {
        if (jobNodeAndInfo != null
                && jobNodeAndInfo.getJobType() != JobType.DISTRIBUTED
                && jobNodeAndInfo.getJobType() != JobType.UNKNOWN) {
            return SvgPresets.RUN
                    .title("Execute job on node " + jobNodeAndInfo.getNodeName() + " now.");
        }
        return null;
    }

    public static String buildJobTypeStr(JobNodeAndInfo jobNodeAndInfo) {
        //noinspection EnhancedSwitchMigration // not in GWT
        switch (jobNodeAndInfo.getJobType()) {
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

    public static InlineSvgToggleButton buildJobFilterButton(final Runnable refreshFunc) {
        final InlineSvgToggleButton showEnabledToggleBtn = new InlineSvgToggleButton();
        showEnabledToggleBtn.setSvg(SvgImage.FILTER);
        showEnabledToggleBtn.setTitle(FILTER_BTN_TITLE_OFF);
        showEnabledToggleBtn.setOff();

        showEnabledToggleBtn.addClickHandler(event -> {
            if (showEnabledToggleBtn.isOn()) {
                showEnabledToggleBtn.setTitle(FILTER_BTN_TITLE_ON);
            } else {
                showEnabledToggleBtn.setTitle(FILTER_BTN_TITLE_OFF);
            }
            refreshFunc.run();
        });
        return showEnabledToggleBtn;
    }

    public Function<JobNodeAndInfo, CommandLink> buildOpenScheduleCommandLinkFunc(
            final TaskListener taskListener,
            final Runnable onSuccess) {

        return (final JobNodeAndInfo jobNodeAndInfo) -> {
            final String schedule = GwtNullSafe.get(jobNodeAndInfo, JobNodeAndInfo::getSchedule);

            if (schedule != null) {
                return new CommandLink(
                        schedule,
                        "Edit schedule",
                        () -> showSchedule(jobNodeAndInfo, taskListener, onSuccess));
            } else {
                return CommandLink.withoutCommand("N/A");
            }
        };
    }
}
