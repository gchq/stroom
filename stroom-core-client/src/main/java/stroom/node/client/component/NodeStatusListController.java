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

package stroom.node.client.component;

import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.table.client.Refreshable;
import stroom.dispatch.client.RestErrorHandler;
import stroom.node.client.NodeClient;
import stroom.node.client.event.NodeChangeEvent;
import stroom.node.shared.ClusterNodeInfo;
import stroom.node.shared.FetchNodeStatusResponse;
import stroom.node.shared.FindNodeStatusCriteria;
import stroom.node.shared.Node;
import stroom.node.shared.NodeStatusResult;
import stroom.preferences.client.DateTimeFormatter;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.NodeMonitoringConfig;
import stroom.util.shared.BuildInfo;
import stroom.util.shared.ModelStringUtil;
import stroom.widget.component.client.Controller;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.TableBuilder;
import stroom.widget.util.client.TableCell;

import com.google.gwt.dom.client.Element;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/// The cluster's nodes ([NodeStatusList]): fetched, then each pinged; a node's info shown in a
/// tooltip, its priority and whether it is enabled saved as they are changed. Refreshes only while
/// auto refresh is on.
public class NodeStatusListController extends Controller implements Refreshable {

    private final NodeStatusList view;
    private final NodeClient nodeClient;
    private final TooltipPresenter tooltipPresenter;
    private final DateTimeFormatter dateTimeFormatter;
    private final RestDataProvider<NodeStatusResult, FetchNodeStatusResponse> dataProvider;

    private final Map<String, NodePing> latestPing = new HashMap<>();
    private final FindNodeStatusCriteria findNodeStatusCriteria = new FindNodeStatusCriteria();

    private Map<String, NodeStatusResult> nodeNameToNodeStatusMap = null;
    private String selectedNodeName = null;
    private boolean autoRefresh = true;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus          The application's event bus.
    /// @param view              The content.
    /// @param nodeClient        Fetches, pings and changes the nodes.
    /// @param tooltipPresenter  Shows a node's info.
    /// @param dateTimeFormatter Formats times in the user's preferences.
    /// @param uiConfigCache     Gives the ping thresholds.
    @Inject
    public NodeStatusListController(final EventBus eventBus,
                                    final NodeStatusList view,
                                    final NodeClient nodeClient,
                                    final TooltipPresenter tooltipPresenter,
                                    final DateTimeFormatter dateTimeFormatter,
                                    final UiConfigCache uiConfigCache) {
        super(eventBus);
        this.view = view;
        own(tooltipPresenter);
        this.nodeClient = nodeClient;
        this.tooltipPresenter = tooltipPresenter;
        this.dateTimeFormatter = dateTimeFormatter;
        initWidget(view);

        view.setPingLookup(latestPing::get);
        view.setDateFormat(dateTimeFormatter::formatWithDuration);
        view.setOnAutoRefreshToggle(pressed -> onAutoRefreshToggled());
        view.setOnShowInfo((row, popupPosition, anchor) -> nodeClient.info(
                row.getNode().getName(),
                result -> showNodeInfoResult(row.getNode(), result, popupPosition, anchor),
                error -> showNodeInfoError(error.getException(), popupPosition, anchor),
                view.getTaskMonitor()));
        view.setOnPriorityChange((row, priority) -> nodeClient.setPriority(
                row.getNode().getName(),
                priority,
                result -> internalRefresh(),
                view.getTaskMonitor()));
        view.setOnEnabledChange((row, enabled) -> {
            final String nodeName = row.getNode().getName();
            nodeClient.setEnabled(
                    nodeName,
                    enabled,
                    result -> {
                        internalRefresh();
                        NodeChangeEvent.fire(NodeStatusListController.this, nodeName);
                    },
                    view.getTaskMonitor());
        });

        dataProvider = new RestDataProvider<NodeStatusResult, FetchNodeStatusResponse>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<FetchNodeStatusResponse> dataConsumer,
                                final RestErrorHandler errorHandler) {
                CriteriaUtil.setSortList(findNodeStatusCriteria, view.getSortList());
                nodeClient.fetchNodeStatus(dataConsumer, errorHandler, findNodeStatusCriteria,
                        view.getTaskMonitor());
            }

