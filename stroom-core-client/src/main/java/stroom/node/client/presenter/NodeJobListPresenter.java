package stroom.node.client.presenter;

import stroom.cell.info.client.CommandLink;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.job.client.JobTypeCell;
import stroom.job.shared.FindJobNodeCriteria;
import stroom.job.shared.Job;
import stroom.job.shared.JobNode;
import stroom.job.shared.JobNode.JobType;
import stroom.job.shared.JobNodeAndInfo;
import stroom.job.shared.JobNodeAndInfoListResponse;
import stroom.job.shared.JobNodeResource;
import stroom.monitoring.client.JobListPlugin;
import stroom.node.client.JobNodeListHelper;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.GwtNullSafe;
import stroom.widget.button.client.InlineSvgToggleButton;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Bottom pane of NodePresenter (Nodes tab). Lists jobNodes for a single node.
 */
public class NodeJobListPresenter extends MyPresenterWidget<PagerView> {

    private static final JobNodeResource JOB_NODE_RESOURCE = GWT.create(JobNodeResource.class);

    private final JobNodeListHelper jobNodeListHelper;
    private final RestDataProvider<JobNodeAndInfo, JobNodeAndInfoListResponse> dataProvider;
    private final MyDataGrid<JobNodeAndInfo> dataGrid;
    private final FindJobNodeCriteria findJobNodeCriteria = new FindJobNodeCriteria();
    private final InlineSvgToggleButton showEnabledToggleBtn;
    private final Provider<JobListPlugin> jobListPluginProvider;
    private final MultiSelectionModelImpl<JobNodeAndInfo> selectionModel;
    private Consumer<Boolean> filterStateConsumer = null;

    @Inject
    public NodeJobListPresenter(final EventBus eventBus,
                                final PagerView view,
                                final RestFactory restFactory,
                                final JobNodeListHelper jobNodeListHelper,
                                final Provider<JobListPlugin> jobListPluginProvider) {
        super(eventBus, view);
        this.jobNodeListHelper = jobNodeListHelper;
        this.jobListPluginProvider = jobListPluginProvider;

        dataGrid = new MyDataGrid<>();
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        view.setDataWidget(dataGrid);

        showEnabledToggleBtn = JobNodeListHelper.buildJobFilterButton(this::refresh);
        view.addButton(showEnabledToggleBtn);

        initTable();

        dataProvider = buildDataProvider(eventBus, view, restFactory);
    }

    private RestDataProvider<JobNodeAndInfo, JobNodeAndInfoListResponse> buildDataProvider(
            final EventBus eventBus,
            final PagerView view,
            final RestFactory restFactory) {

        //noinspection Convert2Diamond
        return new RestDataProvider<JobNodeAndInfo, JobNodeAndInfoListResponse>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<JobNodeAndInfoListResponse> dataConsumer,
                                final RestErrorHandler errorHandler) {
                // We want the
                findJobNodeCriteria.setSort(FindJobNodeCriteria.FIELD_ADVANCED);
                findJobNodeCriteria.addSort(FindJobNodeCriteria.FIELD_JOB_NAME);
                // ON is show enabled only, OFF is show all states
                findJobNodeCriteria.setJobNodeEnabled(showEnabledToggleBtn.isOn()
                        ? true
                        : null);
                restFactory
                        .create(JOB_NODE_RESOURCE)
                        .method(resource ->
                                resource.find(findJobNodeCriteria))
                        .onSuccess(dataConsumer)
                        .onFailure(errorHandler)
                        .taskListener(view)
                        .exec();
            }

