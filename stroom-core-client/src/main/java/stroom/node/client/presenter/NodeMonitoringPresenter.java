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

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import stroom.cell.info.client.InfoColumn;
import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.cell.valuespinner.client.ValueSpinnerCell;
import stroom.cell.valuespinner.shared.EditableInteger;
import stroom.content.client.presenter.ContentTabPresenter;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.data.table.client.Refreshable;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.node.shared.ClusterNodeInfo;
import stroom.node.shared.FetchNodeStatusResponse;
import stroom.node.shared.Node;
import stroom.node.shared.NodeResource;
import stroom.node.shared.NodeStatusResult;
import stroom.svg.client.Icon;
import stroom.svg.client.SvgPresets;
import stroom.util.shared.BuildInfo;
import stroom.util.shared.ModelStringUtil;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupView.PopupType;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;
import stroom.widget.tooltip.client.presenter.TooltipUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class NodeMonitoringPresenter extends ContentTabPresenter<DataGridView<NodeStatusResult>> implements Refreshable {
    private static final NodeResource NODE_RESOURCE = GWT.create(NodeResource.class);

    private final RestFactory restFactory;
    private final TooltipPresenter tooltipPresenter;
    private final RestDataProvider<NodeStatusResult, FetchNodeStatusResponse> dataProvider;

    private final Map<String, PingResult> latestPing = new HashMap<>();

    @Inject
    public NodeMonitoringPresenter(final EventBus eventBus,
                                   final RestFactory restFactory,
                                   final TooltipPresenter tooltipPresenter) {
        super(eventBus, new DataGridViewImpl<>(true));
        this.restFactory = restFactory;
        this.tooltipPresenter = tooltipPresenter;
        initTableColumns();
        dataProvider = new RestDataProvider<NodeStatusResult, FetchNodeStatusResponse>(eventBus) {
            @Override
            protected void exec(final Consumer<FetchNodeStatusResponse> dataConsumer, final Consumer<Throwable> throwableConsumer) {
                final Rest<FetchNodeStatusResponse> rest = restFactory.create();
                rest.onSuccess(dataConsumer).onFailure(throwableConsumer).call(NODE_RESOURCE).find();
            }

            @Override
            protected void changeData(final FetchNodeStatusResponse data) {
                // Ping each node.
                data.getValues().forEach(row -> {
                    final String nodeName = row.getNode().getName();
                    final Rest<Long> rest = restFactory.create();
                    rest.onSuccess(ping -> {
                        latestPing.put(nodeName, new PingResult(ping, null));
                        super.changeData(data);
                    }).onFailure(throwable -> {
                        latestPing.put(nodeName, new PingResult(null, throwable.getMessage()));
                        super.changeData(data);
                    }).call(NODE_RESOURCE).ping(nodeName);
                });
                super.changeData(data);
            }
        };
        dataProvider.addDataDisplay(getView().getDataDisplay());
    }

    /**
     * Add the columns to the table.
     */
    private void initTableColumns() {
        // Info column.
        final InfoColumn<NodeStatusResult> infoColumn = new InfoColumn<NodeStatusResult>() {
            @Override
            protected void showInfo(final NodeStatusResult row, final int x, final int y) {
                final Rest<ClusterNodeInfo> rest = restFactory.create();
                rest
                        .onSuccess(result -> showNodeInfoResult(row.getNode(), result, x, y))
                        .onFailure(caught -> showNodeInfoError(caught, x, y))
                        .call(NODE_RESOURCE)
                        .info(row.getNode().getName());
            }
        };
        getView().addColumn(infoColumn, "<br/>", 20);

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
        getView().addResizableColumn(nameColumn, "Name", 100);

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
        getView().addResizableColumn(hostNameColumn, "Cluster Base Endpoint URL", 400);

        // Ping.
        final Column<NodeStatusResult, String> pingColumn = new Column<NodeStatusResult, String>(new TextCell()) {
            @Override
            public String getValue(final NodeStatusResult row) {
                if (row == null) {
                    return null;
                }

                final PingResult pingResult = latestPing.get(row.getNode().getName());
                if (pingResult != null) {
                    if ("No response".equals(pingResult.getError())) {
                        return pingResult.getError();
                    }
                    if (pingResult.getError() != null) {
                        return "Error";
                    }
                    return ModelStringUtil.formatDurationString(pingResult.getPing());
                }
                return "-";
            }
        };
        getView().addResizableColumn(pingColumn, "Ping", 150);

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
        getView().addColumn(masterColumn, "Master", 50);

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
        priorityColumn.setFieldUpdater((index, row, value) -> {
            final Rest<Node> rest = restFactory.create();
            rest.onSuccess(result -> refresh()).call(NODE_RESOURCE).setPriority(row.getNode().getName(), value.intValue());
        });
        getView().addColumn(priorityColumn, "Priority", 55);

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
        enabledColumn.setFieldUpdater((index, row, value) -> {
            final Rest<Node> rest = restFactory.create();
            rest.onSuccess(result -> refresh()).call(NODE_RESOURCE).setEnabled(row.getNode().getName(), value.toBoolean());
        });

        getView().addColumn(enabledColumn, "Enabled", 60);

        getView().addEndColumn(new EndColumn<>());
    }

    private void showNodeInfoResult(final Node node, final ClusterNodeInfo result, final int x, final int y) {
        final StringBuilder html = new StringBuilder();
        TooltipUtil.addHeading(html, "Node Details");

        if (result != null) {
            final BuildInfo buildInfo = result.getBuildInfo();
            TooltipUtil.addRowData(html, "Node Name", result.getNodeName(), true);
            if (buildInfo != null) {
                TooltipUtil.addRowData(html, "Build Version", buildInfo.getBuildVersion(), true);
                TooltipUtil.addRowData(html, "Build Date", buildInfo.getBuildDate(), true);
                TooltipUtil.addRowData(html, "Up Date", buildInfo.getUpDate(), true);
            }
            TooltipUtil.addRowData(html, "Discover Time", result.getDiscoverTime(), true);
            TooltipUtil.addRowData(html, "Node Endpoint URL", result.getEndpointUrl(), true);
            TooltipUtil.addRowData(html, "Ping", ModelStringUtil.formatDurationString(result.getPing()));
            TooltipUtil.addRowData(html, "Error", result.getError());

            TooltipUtil.addBreak(html);
            TooltipUtil.addHeading(html, "Node List");
            if (result.getItemList() != null) {
                for (final ClusterNodeInfo.ClusterNodeInfoItem info : result.getItemList()) {
                    html.append(info.getNodeName());
                    if (!info.isActive()) {
                        html.append(" (Unknown)");
                    }
                    if (info.isMaster()) {
                        html.append(" (Master)");
                    }
                    html.append("<br/>");
                }
            }

        } else {
            TooltipUtil.addRowData(html, "Node Name", node.getName(), true);
            TooltipUtil.addRowData(html, "Cluster URL", node.getUrl(), true);
        }

        tooltipPresenter.setHTML(html.toString());
        final PopupPosition popupPosition = new PopupPosition(x, y);
        ShowPopupEvent.fire(NodeMonitoringPresenter.this, tooltipPresenter, PopupType.POPUP,
                popupPosition, null);
    }

    private void showNodeInfoError(final Throwable caught, final int x, final int y) {
        tooltipPresenter.setHTML(caught.getMessage());
        final PopupPosition popupPosition = new PopupPosition(x, y);
        ShowPopupEvent.fire(NodeMonitoringPresenter.this, tooltipPresenter, PopupType.POPUP,
                popupPosition, null);
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
