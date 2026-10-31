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

package stroom.openai.client.presenter;

import stroom.ai.client.AiChatTexts;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.config.global.client.presenter.ListDataProvider;
import stroom.data.grid.client.MyDataGrid;
import stroom.dispatch.client.RestFactory;
import stroom.entity.client.presenter.MarkdownConverter;
import stroom.openai.shared.ListMcpToolsResponse;
import stroom.openai.shared.McpToolDescription;
import stroom.openai.shared.McpToolServerConfig;
import stroom.openai.shared.OpenAIModelResource;
import stroom.openai.shared.TestMcpToolServerRequest;
import stroom.svg.client.SvgPresets;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ButtonPanel;
import stroom.widget.button.client.ButtonView;
import stroom.widget.dropdowntree.client.view.QuickFilter;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.ColumnSortList;
import com.google.gwt.user.cellview.client.ColumnSortList.ColumnSortInfo;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/// The tools of the MCP tool server selected on a model's MCP Tool Servers tab: each tool, whether the server's tool
/// policies allow it, its group and what it does, sortable and filtered as the user types; and below, the selected
/// tool's documentation, as the server gives it.
public class McpToolListPresenter extends MyPresenterWidget<McpToolListPresenter.McpToolListView> {

    private static final OpenAIModelResource OPEN_AI_MODEL_RESOURCE = GWT.create(OpenAIModelResource.class);
    private static final String TOOL_NAME = "Tool Name";
    private static final String TOOL_GROUP = "Tool Group";
    private static final String TYPE = "Type";

    private final RestFactory restFactory;
    private final MarkdownConverter markdownConverter;
    private final MyDataGrid<McpToolDescription> dataGrid;
    private final MultiSelectionModelImpl<McpToolDescription> selectionModel;
    private final ListDataProvider<McpToolDescription> dataProvider = new ListDataProvider<>();
    private final ButtonView refreshButton;
    private final Column<McpToolDescription, String> nameColumn;
    private final Column<McpToolDescription, String> groupColumn;
    private final Column<McpToolDescription, String> typeColumn;
    // All the server's tools, before filtering and sorting.
    private List<McpToolDescription> tools = List.of();
    private String modelUuid;
    private McpToolServerConfig server;
    // Counts requests, so a slow answer for a server no longer selected is ignored.
    private int request;

    @Inject
    public McpToolListPresenter(final EventBus eventBus,
                                final McpToolListView view,
                                final RestFactory restFactory,
                                final MarkdownConverter markdownConverter) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.markdownConverter = markdownConverter;

        dataGrid = new MyDataGrid<>(this);
        dataGrid.setTableName("MCP Tools");
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        dataGrid.addColumn(
                DataGridUtil.<McpToolDescription>readOnlyTickBoxColumnBuilder(
                                TickBoxState.createTickBoxFunc(McpToolDescription::isEnabled))
                        .centerAligned()
                        .build(),
                DataGridUtil.headingBuilder("Allowed")
                        .withToolTip("Whether the server's tool policies let the model use the tool.")
                        .centerAligned()
                        .build(),
                70);
        nameColumn = DataGridUtil.<McpToolDescription>textColumnBuilder(McpToolDescription::getName)
                .withSorting(TOOL_NAME)
                .build();
        dataGrid.addResizableColumn(nameColumn, DataGridUtil.headingBuilder(TOOL_NAME).build(), 220);
        groupColumn = DataGridUtil.<McpToolDescription>textColumnBuilder(tool ->
                        NullSafe.string(tool.getGroup()))
                .withSorting(TOOL_GROUP)
                .build();
        dataGrid.addResizableColumn(groupColumn, DataGridUtil.headingBuilder(TOOL_GROUP)
                .withToolTip("The group the server puts the tool in, if any.")
                .build(), 150);
        typeColumn = DataGridUtil.<McpToolDescription>textColumnBuilder(McpToolDescription::getType)
                .withSorting(TYPE)
                .build();
        dataGrid.addAutoResizableColumn(typeColumn, DataGridUtil.headingBuilder(TYPE)
                .withToolTip("What the server says the tool does.")
                .build(), 200);
        // Alphabetical by name, until the user sorts by another column.
        dataGrid.sort(nameColumn);
        dataProvider.addDataDisplay(dataGrid);
        view.setGrid(dataGrid);

