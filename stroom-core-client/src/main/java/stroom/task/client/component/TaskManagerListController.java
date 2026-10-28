/*
 * Copyright 2016 Crown Copyright
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

package stroom.task.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.data.client.CriteriaUtil;
import stroom.data.client.DataViewType;
import stroom.data.client.DisplayMode;
import stroom.data.client.RestDataProvider;
import stroom.data.client.ShowDataEvent;
import stroom.data.client.component.RefCellsController;
import stroom.data.client.component.UserRefCell;
import stroom.data.table.client.Refreshable;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.document.client.event.OpenDocumentEvent;
import stroom.feed.client.FeedClient;
import stroom.node.client.NodeClient;
import stroom.node.shared.FindNodeStatusCriteria;
import stroom.node.shared.Node;
import stroom.node.shared.NodeStatusResult;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.SourceLocation;
import stroom.preferences.client.DateTimeFormatter;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.task.client.event.OpenProcessorTaskEvent;
import stroom.svg.shared.SvgImage;
import stroom.task.client.TaskInfoParser;
import stroom.task.client.TaskInfoParser.TaskInfoKey;
import stroom.task.client.TaskManagerTreeAction;
import stroom.task.client.TaskProgressUtil;
import stroom.task.shared.FindTaskCriteria;
import stroom.task.shared.FindTaskProgressCriteria;
import stroom.task.shared.FindTaskProgressRequest;
import stroom.task.shared.TaskId;
import stroom.task.shared.TaskProgress;
import stroom.task.shared.TaskProgressResponse;
import stroom.task.shared.TaskResource;
import stroom.task.shared.TerminateTaskProgressRequest;
import stroom.util.client.DelayedUpdate;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;
import stroom.widget.component.client.Controller;
import stroom.widget.menu.client.IconMenuItem;
import stroom.widget.menu.client.Item;
import stroom.widget.tooltip.client.component.InfoTooltipController;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.TableBuilder;
import stroom.widget.util.client.TableCell;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.Timer;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/// The tasks running on every node ([TaskManagerList]): each node asked for its tasks, which are
/// combined into one tree once the replies are in; the tasks ticked terminated. Refreshes only
/// while auto refresh is on; filtered by name, after a pause for typing.
public class TaskManagerListController extends Controller implements Refreshable {

    private static final TaskResource TASK_RESOURCE = GWT.create(TaskResource.class);

    private final DateTimeFormatter dateTimeFormatter;
    private final FindTaskProgressCriteria criteria = new FindTaskProgressCriteria();
    private final FindTaskProgressRequest request = new FindTaskProgressRequest(criteria);
    private final InfoTooltipController infoTooltip;
    private final RestFactory restFactory;
    private final NodeClient nodeClient;
    private final FeedClient feedClient;
    private final NameFilterTimer timer = new NameFilterTimer();
    private final Map<String, List<TaskProgress>> responseMap = new HashMap<>();
    private final Map<String, List<String>> errorMap = new HashMap<>();
    private final Set<String> enabledNodeNames = new HashSet<>();
    private final RestDataProvider<TaskProgress, TaskProgressResponse> dataProvider;
    private final TaskManagerList view;

    private String currentWarnings;

    private final TaskManagerTreeAction treeAction = new TaskManagerTreeAction();
    private final DelayedUpdate delayedUpdate;

    private Range range;
    private Consumer<TaskProgressResponse> dataConsumer;
    private Consumer<String> filterErrorConsumer;
    private boolean autoRefresh = true;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus          The application's event bus.
    /// @param view              The content.
    /// @param infoTooltip       Shows a task's info.
    /// @param restFactory       Fetches and terminates the tasks.
    /// @param nodeClient        Lists the nodes.
    /// @param feedClient        Finds a feed by name, to open it.
    /// @param dateTimeFormatter Formats times in the user's preferences.
    /// @param refCells          Lets the user cells open their users.
    @Inject
    public TaskManagerListController(final EventBus eventBus,
                                     final TaskManagerList view,
                                     final InfoTooltipController infoTooltip,
                                     final RestFactory restFactory,
                                     final NodeClient nodeClient,
                                     final FeedClient feedClient,
                                     final DateTimeFormatter dateTimeFormatter,
                                     final RefCellsController refCells) {
        super(eventBus);
        this.view = view;
        own(refCells);
        own(infoTooltip);
        this.infoTooltip = infoTooltip;
        this.restFactory = restFactory;
        this.nodeClient = nodeClient;
        this.feedClient = feedClient;
        this.dateTimeFormatter = dateTimeFormatter;
        initWidget(view);

        view.setOnAutoRefreshToggle(pressed -> onAutoRefreshToggled());
        view.setOnTerminate(this::endSelectedTask);
        view.setOnExpandAll(this::onExpandAll);
        view.setOnCollapseAll(this::onCollapseAll);
        view.setOnShowWarnings(this::showWarnings);
        view.setOnShowInfo((row, popupPosition, anchor) ->
                infoTooltip.show(buildTooltipHtml(row), popupPosition, anchor));
        view.setOnExpand((row, expanded) -> {
            treeAction.setRowExpanded(row, expanded);
            // As we get data async from all nodes we can't be sure when we have finished so
            // need to clear the expandAllRequestState prior to fetching
            treeAction.resetExpandAllRequestState();
            updateButtonStates();
            internalRefresh();
        });
        view.addColumns(this::getNodeName,
                dateTimeFormatter::format,
                refCells.configure(new UserRefCell.Builder<TaskProgress>()),
                this::getContextMenus);

        updateButtonStates();

        delayedUpdate = new DelayedUpdate(this::update);
        dataProvider = new RestDataProvider<TaskProgress, TaskProgressResponse>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<TaskProgressResponse> dataConsumer,
                                final RestErrorHandler errorHandler) {
                CriteriaUtil.setSortList(criteria, view.getSortList());
                TaskManagerListController.this.range = range;
                TaskManagerListController.this.dataConsumer = dataConsumer;
                delayedUpdate.reset();
                fetchNodes(range, dataConsumer, errorHandler);
            }
        };
        view.setDataProvider(dataProvider);

        // Handle use of the expander column.
        dataProvider.setTreeRowHandler(view.createTreeRowHandler(treeAction));

        view.setOnSortChange(() -> {
            // As we get data async from all nodes we can't be sure when we have finished so
            // need to clear the expandAllRequestState prior to fetching
            treeAction.resetExpandAllRequestState();
            dataProvider.refresh();
        });
        // Timers stop with the list
        onDispose(delayedUpdate::reset);
        onDispose(timer::cancel);
    }

    private void onAutoRefreshToggled() {
        autoRefresh = !autoRefresh;
        if (autoRefresh) {
            internalRefresh();
        }
    }

    private void onExpandAll() {
        treeAction.expandAll();
        dataProvider.refresh();
        updateButtonStates();
    }

    private void onCollapseAll() {
        treeAction.collapseAll();
        dataProvider.refresh();
        updateButtonStates();
    }

    private void showWarnings() {
        if (currentWarnings != null && !currentWarnings.isEmpty()) {
            AlertEvent.fireWarn(this, "The following warnings have been created while fetching tasks:",
                    currentWarnings, null);
        }
    }

    private void setErrors(final String errors) {
        currentWarnings = errors;
        view.setWarningsVisible(currentWarnings != null && !currentWarnings.isEmpty());
    }

    private void updateButtonStates() {
        view.setExpandCollapseEnabled(treeAction.hasCollapsedRows(), treeAction.hasExpandedRows());
    }

    private String getNodeName(final TaskProgress taskProgress) {
        final String nodeName = taskProgress.getNodeName();
        if (nodeName == null) {
            return "?";
        } else {
            return enabledNodeNames.contains(nodeName)
                    ? nodeName
                    : nodeName + " (Disabled)";
        }
    }

    private SafeHtml buildTooltipHtml(final TaskProgress row) {
        final TableBuilder tableBuilder = new TableBuilder()
                .row(TableCell.header("Task", 2))
                .row("Name", row.getTaskName())
                .row("Node", getNodeName(row))
                .row("User", NullSafe.get(row, TaskProgress::getUserRef, UserRef::getDisplayName))
                .row("Submit Time", dateTimeFormatter.format(row.getSubmitTimeMs()))
                .row("Age", ModelStringUtil.formatDurationString(row.getAgeMs()))
                .row("Id", NullSafe.get(row.getId(), TaskId::getId));

        NullSafe.consume(row.getId(), TaskId::getParentId, TaskId::getId, parentId ->
                tableBuilder.row("Parent Id", parentId));

        tableBuilder.row("Thread Name", row.getThreadName());

        NullSafe.consume(row.getTaskInfo(), info ->
                tableBuilder.row(
                        TableCell.builder()
                                .value("Info")
                                .build(),
                        TableCell.builder()
                                .addClass(TableCell.WRAP_CLASS)
                                .value(info)
                                .build()));

        final HtmlBuilder htmlBuilder = new HtmlBuilder();
        htmlBuilder.div(tableBuilder::write, Attribute.className("taskManager infoTable"));
        return htmlBuilder.toSafeHtml();
    }

    // The menu of a task's info, from its text
    private List<Item> getContextMenus(final String taskInfo) {
        final List<Item> menuItems = new ArrayList<>();
        int priority = 1;
        final TaskInfoParser parser = new TaskInfoParser(taskInfo);

        final String feedName = parser.getString(TaskInfoKey.FEED_NAME);
        if (feedName != null) {
            // The task info only holds the feed's name, so look up its doc ref (a DocRef must
            // have a UUID) when the item is chosen
            menuItems.add(new IconMenuItem.Builder()
                    .priority(priority++)
                    .icon(SvgImage.OPEN)
                    .text("Open Feed")
                    .enabled(true)
                    .command(() -> openFeed(feedName))
                    .build());
        }

        final Integer filterId = parser.getInt(TaskInfoKey.FILTER_ID);
        if (filterId != null) {
            final ProcessorFilter processorFilter = ProcessorFilter.builder().id(filterId).build();
            menuItems.add(new IconMenuItem.Builder()
                    .priority(priority++)
                    .icon(SvgImage.OPEN)
                    .text("Show Processor Filter Tasks")
                    .enabled(true)
                    .command(() -> OpenProcessorTaskEvent.fire(this, processorFilter))
                    .build());
        }

        final String pipelineUuid = parser.getString(TaskInfoKey.PIPELINE_UUID);
        if (pipelineUuid != null) {
            final DocRef docRef = new DocRef(PipelineDoc.TYPE, pipelineUuid, null);
            menuItems.add(new IconMenuItem.Builder()
                    .priority(priority++)
                    .icon(SvgImage.OPEN)
                    .text("Open Pipeline")
                    .enabled(true)
                    .command(() -> OpenDocumentEvent.fire(this, docRef, true))
                    .build());
        }

        final Long streamId = parser.getLong(TaskInfoKey.STREAM_ID);
        if (streamId != null) {
            final SourceLocation location = SourceLocation.builder(streamId).build();
            menuItems.add(new IconMenuItem.Builder()
                    .priority(priority++)
                    .icon(SvgImage.OPEN)
                    .text("Open Stream")
                    .enabled(true)
                    .command(() -> ShowDataEvent.fire(this,
                            location,
                            DataViewType.INFO,
                            DisplayMode.STROOM_TAB))
                    .build());
        }

        if (!menuItems.isEmpty()) {
            menuItems.add(new stroom.widget.menu.client.Separator(1));
        }
        return menuItems;
    }

    @Override
    public void refresh() {
        if (autoRefresh) {
            internalRefresh();
        }
    }

    private void internalRefresh() {
        treeAction.resetExpandAllRequestState();
        dataProvider.refresh();
    }

    private void fetchNodes(final Range range,
                           final Consumer<TaskProgressResponse> dataConsumer,
                           final RestErrorHandler errorConsumer) {
        nodeClient.fetchNodeStatus(
                fetchNodeStatusResponse -> {
                    final List<String> allNodeNames = fetchNodeStatusResponse.stream()
                            .map(NodeStatusResult::getNode)
                            .map(Node::getName)
                            .collect(Collectors.toList());
                    final Set<String> enabledNodeNames = fetchNodeStatusResponse.stream()
                            .map(NodeStatusResult::getNode)
                            .filter(Node::isEnabled)
                            .map(Node::getName)
                            .collect(Collectors.toSet());
                    // Update our instance view of which nodes are enabled
                    this.enabledNodeNames.removeIf(nodeName ->
                            !enabledNodeNames.contains(nodeName));
                    this.enabledNodeNames.addAll(enabledNodeNames);

                    fetchTasksForNodes(range, dataConsumer, allNodeNames, enabledNodeNames);
                },
                errorConsumer,
                new FindNodeStatusCriteria(),
                view.getTaskMonitor());
    }

    private void fetchTasksForNodes(final Range range,
                                    final Consumer<TaskProgressResponse> dataConsumer,
                                    final List<String> allNodeNames,
                                    final Set<String> enabledNodeNames) {
        responseMap.clear();
        errorMap.clear();
        if (filterErrorConsumer != null) {
            // Cleared up front; any node that rejects the filter sets it again below.
            filterErrorConsumer.accept(null);
        }
        for (final String nodeName : allNodeNames) {
            restFactory
                    .create(TASK_RESOURCE)
                    .method(res -> res.find(nodeName, request))
                    .onSuccess(response -> {
                        // The filter is applied per node, so any node can report it was rejected.
                        // They all get the same filter, so the first reason is representative.
                        if (filterErrorConsumer != null && response.getFilterError() != null) {
                            filterErrorConsumer.accept(response.getFilterError().getText());
                        }
                        responseMap.put(nodeName, response.getValues());
                        // No point in seeing errors for disabled nodes as they are likely down
                        // but do show results for disabled nodes in case they are still doing
                        // work for some reason
                        if (enabledNodeNames.contains(nodeName)) {
                            errorMap.put(nodeName, response.getErrors());
                        }
                        delayedUpdate.update();
                    })
                    .onFailure(throwable -> {
                        responseMap.remove(nodeName);
                        if (enabledNodeNames.contains(nodeName)) {
                            errorMap.put(nodeName, Collections.singletonList(throwable.getMessage()));
                        }
                        delayedUpdate.update();
                    })
                    .owner(this)
                    .taskMonitorFactory(view.getTaskMonitor())
                    .exec();
        }
    }

    private void update() {
        if (isDisposed()) {
            return;
        }
        // Combine data from all nodes.
        final ResultPage<TaskProgress> resultPage = TaskProgressUtil.combine(
                range,
                criteria,
                responseMap.values(),
                treeAction);

        view.retainSelectedTasks(new HashSet<>(resultPage.getValues()));

        final String allErrors = errorMap.entrySet()
                .stream()
                .flatMap(r -> r.getValue().stream().map(message -> r.getKey() + ": " + message))
                .collect(Collectors.joining("\n"));
        setErrors(allErrors);

        final TaskProgressResponse response = new TaskProgressResponse(
                resultPage.getValues(),
                null,
                resultPage.getPageResponse());

        dataConsumer.accept(response);
        updateButtonStates();
    }

    /// Filters the tasks by name, after a pause for typing.
    ///
    /// @param name Part of a task's name, info and so on.
    public void setNameFilter(final String name) {
        timer.setName(name);
        timer.cancel();
        timer.schedule(300);
    }

    /// @param filterErrorConsumer Given why a node rejected the filter, or `null` before each fetch.
    public void setFilterErrorConsumer(final Consumer<String> filterErrorConsumer) {
        this.filterErrorConsumer = filterErrorConsumer;
    }

    private void endSelectedTask() {
        for (final TaskProgress taskProgress : view.getSelectedTasks()) {
            doTerminate(taskProgress);
        }
        internalRefresh();
    }

    private void doTerminate(final TaskProgress taskProgress) {
        final FindTaskCriteria findTaskCriteria = new FindTaskCriteria();
        findTaskCriteria.addId(taskProgress.getId());
        final TerminateTaskProgressRequest request = new TerminateTaskProgressRequest(findTaskCriteria);
        restFactory
                .create(TASK_RESOURCE)
                .method(res -> res.terminate(taskProgress.getNodeName(), request))
                .owner(this)
                .taskMonitorFactory(view.getTaskMonitor())
                .exec();
    }

    private void openFeed(final String feedName) {
        feedClient.getDocRefForName(feedName, docRef -> {
            if (docRef != null) {
                OpenDocumentEvent.fire(this, docRef, true);
            } else {
                AlertEvent.fireError(this, "Unable to find feed '" + feedName + "'", null);
            }
        }, view.getTaskMonitor());
    }


    // --------------------------------------------------------------------------------


    private class NameFilterTimer extends Timer {

        private String name;

        @Override
        public void run() {
            String filter = name;
            if (filter != null) {
                filter = filter.trim();
                if (filter.length() == 0) {
                    filter = null;
                }
            }

            if (!Objects.equals(filter, criteria.getQuickFilter())) {
                // This is a new filter so reset all the expander states
                treeAction.reset();
                criteria.setQuickFilter(filter);
                internalRefresh();
            }
        }

        public void setName(final String name) {
            this.name = name;
        }
    }
}
