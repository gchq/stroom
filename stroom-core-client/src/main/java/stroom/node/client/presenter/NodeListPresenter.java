package stroom.node.client.presenter;

import stroom.cell.info.client.InfoColumn;
import stroom.cell.valuespinner.shared.EditableInteger;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.node.client.NodeManager;
import stroom.node.shared.ClusterNodeInfo;
import stroom.node.shared.FetchNodeStatusResponse;
import stroom.node.shared.FindNodeStatusCriteria;
import stroom.node.shared.Node;
import stroom.node.shared.NodeStatusResult;
import stroom.preferences.client.DateTimeFormatter;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.NodeMonitoringConfig;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.BuildInfo;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.ModelStringUtil;
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

public class NodeListPresenter extends MyPresenterWidget<PagerView> {

    private static final NumberFormat THOUSANDS_FORMATTER = NumberFormat.getFormat("#,###");
    private static final String CLASS_BASE = "nodePingBar";
    private static final String PING_BAR_CLASS = CLASS_BASE + "-bar";
    private static final String PING_TEXT_CLASS = CLASS_BASE + "-text";
    private static final String PING_SEVERITY_CLASS_BASE = CLASS_BASE + "-severity";
    private static final String PING_SEVERITY_CLASS_SUFFIX_HEALTHY = "__healthy";
    private static final String PING_SEVERITY_CLASS_SUFFIX_WARN = "__warn";
    private static final String PING_SEVERITY_CLASS_SUFFIX_MAX = "__max";

    private final MyDataGrid<NodeStatusResult> dataGrid;
    private final NodeManager nodeManager;
    private final TooltipPresenter tooltipPresenter;
    private final DateTimeFormatter dateTimeFormatter;
    private final RestDataProvider<NodeStatusResult, FetchNodeStatusResponse> dataProvider;

    private final Map<String, PingResult> latestPing = new HashMap<>();
    private final FindNodeStatusCriteria findNodeStatusCriteria = new FindNodeStatusCriteria();
    private final MultiSelectionModelImpl<NodeStatusResult> selectionModel;
    private Map<String, NodeStatusResult> nodeNameToNodeStatusMap = null;
    private String selectedNodeName = null;

    @Inject
    public NodeListPresenter(final EventBus eventBus,
                             final PagerView view,
                             final NodeManager nodeManager,
                             final TooltipPresenter tooltipPresenter,
                             final DateTimeFormatter dateTimeFormatter,
                             final UiConfigCache uiConfigCache) {
        super(eventBus, view);

        dataGrid = new MyDataGrid<>();
        view.setDataWidget(dataGrid);
        selectionModel = dataGrid.addDefaultSelectionModel(true);

        this.nodeManager = nodeManager;
        this.tooltipPresenter = tooltipPresenter;
        this.dateTimeFormatter = dateTimeFormatter;

        initTableColumns();
        dataProvider = new RestDataProvider<NodeStatusResult, FetchNodeStatusResponse>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<FetchNodeStatusResponse> dataConsumer,
                                final RestErrorHandler errorHandler) {
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
                                        super.changeData(data);
                                    },
                                    throwable -> {
                                        latestPing.put(nodeName, PingResult.error(
                                                throwable.getMessage(), nodeMonitoringConfig));
                                        super.changeData(data);
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

    public MultiSelectionModel<NodeStatusResult> getSelectionModel() {
        return selectionModel;
    }

    private static boolean isNodeEnabled(NodeStatusResult nodeStatusResult) {
        return GwtNullSafe.isTrue(nodeStatusResult.getNode(), Node::isEnabled);
    }

    private static Number extractNodePriority(NodeStatusResult result) {
        return GwtNullSafe.get(
                result,
                NodeStatusResult::getNode,
                Node::getPriority,
                EditableInteger::new);
    }

    private String extractLastBootTimeAsStr(NodeStatusResult result) {
        return GwtNullSafe.get(
                result,
                NodeStatusResult::getNode,
                Node::getLastBootMs,
                dateTimeFormatter::formatWithDuration);
    }

    /**
     * Add the columns to the table.
     */
    private void initTableColumns() {
        DataGridUtil.addColumnSortHandler(dataGrid, findNodeStatusCriteria, this::refresh);

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
                DataGridUtil.textColumnBuilder((NodeStatusResult result) -> GwtNullSafe.get(
                                result,
                                NodeStatusResult::getNode,
                                Node::getName))
                        .enabledWhen(NodeListPresenter::isNodeEnabled)
                        .withSorting(FindNodeStatusCriteria.FIELD_ID_NAME)
                        .build(),
                DataGridUtil.headingBuilder("Name")
                        .withToolTip("The name of the node as defined in configuration.")
                        .build(),
                300);

        // Host Name.
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder((NodeStatusResult result) -> GwtNullSafe.get(
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
                DataGridUtil.textColumnBuilder((NodeStatusResult result) -> GwtNullSafe.get(
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
                DataGridUtil.readOnlyTickBoxColumnBuilder((NodeStatusResult result) -> GwtNullSafe.get(
                                result,
                                NodeStatusResult::isMaster))
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
                                        result -> refresh(),
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
                DataGridUtil.updatableTickBoxColumnBuilder((NodeStatusResult result) -> GwtNullSafe.get(
                                result,
                                NodeStatusResult::getNode,
                                Node::isEnabled))
                        .enabledWhen(NodeListPresenter::isNodeEnabled)
                        .withSorting(FindNodeStatusCriteria.FIELD_ID_ENABLED)
                        .withFieldUpdater((index, row, value) ->
                                nodeManager.setEnabled(
                                        row.getNode().getName(),
                                        value.toBoolean(),
                                        result -> refresh(),
                                        this))
                        .centerAligned()
                        .build(),
                DataGridUtil.headingBuilder("Enabled")
                        .withToolTip("Whether the node is enabled for processing and background jobs.")
                        .centerAligned()
                        .build(),
                70);

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
            if (!node.isEnabled()) {
                // Disabled so bad ping is expected
                if (ping == null) {
                    ping = 0L;
                    pingMsg = "No response from disabled node";
                }
            } else if ("No response".equals(pingResult.getError())) {
                ping = Long.MAX_VALUE;
                pingMsg = pingResult.getError();
            } else if (pingResult.getError() != null) {
                ping = Long.MAX_VALUE;
                pingMsg = "Error: " + pingResult.getError();
            } else if (ping == null || ping < 0) {
                ping = Long.MAX_VALUE;
                pingMsg = "Invalid ping value: "
                        + GwtNullSafe.requireNonNullElse(ping, "null");
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

        final String text;
        final String title;
        if (msg == null) {
            text = THOUSANDS_FORMATTER.format(ping);
            title = "Ping: " + ping + "ms";
        } else {
            text = msg;
            title = msg;
        }

        HtmlBuilder htmlBuilder = new HtmlBuilder();
        htmlBuilder.div(hb1 ->
                hb1.div(hb2 ->
                                hb2.span(hb3 -> hb3.append(text),
                                        Attribute.className(PING_TEXT_CLASS),
                                        Attribute.title(title)),
                        Attribute.className(PING_BAR_CLASS + " " + severityClass),
                        Attribute.style("width:" +
                                barWidthPct +
                                "%")), Attribute.className("nodePingBar-outer"));
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

    void refresh() {
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