        refreshButton = view.getButtons().addButton(SvgPresets.REFRESH_BLUE.title("List the server's tools again"));
        refreshButton.setEnabled(false);
        show(null, null);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(refreshButton.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                refresh();
            }
        }));
        registerHandler(selectionModel.addSelectionHandler(e -> showDetails(selectionModel.getSelected())));
        registerHandler(dataGrid.addColumnSortHandler(e -> showTools()));
        registerHandler(getView().getFilter().addValueChangeHandler(e -> showTools()));
    }

    /// Lists a server's tools, or none.
    ///
    /// @param modelUuid The model the server belongs to.
    /// @param server    The server, as it is set up now (perhaps not saved yet), or null for none.
    public void show(final String modelUuid, final McpToolServerConfig server) {
        this.modelUuid = modelUuid;
        this.server = server;
        refreshButton.setEnabled(server != null);
        if (server == null) {
            request++;
            setTools(List.of());
            getView().setStatus("Select a tool server to see its tools.");
            return;
        }
        refresh();
    }

    private void refresh() {
        if (server == null) {
            return;
        }
        final int thisRequest = ++request;
        final McpToolServerConfig listed = server;
        setTools(List.of());
        getView().setStatus("Listing the tools of " + listed.getName() + "...");
        restFactory
                .create(OPEN_AI_MODEL_RESOURCE)
                .method(res -> res.listMcpTools(new TestMcpToolServerRequest(modelUuid, listed)))
                .onSuccess(response -> {
                    if (thisRequest == request) {
                        showResponse(listed, response);
                    }
                })
                .onFailure(error -> {
                    if (thisRequest == request) {
                        getView().setStatus("The tools couldn't be listed: " + error.getMessage());
                    }
                })
                .taskMonitorFactory(this)
                .exec();
    }

    private void showResponse(final McpToolServerConfig listed, final ListMcpToolsResponse response) {
        final List<McpToolDescription> tools = NullSafe.list(response.getTools());
        setTools(tools);
        if (response.getError() != null) {
            getView().setStatus(response.getError());
        } else {
            final long enabled = tools.stream().filter(McpToolDescription::isEnabled).count();
            getView().setStatus(listed.getName() + " has " + tools.size() + " tool(s), " + enabled
                                + " allowed by its tool policies.");
        }
    }

    private void setTools(final List<McpToolDescription> tools) {
        this.tools = List.copyOf(tools);
        showTools();
    }

    /// Shows the tools that match the filter, sorted as chosen, keeping the selected one if it is still shown.
    private void showTools() {
        final McpToolDescription selected = selectionModel.getSelected();
        final List<McpToolDescription> shown = new ArrayList<>();
        for (final McpToolDescription tool : tools) {
            if (matches(tool, getView().getFilter().getText())) {
                shown.add(tool);
            }
        }
        shown.sort(comparator());
        dataProvider.setCompleteList(shown);
        final McpToolDescription show = shown.contains(selected)
                ? selected
                : (shown.isEmpty()
                        ? null
                        : shown.get(0));
        if (show != null) {
            selectionModel.setSelected(show);
        }
        showDetails(show);
    }

    /// @return True if the tool's name, group or type contains every word of the filter, ignoring case.
    static boolean matches(final McpToolDescription tool, final String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }
        final String text = (tool.getName() + " " + NullSafe.string(tool.getGroup()) + " " + tool.getType())
                .toLowerCase();
        for (final String word : filter.toLowerCase().trim().split("\\s+")) {
            if (!text.contains(word)) {
                return false;
            }
        }
        return true;
    }

    private Comparator<McpToolDescription> comparator() {
        final ColumnSortList sortList = dataGrid.getColumnSortList();
        final ColumnSortInfo sort = sortList.size() > 0
                ? sortList.get(0)
                : null;
        final Function<McpToolDescription, String> key;
        if (sort != null && sort.getColumn() == groupColumn) {
            key = tool -> NullSafe.string(tool.getGroup());
        } else if (sort != null && sort.getColumn() == typeColumn) {
            key = McpToolDescription::getType;
        } else {
            key = McpToolDescription::getName;
        }
        // Ties are broken by name.
        final Comparator<McpToolDescription> comparator = Comparator.comparing(key, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(McpToolDescription::getName, String.CASE_INSENSITIVE_ORDER);
        return sort == null || sort.isAscending()
                ? comparator
                : comparator.reversed();
    }

    /// Shows the tool's name with its classifications, as on the chat's approval cards, then its documentation.
    private void showDetails(final McpToolDescription tool) {
        if (tool == null) {
            getView().setDetails(SafeHtmlUtils.EMPTY_SAFE_HTML);
            return;
        }
        final HtmlBuilder hb = new HtmlBuilder();
        hb.div(heading -> {
            heading.span(name -> name.append(tool.getName()), Attribute.className("mcpToolList-name"));
            for (final String classification : tool.getClassifications()) {
                final String explanation = AiChatTexts.riskExplanation(classification);
                final Attribute className = Attribute.className("read-only".equals(classification)
                        ? "ai-risk-badge ai-risk-badge--safe"
                        : "ai-risk-badge");
                if (explanation != null) {
                    heading.span(pill -> pill.append(classification), className, Attribute.title(explanation));
                } else {
                    heading.span(pill -> pill.append(classification), className);
                }
            }
        }, Attribute.className("mcpToolList-heading"));
        if (NullSafe.isNonBlankString(tool.getDocumentation())) {
            // The server writes the descriptions, so they are treated as untrusted.
            hb.append(markdownConverter.convertUntrustedMarkdownToHtml(tool.getDocumentation()));
        }
        getView().setDetails(hb.toSafeHtml());
    }


    // --------------------------------------------------------------------------------


    public interface McpToolListView extends View {

        /// @return The tool strip's buttons.
        ButtonPanel getButtons();

        /// @return The box for filtering the tools as the user types.
        QuickFilter getFilter();

        /// Shows the grid of tools.
        void setGrid(Widget widget);

        /// Shows what is being listed, how many tools there are, or what went wrong.
        void setStatus(String text);

        /// Shows the selected tool's documentation.
        void setDetails(SafeHtml html);
    }
}
