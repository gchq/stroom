/*
 * Copyright 2016-2026 Crown Copyright
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

import stroom.alert.client.event.ConfirmEvent;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.config.global.client.presenter.ListDataProvider;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocPresenter;
import stroom.openai.shared.McpToolServerConfig;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.svg.client.SvgPresets;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;

public class OpenAIModelMcpToolServersPresenter
        extends DocPresenter<PagerView, OpenAIModelDoc> {

    private final MyDataGrid<McpToolServerConfig> dataGrid;
    private final MultiSelectionModelImpl<McpToolServerConfig> selectionModel;
    private final Provider<McpToolServerEditPresenter> editPresenterProvider;

    private final ButtonView addButton;
    private final ButtonView editButton;
    private final ButtonView removeButton;
    private final ButtonView moveUpButton;
    private final ButtonView moveDownButton;

    private final List<McpToolServerConfig> servers = new ArrayList<>();
    private final ListDataProvider<McpToolServerConfig> dataProvider = new ListDataProvider<>();
    private boolean initialised;

    @Inject
    public OpenAIModelMcpToolServersPresenter(final EventBus eventBus,
                                              final PagerView view,
                                              final Provider<McpToolServerEditPresenter> editPresenterProvider) {
        super(eventBus, view);
        this.editPresenterProvider = editPresenterProvider;

        dataGrid = new MyDataGrid<>(this);
        dataGrid.setTableName("MCP Tool Servers");
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        view.setDataWidget(dataGrid);

        addButton = view.addButton(SvgPresets.ADD.title("Add tool server"));
        editButton = view.addButton(SvgPresets.EDIT.title("Edit tool server"));
        removeButton = view.addButton(SvgPresets.DELETE.title("Remove tool server"));
        moveUpButton = view.addButton(SvgPresets.UP.title("Move tool server up"));
        moveDownButton = view.addButton(SvgPresets.DOWN.title("Move tool server down"));

        initColumns();
        enableButtons();
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(addButton.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                onAdd();
            }
        }));
        registerHandler(editButton.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                onEdit();
            }
        }));
        registerHandler(removeButton.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                onRemove();
            }
        }));
        registerHandler(moveUpButton.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                onMove(-1);
            }
        }));
        registerHandler(moveDownButton.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                onMove(1);
            }
        }));
        registerHandler(selectionModel.addSelectionHandler(e -> {
            enableButtons();
            if (!isReadOnly() && e.getSelectionType().isDoubleSelect()) {
                onEdit();
            }
        }));
    }

    @Override
    protected void onRead(final DocRef docRef, final OpenAIModelDoc document, final boolean readOnly) {
        servers.clear();
        servers.addAll(document.getMcpToolServers());
        refresh();
        enableButtons();
    }

    @Override
    protected OpenAIModelDoc onWrite(final OpenAIModelDoc document) {
        return document.copy()
                .mcpToolServers(new ArrayList<>(servers))
                .build();
    }

    private void initColumns() {
        dataGrid.addColumn(
                DataGridUtil.<McpToolServerConfig>readOnlyTickBoxColumnBuilder(
                                TickBoxState.createTickBoxFunc(McpToolServerConfig::isEnabled))
                        .centerAligned()
                        .build(),
                DataGridUtil.headingBuilder("Enabled")
                        .centerAligned()
                        .build(),
                80);
        dataGrid.addResizableColumn(
                DataGridUtil.<McpToolServerConfig>textColumnBuilder(McpToolServerConfig::getName).build(),
                DataGridUtil.headingBuilder("Name").build(),
                200);
        dataGrid.addAutoResizableColumn(
                DataGridUtil.<McpToolServerConfig>textColumnBuilder(McpToolServerConfig::getEndpoint).build(),
                DataGridUtil.headingBuilder("Endpoint").build(),
                300);
        dataGrid.addResizableColumn(
                DataGridUtil.<McpToolServerConfig>textColumnBuilder(
                        OpenAIModelMcpToolServersPresenter::authModeText).build(),
                DataGridUtil.headingBuilder("Authentication").build(),
                150);
    }

    private static String authModeText(final McpToolServerConfig server) {
        return server.getAuthMode() == null
                ? ""
                : server.getAuthMode().getDisplayValue();
    }

    private void refresh() {
        if (!initialised) {
            initialised = true;
            dataProvider.addDataDisplay(dataGrid);
        }
        dataProvider.setCompleteList(new ArrayList<>(servers));
    }

    private void enableButtons() {
        final boolean readOnly = isReadOnly();
        final McpToolServerConfig selected = selectionModel.getSelected();
        final int index = selected == null
                ? -1
                : servers.indexOf(selected);
        addButton.setEnabled(!readOnly);
        editButton.setEnabled(selected != null);
        removeButton.setEnabled(!readOnly && selected != null);
        moveUpButton.setEnabled(!readOnly && index > 0);
        moveDownButton.setEnabled(!readOnly && index >= 0 && index < servers.size() - 1);
    }

    private void onAdd() {
        if (isReadOnly()) {
            return;
        }
        editPresenterProvider.get().show(
                McpToolServerConfig.builder().name("New tool server").build(),
                false,
                updated -> {
                    servers.add(updated);
                    refresh();
                    selectionModel.setSelected(updated);
                    enableButtons();
                    onChange();
                });
    }

    private void onEdit() {
        final McpToolServerConfig selected = selectionModel.getSelected();
        if (selected == null) {
            return;
        }
        final int index = servers.indexOf(selected);
        editPresenterProvider.get().show(selected, isReadOnly(), updated -> {
            if (!isReadOnly() && index >= 0 && !updated.equals(selected)) {
                servers.set(index, updated);
                refresh();
                selectionModel.setSelected(updated);
                enableButtons();
                onChange();
            }
        });
    }

    private void onRemove() {
        final McpToolServerConfig selected = selectionModel.getSelected();
        if (isReadOnly() || selected == null) {
            return;
        }
        ConfirmEvent.fire(this,
                "Are you sure you want to remove tool server '"
                + NullSafe.nonBlankStringElse(selected.getName(), selected.getEndpoint()) + "'?",
                ok -> {
                    if (ok) {
                        servers.remove(selected);
                        selectionModel.clear();
                        refresh();
                        enableButtons();
                        onChange();
                    }
                });
    }

    private void onMove(final int direction) {
        final McpToolServerConfig selected = selectionModel.getSelected();
        if (isReadOnly() || selected == null) {
            return;
        }
        final int index = servers.indexOf(selected);
        final int target = index + direction;
        if (index < 0 || target < 0 || target >= servers.size()) {
            return;
        }
        servers.remove(index);
        servers.add(target, selected);
        refresh();
        selectionModel.setSelected(selected);
        enableButtons();
        onChange();
    }
}
