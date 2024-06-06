/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.node.client.presenter;

import stroom.cell.info.client.InfoColumn;
import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.cell.valuespinner.client.ValueSpinnerCell;
import stroom.cell.valuespinner.shared.EditableInteger;
import stroom.content.client.presenter.ContentTabPresenter;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.OrderByColumn;
import stroom.data.grid.client.PagerView;
import stroom.data.table.client.Refreshable;
import stroom.dispatch.client.RestErrorHandler;
import stroom.node.client.NodeManager;
import stroom.node.shared.ClusterNodeInfo;
import stroom.node.shared.FetchNodeStatusResponse;
import stroom.node.shared.FindNodeStatusCriteria;
import stroom.node.shared.Node;
import stroom.node.shared.NodeStatusResult;
import stroom.preferences.client.DateTimeFormatter;
import stroom.svg.client.IconColour;
import stroom.svg.shared.SvgImage;
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
import stroom.widget.util.client.SafeHtmlUtil;
import stroom.widget.util.client.TableBuilder;
import stroom.widget.util.client.TableCell;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.i18n.client.NumberFormat;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class NodeMonitoringPresenter
        extends ContentTabPresenter<PagerView>
        implements Refreshable {

    public static final String TAB_TYPE = "Nodes";

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

    @Inject
    public NodeMonitoringPresenter(final EventBus eventBus,
                                   final PagerView view,
                                   final NodeManager nodeManager,
                                   final TooltipPresenter tooltipPresenter,
                                   final DateTimeFormatter dateTimeFormatter,
                                   final UiConfigCache uiConfigCache) {
        super(eventBus, view);

        dataGrid = new MyDataGrid<>();
        view.setDataWidget(dataGrid);

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
                        NodeMonitoringPresenter.this);
            }

            @Override
            protected void changeData(final FetchNodeStatusResponse data) {
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
                                    NodeMonitoringPresenter.this);
                        });
                    }
                }, getView());

                super.changeData(data);
            }
        };
        dataProvider.addDataDisplay(dataGrid);
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
                        NodeMonitoringPresenter.this);
            }
        };
        dataGrid.addColumn(infoColumn, "<br/>", ColumnSizeConstants.ICON_COL);

        // Name.
        final Column<NodeStatusResult, String> nameColumn = new OrderByColumn<NodeStatusResult, String>(
                new TextCell(),
                FindNodeStatusCriteria.FIELD_ID_NAME,
                true) {
            @Override
            public String getValue(final NodeStatusResult row) {
                if (row == null) {
                    return null;
                }
                return row.getNode().getName();
            }
        };
        DataGridUtil.addColumnSortHandler(dataGrid, findNodeStatusCriteria, this::refresh);
        dataGrid.addResizableColumn(nameColumn, "Name", 200);

        // Host Name.
        final Column<NodeStatusResult, String> hostNameColumn = new OrderByColumn<NodeStatusResult, String>(
                new TextCell(),
                FindNodeStatusCriteria.FIELD_ID_URL,
                true) {
            @Override
            public String getValue(final NodeStatusResult row) {
                if (row == null) {
                    return null;
                }
                return row.getNode().getUrl();
            }
        };
        dataGrid.addResizableColumn(hostNameColumn, "Cluster Base Endpoint URL", 400);

        // Ping (ms)
        final Column<NodeStatusResult, SafeHtml> safeHtmlColumn = DataGridUtil.safeHtmlColumn(this::getPingBarSafeHtml);
        dataGrid.addResizableColumn(safeHtmlColumn, "Ping (ms)", 300);

        // Master.
        final Column<NodeStatusResult, TickBoxState> masterColumn = new Column<NodeStatusResult, TickBoxState>(
                TickBoxCell.create(new TickBoxCell.NoBorderAppearance(), false, false, false)) {
            @Override
            public TickBoxState getValue(final NodeStatusResult row) {
                if (row == null) {
                    return null;
                }
                return TickBoxState.fromBoolean(row.isMaster());
            }
        };
        masterColumn.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_CENTER);
        dataGrid.addColumn(masterColumn, "Master", 60);

        // Priority.
        final Column<NodeStatusResult, Number> priorityColumn = new OrderByColumn<NodeStatusResult, Number>(
                new ValueSpinnerCell(1, 100),
                FindNodeStatusCriteria.FIELD_ID_PRIORITY,
                true) {
            @Override
            public Number getValue(final NodeStatusResult row) {
                if (row == null) {
                    return null;
                }
                return new EditableInteger(row.getNode().getPriority());
            }
        };
        priorityColumn.setFieldUpdater((index, row, value) ->
                nodeManager.setPriority(
                        row.getNode().getName(),
                        value.intValue(),
                        result -> refresh(),
                        this));
        dataGrid.addColumn(priorityColumn, "Priority", 75);

        // Enabled
        final Column<NodeStatusResult, TickBoxState> enabledColumn = new OrderByColumn<NodeStatusResult, TickBoxState>(
                TickBoxCell.create(false, false),
                FindNodeStatusCriteria.FIELD_ID_ENABLED,
                true) {
            @Override
            public TickBoxState getValue(final NodeStatusResult row) {
                if (row == null) {
                    return null;
                }
                return TickBoxState.fromBoolean(row.getNode().isEnabled());
            }
        };
        enabledColumn.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_CENTER);
        enabledColumn.setFieldUpdater((index, row, value) ->
                nodeManager.setEnabled(
                        row.getNode().getName(),
                        value.toBoolean(),
                        result -> refresh(),
                        this));

        dataGrid.addColumn(enabledColumn, "Enabled", 70);

        dataGrid.addEndColumn(new EndColumn<>());
    }

    private SafeHtml getPingBarSafeHtml(final NodeStatusResult row) {
        if (row == null) {
            return SafeHtmlUtils.EMPTY_SAFE_HTML;
        }

        final PingResult pingResult = latestPing.get(row.getNode().getName());

        if (pingResult != null) {
            if ("No response".equals(pingResult.getError())) {
                return SafeHtmlUtil.getSafeHtml(pingResult.getError());
            }
            if (pingResult.getError() != null) {
                return SafeHtmlUtil.getSafeHtml("Error");
            }
            final Long ping = pingResult.getPing();
            if (ping == null || ping < 0) {
                return SafeHtmlUtil.getSafeHtml("Invalid ping value: "
                        + GwtNullSafe.requireNonNullElse(ping, "null"));
            }

            return buildPingBar(ping, pingResult.getNodeMonitoringConfig());
        }
        return SafeHtmlUtil.getSafeHtml("-");
    }

    private SafeHtml buildPingBar(final long ping, final NodeMonitoringConfig nodeMonitoringConfig) {
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

        HtmlBuilder htmlBuilder = new HtmlBuilder();
        htmlBuilder.div(hb1 ->
                hb1.div(hb2 ->
                                hb2.span(hb3 ->
                                                hb3.append(THOUSANDS_FORMATTER
                                                        .format(ping)),
                                        Attribute.className(PING_TEXT_CLASS)),
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

    @Override
    public void refresh() {
        dataProvider.refresh();
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.NODES;
    }

    @Override
    public IconColour getIconColour() {
        return IconColour.GREY;
    }

    @Override
    public String getLabel() {
        return "Nodes";
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }


    // --------------------------------------------------------------------------------


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
