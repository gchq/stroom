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
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.List;

/// The tools of the MCP tool server selected on a model's MCP Tool Servers tab: each tool's name and whether
/// the server's tool policies enable it, and the selected tool's documentation, as the server gives it.
public class McpToolListPresenter extends MyPresenterWidget<McpToolListPresenter.McpToolListView> {

    private static final OpenAIModelResource OPEN_AI_MODEL_RESOURCE = GWT.create(OpenAIModelResource.class);

    private final RestFactory restFactory;
    private final MarkdownConverter markdownConverter;
    private final MyDataGrid<McpToolDescription> dataGrid;
    private final MultiSelectionModelImpl<McpToolDescription> selectionModel;
    private final ListDataProvider<McpToolDescription> dataProvider = new ListDataProvider<>();
    private final ButtonView refreshButton;
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
                DataGridUtil.headingBuilder("Enabled")
                        .withToolTip("Whether the server's tool policies let the model use the tool.")
                        .centerAligned()
                        .build(),
                70);
        dataGrid.addAutoResizableColumn(
                DataGridUtil.<McpToolDescription>textColumnBuilder(McpToolDescription::getName).build(),
                DataGridUtil.headingBuilder("Tool").build(),
                200);
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
                                + " enabled by its tool policies.");
        }
    }

    private void setTools(final List<McpToolDescription> tools) {
        dataProvider.setCompleteList(new ArrayList<>(tools));
        if (tools.isEmpty()) {
            showDetails(null);
        } else {
            selectionModel.setSelected(tools.get(0));
            showDetails(tools.get(0));
        }
    }

    private void showDetails(final McpToolDescription tool) {
        final SafeHtml html = tool == null || NullSafe.isBlankString(tool.getDocumentation())
                ? SafeHtmlUtils.EMPTY_SAFE_HTML
                // The server writes the descriptions, so they are treated as untrusted.
                : markdownConverter.convertUntrustedMarkdownToHtml(tool.getDocumentation());
        getView().setDetails(html);
    }


    // --------------------------------------------------------------------------------


    public interface McpToolListView extends View {

        /// @return The tool strip's buttons.
        ButtonPanel getButtons();

        /// Shows the grid of tools.
        void setGrid(Widget widget);

        /// Shows what is being listed, how many tools there are, or what went wrong.
        void setStatus(String text);

        /// Shows the selected tool's documentation.
        void setDetails(SafeHtml html);
    }
}
