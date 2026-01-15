/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.node.client.presenter;

import stroom.cell.info.client.InfoColumn;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.cell.valuespinner.shared.EditableInteger;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.data.table.client.Refreshable;
import stroom.dispatch.client.RestErrorHandler;
import stroom.node.client.NodeManager;
import stroom.node.client.event.NodeChangeEvent;
import stroom.node.shared.ClusterNodeInfo;
import stroom.node.shared.FetchNodeStatusResponse;
import stroom.node.shared.FindNodeStatusCriteria;
import stroom.node.shared.Node;
import stroom.node.shared.NodeStatusResult;
import stroom.preferences.client.DateTimeFormatter;
import stroom.svg.shared.SvgImage;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.NodeMonitoringConfig;
import stroom.util.client.DataGridUtil;
import stroom.util.client.DelayedUpdate;
import stroom.util.shared.BuildInfo;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.InlineSvgToggleButton;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;
import stroom.widget.util.client.SafeHtmlUtil;
import stroom.widget.util.client.TableBuilder;
import stroom.widget.util.client.TableCell;

import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.i18n.client.NumberFormat;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class NodeListPresenter extends MyPresenterWidget<PagerView> implements Refreshable {

    private static final NumberFormat THOUSANDS_FORMATTER = NumberFormat.getFormat("#,###");
    private static final String CLASS_BASE = "nodePingBar";
    private static final String PING_BAR_CLASS = CLASS_BASE + "-bar";
    private static final String PING_TEXT_CLASS = CLASS_BASE + "-text";
    private static final String PING_ERROR_TEXT_CLASS = CLASS_BASE + "-error-text";
    private static final String PING_SEVERITY_CLASS_BASE = CLASS_BASE + "-severity";
    private static final String PING_SEVERITY_CLASS_SUFFIX_HEALTHY = "__healthy";
    private static final String PING_SEVERITY_CLASS_SUFFIX_WARN = "__warn";
    private static final String PING_SEVERITY_CLASS_SUFFIX_MAX = "__max";
    private static final String AUTO_REFRESH_ON_TITLE = "Turn Auto Refresh Off";
    private static final String AUTO_REFRESH_OFF_TITLE = "Turn Auto Refresh On";

    private final MyDataGrid<NodeStatusResult> dataGrid;
    private final NodeManager nodeManager;
    private final TooltipPresenter tooltipPresenter;
    private final DateTimeFormatter dateTimeFormatter;
    private final RestDataProvider<NodeStatusResult, FetchNodeStatusResponse> dataProvider;

    private final Map<String, PingResult> latestPing = new HashMap<>();
    private final FindNodeStatusCriteria findNodeStatusCriteria = new FindNodeStatusCriteria();
    private final MultiSelectionModelImpl<NodeStatusResult> selectionModel;
    private final InlineSvgToggleButton autoRefreshButton;
    private final DelayedUpdate redrawDelayedUpdate;

    private Map<String, NodeStatusResult> nodeNameToNodeStatusMap = null;
    private String selectedNodeName = null;
    private boolean autoRefresh = true;

    @Inject
    public NodeListPresenter(final EventBus eventBus,
                             final PagerView view,
                             final NodeManager nodeManager,
                             final TooltipPresenter tooltipPresenter,
                             final DateTimeFormatter dateTimeFormatter,
                             final UiConfigCache uiConfigCache) {
        super(eventBus, view);

        dataGrid = new MyDataGrid<>(this);
        view.setDataWidget(dataGrid);
        selectionModel = dataGrid.addDefaultSelectionModel(false);

        this.nodeManager = nodeManager;
        this.tooltipPresenter = tooltipPresenter;
        this.dateTimeFormatter = dateTimeFormatter;
        this.redrawDelayedUpdate = new DelayedUpdate(dataGrid::redraw);

        autoRefreshButton = new InlineSvgToggleButton();
        autoRefreshButton.setSvg(SvgImage.AUTO_REFRESH);
        autoRefreshButton.setTitle(AUTO_REFRESH_ON_TITLE);
        autoRefreshButton.setState(autoRefresh);
        getView().addButton(autoRefreshButton);

        initTableColumns();
        dataProvider = new RestDataProvider<NodeStatusResult, FetchNodeStatusResponse>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<FetchNodeStatusResponse> dataConsumer,
                                final RestErrorHandler errorHandler) {
                CriteriaUtil.setSortList(findNodeStatusCriteria, dataGrid.getColumnSortList());
                nodeManager.fetchNodeStatus(dataConsumer, errorHandler, findNodeStatusCriteria,
                        NodeListPresenter.this);
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
                            nodeManager.ping(nodeName,
                                    pingMs -> {
                                        latestPing.put(nodeName, PingResult.success(pingMs, nodeMonitoringConfig));
                                        scheduleDataGridRedraw();
                                    },
                                    throwable -> {
                                        latestPing.put(nodeName, PingResult.error(
                                                throwable.getMessage(), nodeMonitoringConfig));
                                        scheduleDataGridRedraw();
                                    },
                                    NodeListPresenter.this);
                        });
                    }
                }, getView());

                super.changeData(data);
            }
        };
        dataProvider.addDataDisplay(dataGrid);
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(autoRefreshButton.addClickHandler(event -> {
            if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                autoRefresh = !autoRefresh;
                if (autoRefresh) {
                    autoRefreshButton.setTitle(AUTO_REFRESH_ON_TITLE);
                    internalRefresh();
                } else {
                    autoRefreshButton.setTitle(AUTO_REFRESH_OFF_TITLE);
                }
            }
        }));

        registerHandler(dataGrid.addColumnSortHandler(event -> internalRefresh()));
    }

    private void scheduleDataGridRedraw() {
        // Saves the grid being redrawn for every single row in the list
        redrawDelayedUpdate.update();
    }

    public MultiSelectionModel<NodeStatusResult> getSelectionModel() {
        return selectionModel;
    }

    private static boolean isNodeEnabled(final NodeStatusResult nodeStatusResult) {
        return NullSafe.isTrue(nodeStatusResult.getNode(), Node::isEnabled);
    }

    private static Number extractNodePriority(final NodeStatusResult result) {
        return NullSafe.get(
                result,
                NodeStatusResult::getNode,
                Node::getPriority,
                EditableInteger::new);
    }

    private String extractLastBootTimeAsStr(final NodeStatusResult result) {
        return NullSafe.get(
                result,
                NodeStatusResult::getNode,
                Node::getLastBootMs,
                dateTimeFormatter::formatWithDuration);
    }

    /**
     * Add the columns to the table.
     */
    private void initTableColumns() {
        // Info column.
        final InfoColumn<NodeStatusResult> infoColumn = new InfoColumn<NodeStatusResult>() {
            @Override
            protected void showInfo(final NodeStatusResult row, final PopupPosition popupPosition) {
                nodeManager.info(
                        row.getNode().getName(),
                        result -> showNodeInfoResult(row.getNode(), result, popupPosition),
                        error -> showNodeInfoError(error.getException(), popupPosition),
                        NodeListPresenter.this);
            }
        };
        dataGrid.addColumn(infoColumn, "<br/>", ColumnSizeConstants.ICON_COL);

        // Name.
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(DataGridUtil.toStringFunc(NodeStatusResult::getNode, Node::getName))
                        .enabledWhen(NodeListPresenter::isNodeEnabled)
                        .withSorting(FindNodeStatusCriteria.FIELD_ID_NAME)
                        .build(),
                DataGridUtil.headingBuilder("Name")
                        .withToolTip("The name of the node as defined in configuration.")
                        .build(),
                300);

        // Host Name.
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder((NodeStatusResult result) -> NullSafe.get(
                                result,
                                NodeStatusResult::getNode,
                                Node::getUrl))
                        .enabledWhen(NodeListPresenter::isNodeEnabled)
                        .withSorting(FindNodeStatusCriteria.FIELD_ID_URL)
                        .build(),
                DataGridUtil.headingBuilder("Cluster Base Endpoint")
                        .withToolTip("The base URL for the node.")
                        .build(),
                300);

        // Version
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder((NodeStatusResult result) -> NullSafe.get(
                                result,
                                NodeStatusResult::getNode,
                                Node::getBuildVersion))
                        .enabledWhen(NodeListPresenter::isNodeEnabled)
                        .withSorting(FindNodeStatusCriteria.FIELD_ID_BUILD_VERSION)
                        .build(),
                DataGridUtil.headingBuilder("Build Version")
                        .withToolTip("The version of Stroom that the node is running.")
                        .build(),
                150);

        // Ping (ms)
        dataGrid.addResizableColumn(
                DataGridUtil.columnBuilder(
                                this::getPingBarSafeHtml,
                                SafeHtmlCell::new)
                        .enabledWhen(NodeListPresenter::isNodeEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Ping (ms)")
                        .withToolTip("The time in milliseconds to get a response back from the node.")
                        .build(),
                250);

        // Last Boot Time
        dataGrid.addColumn(
                DataGridUtil.textColumnBuilder(this::extractLastBootTimeAsStr)
                        .enabledWhen(NodeListPresenter::isNodeEnabled)
                        .withSorting(FindNodeStatusCriteria.FIELD_ID_LAST_BOOT_MS)
                        .build(),
                DataGridUtil.headingBuilder("Up Date")
                        .withToolTip("The time that the node last started up whether currently running or not.")
                        .build(),
                ColumnSizeConstants.DATE_AND_DURATION_COL);

        // Master.
        dataGrid.addColumn(
                DataGridUtil.readOnlyTickBoxColumnBuilder(TickBoxState.createTickBoxFunc(
                                (NodeStatusResult result) -> NullSafe.get(
                                        result,
                                        NodeStatusResult::isMaster)))
                        .enabledWhen(NodeListPresenter::isNodeEnabled)
                        .centerAligned()
                        .build(),
                DataGridUtil.headingBuilder("Master")
                        .withToolTip("Whether the node has been assigned as the master node for the cluster.")
                        .centerAligned()
                        .build(),
                60);

        // Priority.
        dataGrid.addColumn(
                DataGridUtil.valueSpinnerColumnBuilder(NodeListPresenter::extractNodePriority,
                                1L,
                                100L)
                        .enabledWhen(NodeListPresenter::isNodeEnabled)
                        .withSorting(FindNodeStatusCriteria.FIELD_ID_PRIORITY)
                        .withFieldUpdater((rowIndex, nodeStatusResult, value) ->
                                nodeManager.setPriority(
                                        nodeStatusResult.getNode().getName(),
                                        value.intValue(),
                                        result -> internalRefresh(),
                                        this))
                        .build(),
                DataGridUtil.headingBuilder("Priority")
                        .withToolTip("Defines how important the node is when it comes to " +
                                     "assigning a master. The larger the number the more chance it will become " +
                                     "the master.")
                        .build(),
                80);

        // Enabled
        dataGrid.addColumn(
                DataGridUtil.updatableTickBoxColumnBuilder(TickBoxState.createTickBoxFunc(
                                (NodeStatusResult result) -> NullSafe.get(
                                        result,
                                        NodeStatusResult::getNode,
                                        Node::isEnabled)))
                        .enabledWhen(NodeListPresenter::isNodeEnabled)
                        .withSorting(FindNodeStatusCriteria.FIELD_ID_ENABLED)
                        .withFieldUpdater((index, row, value) -> {
                            if (row != null) {
                                final String nodeName = row.getNode().getName();
                                nodeManager.setEnabled(
                                        nodeName,
                                        value.toBoolean(),
                                        result -> {
                                            internalRefresh();
                                            NodeChangeEvent.fire(NodeListPresenter.this, nodeName);
                                        },
                                        this);
                            }
                        })
                        .centerAligned()
                        .build(),
                DataGridUtil.headingBuilder("Enabled")
                        .withToolTip("Whether the node is enabled for processing and background jobs.")
                        .centerAligned()
                        .build(),
                ColumnSizeConstants.ENABLED_COL);

        DataGridUtil.addEndColumn(dataGrid);
    }

    private SafeHtml getPingBarSafeHtml(final NodeStatusResult row) {
        if (row == null) {
            return SafeHtmlUtils.EMPTY_SAFE_HTML;
        }

        final Node node = row.getNode();
        final PingResult pingResult = latestPing.get(node.getName());

        if (pingResult != null) {
            Long ping = pingResult.getPing();
            String pingMsg = null;
            if (!node.isEnabled() && ping == null) {
                // Disabled so bad ping is expected
                ping = 0L;
                pingMsg = pingResult.getError();
            } else if (pingResult.getError() != null) {
                ping = Long.MAX_VALUE;
                pingMsg = pingResult.getError();
            } else if (ping == null || ping < 0) {
                ping = Long.MAX_VALUE;
                pingMsg = "Invalid ping value: "
                          + NullSafe.requireNonNullElse(ping, "null");
            }

            return buildPingBar(ping, pingMsg, pingResult.getNodeMonitoringConfig());
        }
        return SafeHtmlUtil.getSafeHtml("-");
    }

    private SafeHtml buildPingBar(final Long ping,
                                  final String msg,
                                  final NodeMonitoringConfig nodeMonitoringConfig) {
        final int warnThresholdMs = nodeMonitoringConfig.getPingWarnThreshold();
        final int maxThresholdMs = nodeMonitoringConfig.getPingMaxThreshold();
        final int maxBarWidthPct = 100;

        final double barWidthPct = ping >= maxThresholdMs
                ? maxBarWidthPct
                : ((double) ping / maxThresholdMs * maxBarWidthPct);

        final String severityClassSuffix;
        if (ping >= maxThresholdMs) {
            severityClassSuffix = PING_SEVERITY_CLASS_SUFFIX_MAX;
        } else if (ping >= warnThresholdMs) {
            severityClassSuffix = PING_SEVERITY_CLASS_SUFFIX_WARN;
        } else {
            severityClassSuffix = PING_SEVERITY_CLASS_SUFFIX_HEALTHY;
        }
        final String severityClass = PING_SEVERITY_CLASS_BASE + severityClassSuffix;

//        GWT.log("ping: " + ping + ", msg '" + msg + "', severityClass: "
//                + severityClass + ", barWidthPct: " + barWidthPct);

        final String text;
        final String title;
        final boolean isErrorMsg;
        if (msg == null) {
            final String pingStr = THOUSANDS_FORMATTER.format(ping);
            text = pingStr;
            title = "Ping: " + pingStr + " ms";
            isErrorMsg = false;
        } else {
            if (msg.startsWith("Unable to connect")
                || msg.startsWith("Error calling node")
                || msg.startsWith("Request failed")) {
                text = "Unable to connect";
            } else {
                text = "Error";
            }
            title = msg;
            isErrorMsg = true;
        }

        final Attribute barWidthAttr = Attribute.style("width:" + barWidthPct + "%");
        final Attribute textClass = Attribute.className(PING_TEXT_CLASS);
        final Attribute errorTextClass = Attribute.className(PING_ERROR_TEXT_CLASS);
        final Attribute barClass = Attribute.className(PING_BAR_CLASS + " " + severityClass);
        final Attribute barWrapperClass = Attribute.className(PING_BAR_CLASS + "-wrapper");
        final Attribute titleAttr = Attribute.title(title);
        final Attribute outerClass = Attribute.className("nodePingBar-outer");

        final HtmlBuilder htmlBuilder = new HtmlBuilder();
        if (isErrorMsg) {
            // A text div covering the whole of the cell
            // e.g. ' Unable to connect           '
            htmlBuilder.div(outerBuilder -> outerBuilder
                            .div(text, errorTextClass),
                    outerClass, titleAttr);
        } else {
            // A text div for the ping number
            // A div for the ping bar
            // e.g. '   23 ===================      '
            htmlBuilder.div(outerBuilder -> outerBuilder
                            .div(text, textClass)
                            .div(barWrapperBuilder ->
                                            barWrapperBuilder.div(
                                                    (Consumer<HtmlBuilder>) null, barWidthAttr, barClass),
                                    barWrapperClass),
                    outerClass, titleAttr);
        }
        return htmlBuilder.toSafeHtml();
    }

    private void showNodeInfoResult(final Node node, final ClusterNodeInfo result, final PopupPosition popupPosition) {
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

        tooltipPresenter.show(htmlBuilder.toSafeHtml(), popupPosition);
    }

    private void showNodeInfoError(final Throwable caught, final PopupPosition popupPosition) {
        tooltipPresenter.show(SafeHtmlUtils.fromString(caught.getMessage()), popupPosition);
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

    public void setSelected(final String nodeName) {
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


    // --------------------------------------------------------------------------------


    @SuppressWarnings("ClassCanBeRecord")
    private static final class PingResult {

        private final Long ping;
        private final String error;
        private final NodeMonitoringConfig nodeMonitoringConfig;

        PingResult(final Long ping,
                   final String error,
                   final NodeMonitoringConfig nodeMonitoringConfig) {
            this.ping = ping;
            this.error = error;
            this.nodeMonitoringConfig = nodeMonitoringConfig;
        }

        static PingResult success(final Long ping, final NodeMonitoringConfig nodeMonitoringConfig) {
            return new PingResult(ping, null, nodeMonitoringConfig);
        }

        static PingResult error(final String error, final NodeMonitoringConfig nodeMonitoringConfig) {
            return new PingResult(null, error, nodeMonitoringConfig);
        }

        Long getPing() {
            return ping;
        }

        String getError() {
            return error;
        }

        public NodeMonitoringConfig getNodeMonitoringConfig() {
            return nodeMonitoringConfig;
        }
    }
}
