/*
 * Copyright 2026 Crown Copyright
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

import stroom.cell.info.client.InfoColumn;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.cell.valuespinner.shared.EditableInteger;
import stroom.data.client.ColumnSizeConstants;
import stroom.node.shared.FindNodeStatusCriteria;
import stroom.node.shared.Node;
import stroom.node.shared.NodeStatusResult;
import stroom.task.client.TaskMonitorFactory;
import stroom.ui.config.shared.NodeMonitoringConfig;
import stroom.util.client.DataGridUtil;
import stroom.util.client.DelayedUpdate;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ToolbarToggleButton;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.popup.client.PopupPosition;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.MultiSelectionModelImpl;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.i18n.client.NumberFormat;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.ColumnSortList;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.AbstractDataProvider;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/// The cluster's nodes, a row each (its info, name, endpoint, version, a bar of its latest ping,
/// when it started, whether it is the master, its priority and whether it is enabled); one can be
/// selected. Auto refresh can be turned off.
public class NodeStatusList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final NumberFormat THOUSANDS_FORMATTER = NumberFormat.getFormat("#,###");
    private static final String CLASS_BASE = "node-ping-bar";
    private static final String PING_BAR_CLASS = CLASS_BASE + "__bar";
    private static final String PING_TEXT_CLASS = CLASS_BASE + "__text";
    private static final String PING_ERROR_TEXT_CLASS = CLASS_BASE + "__error-text";
    private static final String PING_SEVERITY_CLASS_BASE = PING_BAR_CLASS + "--";
    private static final String PING_SEVERITY_CLASS_SUFFIX_HEALTHY = "healthy";
    private static final String PING_SEVERITY_CLASS_SUFFIX_WARN = "warn";
    private static final String PING_SEVERITY_CLASS_SUFFIX_MAX = "max";

    @UiField
    PagerPanel pager;
    @UiField
    ToolbarToggleButton autoRefreshButton;
    @UiField
    DataGrid<NodeStatusResult> dataGrid;

    private final MultiSelectionModelImpl<NodeStatusResult> selectionModel;
    // Saves the grid being redrawn for every single row in the list
    private final DelayedUpdate redrawDelayedUpdate;
    private Function<String, NodePing> pingLookup = nodeName -> null;
    private Function<Long, String> dateFormat = String::valueOf;
    private InfoHandler onShowInfo = (row, popupPosition, anchor) -> {
    };
    private BiConsumer<NodeStatusResult, Integer> onPriorityChange = (row, priority) -> {
    };
    private BiConsumer<NodeStatusResult, Boolean> onEnabledChange = (row, enabled) -> {
    };
    private Runnable onSortChange = () -> {
    };

    /// Makes an empty list.
    public NodeStatusList() {
        initWidget(BINDER.createAndBindUi(this));
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        redrawDelayedUpdate = new DelayedUpdate(dataGrid::redraw);
        addColumns();
        dataGrid.addColumnSortHandler(event -> onSortChange.run());
    }

    /// @param dataProvider Gives the list its rows.
    public void setDataProvider(final AbstractDataProvider<NodeStatusResult> dataProvider) {
        dataProvider.addDataDisplay(dataGrid);
    }

    /// @return The task monitor that spins the pager's refresh button.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }

    /// @return The selection, one node at most.
    public MultiSelectionModelImpl<NodeStatusResult> getSelectionModel() {
        return selectionModel;
    }

    /// @return How the rows are sorted, for the request for them.
    public ColumnSortList getSortList() {
        return dataGrid.getColumnSortList();
    }

    /// Draws the rows again soon, e.g. as each node's ping comes in, once for them all.
    public void scheduleRedraw() {
        redrawDelayedUpdate.update();
    }

    /// Cancels a redraw [#scheduleRedraw()] asked for that hasn't happened yet.
    public void cancelRedraw() {
        redrawDelayedUpdate.reset();
    }

    /// @param pingLookup Gives a node's latest ping, or `null` if it hasn't one yet.
    public void setPingLookup(final Function<String, NodePing> pingLookup) {
        this.pingLookup = pingLookup;
    }

    /// @param dateFormat Formats a time with how long ago it was, in the user's preferences.
    public void setDateFormat(final Function<Long, String> dateFormat) {
        this.dateFormat = dateFormat;
    }

    /// @param onAutoRefreshToggle Given whether auto refresh is now on, when its button is pressed.
    public void setOnAutoRefreshToggle(final Consumer<Boolean> onAutoRefreshToggle) {
        autoRefreshButton.setOnToggle(onAutoRefreshToggle);
    }

    /// @param onShowInfo Shows a node's info, by the info icon pressed.
    public void setOnShowInfo(final InfoHandler onShowInfo) {
        this.onShowInfo = onShowInfo;
    }

    /// @param onPriorityChange Given a node and its new priority.
    public void setOnPriorityChange(final BiConsumer<NodeStatusResult, Integer> onPriorityChange) {
        this.onPriorityChange = onPriorityChange;
    }

    /// @param onEnabledChange Given a node and whether it is now enabled, when its tick box is pressed.
    public void setOnEnabledChange(final BiConsumer<NodeStatusResult, Boolean> onEnabledChange) {
        this.onEnabledChange = onEnabledChange;
    }

    /// @param onSortChange Run when the user sorts by another column.
    public void setOnSortChange(final Runnable onSortChange) {
        this.onSortChange = onSortChange;
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
                dateFormat);
    }

    private void addColumns() {
        // Info column.
        final InfoColumn<NodeStatusResult> infoColumn = new InfoColumn<NodeStatusResult>() {
            @Override
            protected void showInfo(final NodeStatusResult row,
                                    final PopupPosition popupPosition,
                                    final Element anchor) {
                onShowInfo.show(row, popupPosition, anchor);
            }
        };
        dataGrid.addColumn(infoColumn, "<br/>", ColumnSizeConstants.ICON_COL);

        // Name.
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(DataGridUtil.toStringFunc(NodeStatusResult::getNode, Node::getName))
                        .enabledWhen(NodeStatusList::isNodeEnabled)
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
                        .enabledWhen(NodeStatusList::isNodeEnabled)
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
                        .enabledWhen(NodeStatusList::isNodeEnabled)
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
                        .enabledWhen(NodeStatusList::isNodeEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Ping (ms)")
                        .withToolTip("The time in milliseconds to get a response back from the node.")
                        .build(),
                250);

        // Last Boot Time
        dataGrid.addColumn(
                DataGridUtil.textColumnBuilder(this::extractLastBootTimeAsStr)
                        .enabledWhen(NodeStatusList::isNodeEnabled)
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
                        .enabledWhen(NodeStatusList::isNodeEnabled)
                        .centerAligned()
                        .build(),
                DataGridUtil.headingBuilder("Master")
                        .withToolTip("Whether the node has been assigned as the master node for the cluster.")
                        .centerAligned()
                        .build(),
                60);

        // Priority.
        dataGrid.addColumn(
                DataGridUtil.valueSpinnerColumnBuilder("Priority", NodeStatusList::extractNodePriority,
                                1L,
                                100L)
                        .enabledWhen(NodeStatusList::isNodeEnabled)
                        .withSorting(FindNodeStatusCriteria.FIELD_ID_PRIORITY)
                        .withFieldUpdater((rowIndex, nodeStatusResult, value) ->
                                onPriorityChange.accept(nodeStatusResult, value.intValue()))
                        .build(),
                DataGridUtil.headingBuilder("Priority")
                        .withToolTip("Defines how important the node is when it comes to " +
                                     "assigning a master. The larger the number the more chance it will become " +
                                     "the master.")
                        .build(),
                ColumnSizeConstants.VALUE_SPINNER_COL);

        // Enabled
        dataGrid.addColumn(
                DataGridUtil.updatableTickBoxColumnBuilder(TickBoxState.createTickBoxFunc(
                                (NodeStatusResult result) -> NullSafe.get(
                                        result,
                                        NodeStatusResult::getNode,
                                        Node::isEnabled)))
                        .enabledWhen(NodeStatusList::isNodeEnabled)
                        .withSorting(FindNodeStatusCriteria.FIELD_ID_ENABLED)
                        .withFieldUpdater((index, row, value) -> {
                            if (row != null) {
                                onEnabledChange.accept(row, value.toBoolean());
                            }
                        })
                        .centerAligned()
                        .build(),
                DataGridUtil.headingBuilder("Enabled")
                        .withToolTip("Whether the node is enabled for processing and background jobs.")
                        .centerAligned()
                        .build(),
                ColumnSizeConstants.ENABLED_COL);
    }

    private SafeHtml getPingBarSafeHtml(final NodeStatusResult row) {
        if (row == null) {
            return SafeHtmlUtils.EMPTY_SAFE_HTML;
        }

        final Node node = row.getNode();
        final NodePing pingResult = pingLookup.apply(node.getName());

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
                          + Objects.requireNonNullElse(ping, "null");
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

        // The bar's width is known only now, so set as a custom property that its class uses
        final Attribute barWidthAttr = Attribute.style("--node-ping-bar__width:" + barWidthPct + "%");
        final Attribute textClass = Attribute.className(PING_TEXT_CLASS);
        final Attribute errorTextClass = Attribute.className(PING_ERROR_TEXT_CLASS);
        final Attribute barClass = Attribute.className(PING_BAR_CLASS + " " + severityClass);
        final Attribute barWrapperClass = Attribute.className(PING_BAR_CLASS + "-wrapper");
        final Attribute titleAttr = Attribute.title(title);
        final Attribute outerClass = Attribute.className(CLASS_BASE);

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


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, NodeStatusList> {

    }

    /// Shows a node's info.
    public interface InfoHandler {

        /// @param row           The node's row.
        /// @param popupPosition Where to show it.
        /// @param anchor        The info icon pressed.
        void show(NodeStatusResult row, PopupPosition popupPosition, Element anchor);
    }
}