            @Override
            protected void changeData(final FetchNodeStatusResponse data) {
                // Build a map to help us with selecting rows
                final boolean isSelectionRequired = nodeNameToNodeStatusMap == null;
                nodeNameToNodeStatusMap = data.stream()
                        .filter(Objects::nonNull)
                        .filter(nodeStatus -> nodeStatus.getNode() != null)
                        .collect(Collectors.toMap(
                                nodeStatus -> nodeStatus.getNode().getName(),
                                Function.identity()));
                // Do the requested selection now we can map node names to rows
                if (isSelectionRequired) {
                    setSelected(selectedNodeName);
                }

                uiConfigCache.get(uiConfig -> {
                    if (uiConfig != null) {
                        final NodeMonitoringConfig nodeMonitoringConfig = uiConfig.getNodeMonitoring();

                        // Ping each node.
                        data.getValues().forEach(row -> {
                            final String nodeName = row.getNode().getName();
                            nodeClient.ping(nodeName,
                                    pingMs -> {
                                        latestPing.put(nodeName, NodePing.success(pingMs, nodeMonitoringConfig));
                                        view.scheduleRedraw();
                                    },
                                    throwable -> {
                                        latestPing.put(nodeName, NodePing.error(
                                                throwable.getMessage(), nodeMonitoringConfig));
                                        view.scheduleRedraw();
                                    },
                                    view.getTaskMonitor());
                        });
                    }
                }, view.getTaskMonitor());

                super.changeData(data);
            }
        };
        view.setDataProvider(dataProvider);
        view.setOnSortChange(this::internalRefresh);
        onDispose(view::cancelRedraw);
    }

    private void onAutoRefreshToggled() {
        autoRefresh = !autoRefresh;
        if (autoRefresh) {
            internalRefresh();
        }
    }

    /// @return The selection, one node at most.
    public MultiSelectionModel<NodeStatusResult> getSelectionModel() {
        return view.getSelectionModel();
    }

    private void showNodeInfoResult(final Node node,
                                    final ClusterNodeInfo result,
                                    final PopupPosition popupPosition,
                                    final Element anchor) {
        final TableBuilder tb1 = new TableBuilder();
        final TableBuilder tb2 = new TableBuilder();

        if (result != null) {
            final BuildInfo buildInfo = result.getBuildInfo();
            tb1
                    .row(TableCell.header("Node Details", 2))
                    .row("Node Name", result.getNodeName());
            if (buildInfo != null) {
                tb1
                        .row("Build Version", buildInfo.getBuildVersion())
                        .row("Build Date", dateTimeFormatter.format(buildInfo.getBuildTime()))
                        .row("Up Date", dateTimeFormatter.format(buildInfo.getUpTime()));
            }
            tb1
                    .row("Discover Time", dateTimeFormatter.format(result.getDiscoverTime()))
                    .row("Node Endpoint URL", result.getEndpointUrl())
                    .row("Ping", ModelStringUtil.formatDurationString(result.getPing()))
                    .row("Error", result.getError());

            tb2.row(TableCell.header("Node List"));
            if (result.getItemList() != null) {
                for (final ClusterNodeInfo.ClusterNodeInfoItem info : result.getItemList()) {
                    String nodeValue = info.getNodeName();

                    if (!info.isActive()) {
                        nodeValue += " (Unknown)";
                    }
                    if (info.isMaster()) {
                        nodeValue += " (Master)";
                    }
                    tb2.row(nodeValue);
                }
            }

        } else {
            tb1
                    .row("Node Name", node.getName())
                    .row("Cluster URL", node.getUrl());
        }

        final HtmlBuilder htmlBuilder = new HtmlBuilder();
        htmlBuilder.div(tb1::write, Attribute.className("infoTable"));
        htmlBuilder.div(tb2::write, Attribute.className("infoTable"));

        tooltipPresenter.show(htmlBuilder.toSafeHtml(), popupPosition, anchor);
    }

    private void showNodeInfoError(final Throwable caught,
                                   final PopupPosition popupPosition,
                                   final Element anchor) {
        tooltipPresenter.show(SafeHtmlUtils.fromString(caught.getMessage()), popupPosition, anchor);
    }

    @Override
    public void refresh() {
        if (autoRefresh) {
            internalRefresh();
        }
    }

    private void internalRefresh() {
        dataProvider.refresh();
    }

    /// Selects a node, now or once the nodes are fetched.
    ///
    /// @param nodeName The node's name, or `null` for none.
    public void setSelected(final String nodeName) {
        final MultiSelectionModel<NodeStatusResult> selectionModel = view.getSelectionModel();
        selectedNodeName = nodeName;
        selectionModel.clear();

        if (nodeName != null && nodeNameToNodeStatusMap != null) {
            final NodeStatusResult nodeStatusResult = nodeNameToNodeStatusMap.get(nodeName);
            if (nodeStatusResult != null) {
                selectionModel.setSelected(nodeStatusResult);
            } else {
                selectionModel.clear();
            }
        }
    }
}