            @Override
            protected void changeData(final JobNodeAndInfoListResponse data) {
                final List<JobNodeAndInfo> rtnList = new ArrayList<>();
                boolean addedGap = false;

                for (final JobNodeAndInfo jobNodeAndInfo : data.getValues()) {
                    rtnList.add(jobNodeAndInfo);
                    if (!addedGap && GwtNullSafe.test(jobNodeAndInfo, JobNodeAndInfo::getJob, Job::isAdvanced)) {
                        // Add a gap between the non-advanced and advanced jobs
                        rtnList.add(null);
                        addedGap = true;
                    }
                }

                final JobNodeAndInfoListResponse modifiedData =
                        JobNodeAndInfoListResponse.createUnboundedResponse(rtnList);
                super.changeData(modifiedData);
            }
        };
    }

    public void read(final String nodeName) {
        if (dataProvider.getDataDisplays().isEmpty()) {
            dataProvider.addDataDisplay(dataGrid);
        }
        setNodeNameCriteria(nodeName);
        refresh();
    }

    private String getNodeNameCriteria() {
        return findJobNodeCriteria.getNodeName().getString();
    }

    private void setNodeNameCriteria(final String nodeName) {
        findJobNodeCriteria.getNodeName().setString(nodeName);
    }

    void refresh() {
        updateFormGroupHeading();
        dataProvider.refresh();
    }

    /**
     * Add the columns to the table.
     */
    private void initTable() {
//        DataGridUtil.addColumnSortHandler(dataGrid, findJobNodeCriteria, this::refresh);

        // JobNode Enabled.
        dataGrid.addColumn(
                DataGridUtil.updatableTickBoxColumnBuilder(JobNodeAndInfo::isEnabled)
                        .enabledWhen(JobNodeListHelper::isJobNodeEnabled)
//                        .withSorting(FindJobNodeCriteria.FIELD_ID_ENABLED)
                        .withFieldUpdater(jobNodeListHelper.createEnabledStateFieldUpdater(
                                getView(), this::refresh))
                        .build(),
                DataGridUtil.headingBuilder("Enabled")
                        .withToolTip("Whether this job is enabled on this node or not. " +
                                "The parent job must also be enabled for the job to execute.")
                        .build(),
                60);

        // Job Name
        final Column<JobNodeAndInfo, CommandLink> jobNameColumn = DataGridUtil.commandLinkColumnBuilder(
                        this::openJobNodeAsCommandLink)
                .enabledWhen(JobNodeListHelper::isJobNodeEnabled)
//                .withSorting(FindJobNodeCriteria.FIELD_ID_NODE)
                .build();

        DataGridUtil.addCommandLinkFieldUpdater(jobNameColumn);
        dataGrid.addResizableColumn(
                jobNameColumn,
                DataGridUtil.headingBuilder("Job Name")
                        .withToolTip("The name of the job.")
                        .build(),
                350);

        // Job State.
        dataGrid.addColumn(
                DataGridUtil.textColumnBuilder(JobNodeListHelper::buildParentJobEnabledStr)
                        .enabledWhen(JobNodeListHelper::isJobNodeEnabled)
//                        .withSorting(FindJobNodeCriteria.FIELD_ID_ENABLED)
                        .build(),
                DataGridUtil.headingBuilder("Job State")
                        .withToolTip("Whether this job is enabled across all nodes or not. " +
                                "An enabled job must also be enabled on a node for it to execute on that node.")
                        .build(),
                80);

        // Type
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(JobNodeListHelper::buildJobTypeStr)
                        .enabledWhen(JobNodeListHelper::isJobNodeEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Type")
                        .withToolTip("The type of the job")
                        .build(),
                80);

        // Schedule.
        final Column<JobNodeAndInfo, CommandLink> scheduleColumn = DataGridUtil.commandLinkColumnBuilder(
                        jobNodeListHelper.buildOpenScheduleCommandLinkFunc(
                                selectionModel,
                                getView(),
                                NodeJobListPresenter.this,
                                this::refresh))
                .enabledWhen(JobNodeListHelper::isJobNodeEnabled)
                .build();
        DataGridUtil.addCommandLinkFieldUpdater(scheduleColumn);
        dataGrid.addResizableColumn(
                scheduleColumn,
                DataGridUtil.headingBuilder("Schedule")
                        .withToolTip("The schedule for this job on this node, if applicable to the job type")
                        .build(),
                250);

        // Job Type Icon, always enabled, so you can edit schedule for disabled jobs
        dataGrid.addColumn(
                DataGridUtil.columnBuilder((JobNodeAndInfo jobNodeAndInfo) ->
                                        GwtNullSafe.requireNonNullElse(jobNodeAndInfo.getJobType(), JobType.UNKNOWN),
                                JobTypeCell::new)
                        .withBrowserEventHandler((context, elem, jobNode, event) -> {
                            if (jobNode != null && MouseUtil.isPrimary(event)) {
                                jobNodeListHelper.showSchedule(
                                        jobNode,
                                        selectionModel,
                                        getView(),
                                        NodeJobListPresenter.this,
                                        this::refresh);
                            }
                        })
                        .build(),
                DataGridUtil.headingBuilder("")
                        .build(),
                ColumnSizeConstants.ICON_COL);

        // Run now icon, always enabled, so you can run disabled jobs
        dataGrid.addColumn(
                DataGridUtil.svgPresetColumnBuilder(true, JobNodeListHelper::buildRunIconPreset)
                        .withBrowserEventHandler(jobNodeListHelper.createExecuteJobNowHandler(
                                NodeJobListPresenter.this,
                                getView()))
                        .build(),
                DataGridUtil.headingBuilder("Run")
                        .withToolTip("Execute the job on a node now.")
                        .build(), 40);
//
//        // Max.
//        dataGrid.addColumn(
//                DataGridUtil.valueSpinnerColumnBuilder(this::getTaskLimit, 1L, 9999L)
//                        .enabledWhen(JobListUtil::isJobNodeEnabled)
//                        .withFieldUpdater((rowIndex, jobNode, value) -> {
//                            jobNode.setTaskLimit(value.intValue());
//                            restFactory
//                                    .create(JOB_NODE_RESOURCE)
//                                    .call(res -> res.setTaskLimit(jobNode.getId(), value.intValue()))
//                                    .taskListener(getView())
//                                    .exec();
//                        })
//                        .build(),
//                DataGridUtil.headingBuilder("Max Tasks")
//                        .withToolTip("The task limit for this job on this node")
//                        .build(),
//                80);
//
//        // Current Tasks (Cur).
//        dataGrid.addColumn(
//                DataGridUtil.textColumnBuilder(this::getCurrentTaskCountAsStr)
//                        .enabledWhen(JobListUtil::isJobNodeEnabled)
//                        .rightAligned()
//                        .build(),
//                DataGridUtil.headingBuilder("Current Tasks")
//                        .withToolTip("The number of the currently executing tasks on this node for this job")
//                        .build(),
//                100);
//
        // Last executed.
        dataGrid.addColumn(
                DataGridUtil.textColumnBuilder(jobNodeListHelper::getLastExecutedTimeAsStr)
                        .enabledWhen(JobNodeListHelper::isJobNodeEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Last Executed")
                        .withToolTip("The date/time that this job was last executed on this node, " +
                                "if applicable to the job type.")
                        .build(),
                ColumnSizeConstants.DATE_AND_DURATION_COL);

        // Next executed.
        dataGrid.addColumn(
                DataGridUtil.textColumnBuilder(jobNodeListHelper::getNextScheduledTimeAsStr)
                        .enabledWhen(JobNodeListHelper::isJobNodeEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Next Scheduled")
                        .withToolTip("The date/time that this job is next scheduled to execute on this node, " +
                                "if applicable to the job type.")
                        .build(),
                ColumnSizeConstants.DATE_AND_DURATION_COL);

        DataGridUtil.addEndColumn(dataGrid);
    }

    private CommandLink openJobNodeAsCommandLink(JobNodeAndInfo jobNodeAndInfo) {
        if (jobNodeAndInfo != null) {
            final String jobName = jobNodeAndInfo.getJobName();
            final String nodeName = jobNodeAndInfo.getNodeName();
            return new CommandLink(
                    jobName,
                    "Open job '" + jobName + "' on node '" + nodeName + "' on the Jobs screen.",
                    () -> jobListPluginProvider.get().open(jobNodeAndInfo.getJobNode()));
        } else {
            return null;
        }
    }

    public void setSelected(final JobNode jobNode) {
        if (jobNode != null) {
            selectionModel.setSelected(JobNodeAndInfo.withoutInfo(jobNode));
        } else {
            selectionModel.clear();
        }
    }

    private void updateFormGroupHeading() {
        final String nodeName = getNodeNameCriteria();
        final boolean isShowEnabled = showEnabledToggleBtn.getState();
        final String prefix = isShowEnabled
                ? "Enabled"
                : "All";
        final String heading = nodeName != null
                ? prefix + " jobs on node '" + nodeName + "'"
                : null;
        getView().setHeading(heading);
    }
}
