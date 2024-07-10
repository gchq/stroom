package stroom.node.client.presenter;

import stroom.cell.info.client.CommandLink;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.job.client.event.JobChangeEvent;
import stroom.job.client.event.JobNodeChangeEvent;
import stroom.job.shared.FindJobNodeCriteria;
import stroom.job.shared.Job;
import stroom.job.shared.JobNode;
import stroom.job.shared.JobNodeAndInfo;
import stroom.job.shared.JobNodeAndInfoListResponse;
import stroom.job.shared.JobNodeResource;
import stroom.monitoring.client.JobListPlugin;
import stroom.node.client.JobNodeListHelper;
import stroom.preferences.client.DateTimeFormatter;
import stroom.schedule.client.SchedulePopup;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.GwtNullSafe;
import stroom.widget.button.client.InlineSvgToggleButton;
import stroom.widget.menu.client.presenter.MenuPresenter;
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
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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

    @Inject
    public NodeJobListPresenter(final EventBus eventBus,
                                final PagerView view,
                                final RestFactory restFactory,
                                final Provider<JobListPlugin> jobListPluginProvider,
                                final SchedulePopup schedulePresenter,
                                final MenuPresenter menuPresenter,
                                final DateTimeFormatter dateTimeFormatter) {
        super(eventBus, view);
        this.jobListPluginProvider = jobListPluginProvider;
        this.dataGrid = new MyDataGrid<>();
        this.selectionModel = dataGrid.addDefaultSelectionModel(false);
        view.setDataWidget(dataGrid);
        this.dataProvider = buildDataProvider(eventBus, view, restFactory);

        this.jobNodeListHelper = new JobNodeListHelper(
                dateTimeFormatter,
                restFactory,
                schedulePresenter,
                menuPresenter,
                selectionModel,
                getView(),
                NodeJobListPresenter.this,
                this::refresh);

        this.showEnabledToggleBtn = jobNodeListHelper.buildJobFilterButton();
        view.addButton(showEnabledToggleBtn);

        // Must call this after initialising JobNodeListHelper
        initTable();
    }

    @Override
    protected void onBind() {
        super.onBind();

        // NodeLisPresenter may change a node
        // Currently the node state does not affect how the job nodes are displayed
//        registerHandler(getEventBus().addHandler(
//                NodeChangeEvent.getType(), event -> {
//                    final String currentNodeName = getNodeNameCriteria();
//                    final String affectedNodeName = event.getNodeName();
//                    if (currentNodeName != null && Objects.equals(currentNodeName, affectedNodeName)) {
//                        refresh();
//                    }
//                }));

        // JobListPresenter may change a job
        registerHandler(getEventBus().addHandler(
                JobChangeEvent.getType(), event -> {
                    // We are likely showing all jobs so just refresh
                    refresh();
                }));

        // JobNodeListPresenter may change one or more jobNodes
        registerHandler(getEventBus().addHandler(
                JobNodeChangeEvent.getType(), event -> {
//                    GWT.log(NodeJobListPresenter.this.getClass().getSimpleName()
//                            + " - jobNodes changed: " + event.getJobNodes().size());
                    if (!Objects.equals(event.getSource().getClass(), NodeJobListPresenter.this.getClass())) {
                        final Set<String> affectedNodes = event.getJobNodes()
                                .stream()
                                .map(JobNode::getNodeName)
                                .collect(Collectors.toSet());
                        final String currentNodeName = getNodeNameCriteria();
                        if (currentNodeName != null && affectedNodes.contains(currentNodeName)) {
                            refresh();
                        }
                    }
                }));
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
                                NodeJobListPresenter.this, getView(), this::refresh))
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
        jobNodeListHelper.addTypeColumn(dataGrid);

        // Schedule.
        jobNodeListHelper.addScheduleColumn(dataGrid);
//
        // Last executed.
        jobNodeListHelper.addLastExecutedColumn(dataGrid);

        // Next executed.
        jobNodeListHelper.addNextExecutedColumn(dataGrid);

        // Action column
        jobNodeListHelper.addActionColumn(dataGrid);

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
