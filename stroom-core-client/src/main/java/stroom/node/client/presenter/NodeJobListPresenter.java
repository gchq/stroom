package stroom.node.client.presenter;

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
import stroom.job.shared.JobNodeResource;
import stroom.node.client.JobNodeListHelper;
import stroom.svg.shared.SvgImage;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.ResultPage;
import stroom.widget.button.client.InlineSvgToggleButton;
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.function.Consumer;

public class NodeJobListPresenter extends MyPresenterWidget<PagerView> {

    private static final JobNodeResource JOB_NODE_RESOURCE = GWT.create(JobNodeResource.class);
    private static final String FILTER_BTN_TITLE_ON = "Click to show all jobs";
    private static final String FILTER_BTN_TITLE_OFF = "Click to show only enabled jobs";

    private final RestFactory restFactory;
    private final JobNodeListHelper jobNodeListHelper;
    private final RestDataProvider<JobNode, ResultPage<JobNode>> dataProvider;
    private final MyDataGrid<JobNode> dataGrid;
    private final FindJobNodeCriteria findJobNodeCriteria = new FindJobNodeCriteria();
    private final InlineSvgToggleButton showEnabledToggleBtn;
    private String nodeName;

    @Inject
    public NodeJobListPresenter(final EventBus eventBus,
                                final PagerView view,
                                final RestFactory restFactory,
                                final JobNodeListHelper jobNodeListHelper) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.jobNodeListHelper = jobNodeListHelper;

        dataGrid = new MyDataGrid<>();
        dataGrid.addDefaultSelectionModel(true);
        view.setDataWidget(dataGrid);
        showEnabledToggleBtn = new InlineSvgToggleButton();
        showEnabledToggleBtn.setSvg(SvgImage.FILTER);
        showEnabledToggleBtn.setTitle(FILTER_BTN_TITLE_OFF);
        showEnabledToggleBtn.setOff();
        view.addButton(showEnabledToggleBtn);

        showEnabledToggleBtn.addClickHandler(event -> {
            if (showEnabledToggleBtn.isOn()) {
                showEnabledToggleBtn.setTitle(FILTER_BTN_TITLE_ON);
            } else {
                showEnabledToggleBtn.setTitle(FILTER_BTN_TITLE_OFF);
            }
            refresh();
        });

        initTable();

        dataProvider = new RestDataProvider<JobNode, ResultPage<JobNode>>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<JobNode>> dataConsumer,
                                final RestErrorHandler errorHandler) {
                findJobNodeCriteria.getNodeName().setString(nodeName);
                // ON is show enabled only, OFF is show all states
                findJobNodeCriteria.setJobNodeEnabled(showEnabledToggleBtn.isOn()
                        ? true
                        : null);
                restFactory
                        .create(JOB_NODE_RESOURCE)
                        .method(res -> res.find(findJobNodeCriteria))
                        .onSuccess(dataConsumer)
                        .onFailure(errorHandler)
                        .taskListener(view)
                        .exec();
            }

            @Override
            protected void changeData(final ResultPage<JobNode> data) {
                // Ping each node.
                data.getValues().forEach(jobNode -> {
                    restFactory
                            .create(JOB_NODE_RESOURCE)
                            .method(res -> res.info(jobNode.getJob().getName(), jobNode.getNodeName()))
                            .onSuccess(info -> {
                                jobNodeListHelper.putJobNodeInfo(jobNode, info);
                                super.changeData(data);
                                dataGrid.redraw();
                            })
                            .onFailure(throwable -> {
                                jobNodeListHelper.removeJobNodeInfo(jobNode);
                                super.changeData(data);
                            })
                            .taskListener(getView())
                            .exec();
                });
                super.changeData(data);
            }
        };
    }

    public void read(final String nodeName) {
        if (this.nodeName == null) {
            this.nodeName = nodeName;
            dataProvider.addDataDisplay(dataGrid);
        } else {
            this.nodeName = nodeName;
            dataProvider.refresh();
        }
    }

    void refresh() {
        dataProvider.refresh();
    }

    /**
     * Add the columns to the table.
     */
    private void initTable() {
        DataGridUtil.addColumnSortHandler(dataGrid, findJobNodeCriteria, this::refresh);

        // Job Enabled.
        dataGrid.addColumn(
                DataGridUtil.readOnlyTickBoxColumnBuilder((JobNode jobNode) -> GwtNullSafe.test(
                                jobNode,
                                JobNode::getJob,
                                Job::isEnabled))
                        .enabledWhen(jobNode -> false)
                        .withSorting(FindJobNodeCriteria.FIELD_ID_ENABLED)
                        .build(),
                DataGridUtil.headingBuilder("Job")
                        .withToolTip("Whether this job is enabled across all nodes or not. " +
                                "An enabled job must also be enabled on a node for it to execute on that node.")
                        .build(),
                40);

        // JobNode Enabled.
        dataGrid.addColumn(
                DataGridUtil.updatableTickBoxColumnBuilder(JobNode::isEnabled)
                        .enabledWhen(JobNodeListHelper::isJobNodeEnabled)
                        .withSorting(FindJobNodeCriteria.FIELD_ID_ENABLED)
                        .withFieldUpdater(jobNodeListHelper.createEnabledStateFieldUpdater(
                                getView(), this::refresh))
                        .build(),
                DataGridUtil.headingBuilder("This Node")
                        .withToolTip("Whether this job is enabled on this node or not. " +
                                "The parent job must also be enabled for the job to execute.")
                        .build(),
                80);

        // Job Name
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(JobNode::getJobName)
                        .enabledWhen(JobNodeListHelper::isJobNodeEnabled)
                        .withSorting(FindJobNodeCriteria.FIELD_ID_NODE)
                        .build(),
                DataGridUtil.headingBuilder("Job Name")
                        .withToolTip("The name of the job.")
                        .build(),
                350);

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
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder((JobNode jobNode1) ->
                                GwtNullSafe.requireNonNullElse(
                                        jobNode1.getSchedule(),
                                        "N/A"))
                        .enabledWhen(JobNodeListHelper::isJobNodeEnabled)
//                        .withBrowserEventHandler((context, elem, jobNode, event) -> {
//                            if (jobNode != null && MouseUtil.isPrimary(event)) {
//                                showSchedule(jobNode);
//                            }
//                        })
                        .build(),
                DataGridUtil.headingBuilder("Schedule")
                        .withToolTip("The schedule for this job on this node, if applicable to the job type")
                        .build(),
                250);

        // Job Type Icon, always enabled, so you can edit schedule for disabled jobs
        dataGrid.addColumn(
                DataGridUtil.columnBuilder((JobNode jobNode) ->
                                        GwtNullSafe.requireNonNullElse(jobNode.getJobType(), JobType.UNKNOWN),
                                JobTypeCell::new)
                        .withBrowserEventHandler((context, elem, jobNode, event) -> {
                            if (jobNode != null && MouseUtil.isPrimary(event)) {
                                jobNodeListHelper.showSchedule(jobNode, getView(), dataProvider::refresh);
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
}
