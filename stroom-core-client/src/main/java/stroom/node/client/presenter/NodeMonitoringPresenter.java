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
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.data.table.client.Refreshable;
import stroom.node.client.NodeManager;
import stroom.node.shared.ClusterNodeInfo;
import stroom.node.shared.FetchNodeStatusResponse;
import stroom.node.shared.Node;
import stroom.node.shared.NodeStatusResult;
import stroom.preferences.client.DateTimeFormatter;
import stroom.svg.client.Icon;
import stroom.svg.client.SvgPresets;
import stroom.util.client.DataGridUtil;
import stroom.util.client.SafeHtmlUtil;
import stroom.util.shared.BuildInfo;
import stroom.util.shared.ModelStringUtil;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;
import stroom.widget.tooltip.client.presenter.TooltipUtil;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.dom.client.Style.Unit;
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

public class NodeMonitoringPresenter extends ContentTabPresenter<PagerView>
        implements Refreshable {

    private static final NumberFormat THOUSANDS_FORMATTER = NumberFormat.getFormat("#,###");

    private final MyDataGrid<NodeStatusResult> dataGrid;
    private final NodeManager nodeManager;
    private final TooltipPresenter tooltipPresenter;
    private final DateTimeFormatter dateTimeFormatter;
    private final RestDataProvider<NodeStatusResult, FetchNodeStatusResponse> dataProvider;

    private final Map<String, PingResult> latestPing = new HashMap<>();

    @Inject
    public NodeMonitoringPresenter(final EventBus eventBus,
                                   final PagerView view,
                                   final NodeManager nodeManager,
                                   final TooltipPresenter tooltipPresenter,
                                   final DateTimeFormatter dateTimeFormatter) {
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
                                final Consumer<Throwable> throwableConsumer) {
                nodeManager.fetchNodeStatus(dataConsumer, throwableConsumer);
            }

            @Override
            protected void changeData(final FetchNodeStatusResponse data) {
                // Ping each node.
                data.getValues().forEach(row -> {
                    final String nodeName = row.getNode().getName();
                    nodeManager.ping(nodeName,
                            ping -> {
                                latestPing.put(nodeName, new PingResult(ping, null));
                                super.changeData(data);
                            },
                            throwable -> {
                                latestPing.put(nodeName, new PingResult(null, throwable.getMessage()));
                                super.changeData(data);
                            });
                });
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
            protected void showInfo(final NodeStatusResult row, final int x, final int y) {
                nodeManager.info(
                        row.getNode().getName(),
                        result -> showNodeInfoResult(row.getNode(), result, x, y),
                        caught -> showNodeInfoError(caught, x, y));
            }
        };
        dataGrid.addColumn(infoColumn, "<br/>", 20);

        // Name.
        final Column<NodeStatusResult, String> nameColumn = new Column<NodeStatusResult, String>(new TextCell()) {
            @Override
            public String getValue(final NodeStatusResult row) {
                if (row == null) {
                    return null;
                }
                return row.getNode().getName();
            }
        };
        dataGrid.addResizableColumn(nameColumn, "Name", 200);

        // Host Name.
        final Column<NodeStatusResult, String> hostNameColumn = new Column<NodeStatusResult, String>(new TextCell()) {
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
        final Column<NodeStatusResult, SafeHtml> safeHtmlColumn = DataGridUtil.safeHtmlColumn(row -> {
            if (row == null) {
                return null;
            }

            final PingResult pingResult = latestPing.get(row.getNode().getName());
            if (pingResult != null) {
                if ("No response".equals(pingResult.getError())) {
                    return SafeHtmlUtil.getSafeHtml(pingResult.getError());
                }
                if (pingResult.getError() != null) {
                    return SafeHtmlUtil.getSafeHtml("Error");
                }

                // Bar widths are all relative to the longest ping.
                final long unHealthyThresholdPing = 250;
                final long highestPing = latestPing.values().stream()
                        .filter(result -> result.getPing() != null)
                        .mapToLong(PingResult::getPing)
                        .max()
                        .orElse(unHealthyThresholdPing);
                final int maxBarWidthPct = 100;
                final double barWidthPct = pingResult.getPing() > highestPing
                        ? maxBarWidthPct
                        : ((double) pingResult.getPing() / highestPing * maxBarWidthPct);

                final SafeHtml textHtml = TooltipUtil.styledSpan(
                        THOUSANDS_FORMATTER.format(pingResult.getPing()),
                        safeStylesBuilder ->
                                safeStylesBuilder.paddingLeft(0.25, Unit.EM));

                final String healthyBarColour = "rgba(33, 150, 243, 0.6)"; // stroom blue with alpha
                final String unHealthyBarColour = "rgba(255, 111, 0, 0.6)"; // stroom dirty amber with aplha
                final String barColour = pingResult.getPing() < unHealthyThresholdPing
                        ? healthyBarColour
                        : unHealthyBarColour;

                final SafeHtml barDiv = TooltipUtil.styledDiv(textHtml, safeStylesBuilder ->
                        safeStylesBuilder
                                .trustedColor("#white")
                                .trustedBackgroundColor(barColour)
                                .width(barWidthPct, Unit.PCT)
                                .height(100, Unit.PCT));

                final SafeHtml outerDiv = TooltipUtil.styledDiv(barDiv, safeStylesBuilder ->
                        safeStylesBuilder
                                .paddingLeft(0.25, Unit.EM)
                                .paddingRight(0.25, Unit.EM));

                return outerDiv;
            }
            return SafeHtmlUtil.getSafeHtml("-");
        });
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
        dataGrid.addColumn(masterColumn, "Master", 50);

        // Priority.
        final Column<NodeStatusResult, Number> priorityColumn = new Column<NodeStatusResult, Number>(
                new ValueSpinnerCell(1, 100)) {
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
                        result -> refresh()));
        dataGrid.addColumn(priorityColumn, "Priority", 55);

        // Enabled
        final Column<NodeStatusResult, TickBoxState> enabledColumn = new Column<NodeStatusResult, TickBoxState>(
                TickBoxCell.create(false, false)) {
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
                        result -> refresh()));

        dataGrid.addColumn(enabledColumn, "Enabled", 60);

        dataGrid.addEndColumn(new EndColumn<>());
    }

    private void showNodeInfoResult(final Node node, final ClusterNodeInfo result, final int x, final int y) {
        final TooltipUtil.Builder builder = TooltipUtil.builder();

        if (result != null) {
            final BuildInfo buildInfo = result.getBuildInfo();
            builder
                    .addTwoColTable(tableBuilder -> {
                        tableBuilder.addHeaderRow("Node Details");
                        tableBuilder.addRow("Node Name", result.getNodeName(), true);
                        if (buildInfo != null) {
                            tableBuilder
                                    .addRow("Build Version", buildInfo.getBuildVersion(), true)
                                    .addRow("Build Date", dateTimeFormatter.format(buildInfo.getBuildTime()), true)
                                    .addRow("Up Date", dateTimeFormatter.format(buildInfo.getUpTime()), true);
                        }
                        return tableBuilder
                                .addRow("Discover Time", dateTimeFormatter.format(result.getDiscoverTime()), true)
                                .addRow("Node Endpoint URL", result.getEndpointUrl(), true)
                                .addRow("Ping", ModelStringUtil.formatDurationString(result.getPing()))
                                .addRow("Error", result.getError())
                                .build();
                    })
                    .addBreak()
                    .addHeading("Node List");

            if (result.getItemList() != null) {
                for (final ClusterNodeInfo.ClusterNodeInfoItem info : result.getItemList()) {
                    String nodeValue = info.getNodeName();

                    if (!info.isActive()) {
                        nodeValue += " (Unknown)";
                    }
                    if (info.isMaster()) {
                        nodeValue += " (Master)";
                    }
                    builder.addLine(nodeValue);
                }
            }
        } else {
            builder.addTwoColTable(tableBuilder -> tableBuilder
                    .addRow("Node Name", node.getName(), true)
                    .addRow("Cluster URL", node.getUrl(), true)
                    .build());
        }
        tooltipPresenter.show(builder.build(), x, y);
    }

    private void showNodeInfoError(final Throwable caught, final int x, final int y) {
        tooltipPresenter.show(SafeHtmlUtils.fromString(caught.getMessage()), x, y);
    }

    @Override
    public void refresh() {
        dataProvider.refresh();
    }

    @Override
    public Icon getIcon() {
        return SvgPresets.NODES;
    }

    @Override
    public String getLabel() {
        return "Nodes";
    }

    private static final class PingResult {

        private final Long ping;
        private final String error;

        PingResult(final Long ping, final String error) {
            this.ping = ping;
            this.error = error;
        }

        Long getPing() {
            return ping;
        }

        String getError() {
            return error;
        }
    }
}
