/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.explorer.client.presenter;

import stroom.cell.tickbox.shared.TickBoxState;
import stroom.dispatch.client.RestFactory;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.FetchExplorerNodeResult;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.util.client.MultiSelectionModelImpl;
import stroom.widget.util.client.SelectionType;

import com.google.gwt.view.client.CellPreviewEvent;

import java.util.List;
import java.util.Set;

public class ExplorerTickBoxTree extends AbstractExplorerTree {

    private TickBoxSelectionModel tickBoxSelectionModel;
    private List<ExplorerNode> rootNodes;

    public ExplorerTickBoxTree(final RestFactory restFactory,
                               final TaskMonitorFactory taskMonitorFactory) {
        super(restFactory, taskMonitorFactory, false, true);
        super.setSettingOfInitialSelectionState(false);
    }

    @Override
    MultiSelectionModelImpl<ExplorerNode> getSelectionModel() {
        return null;
    }

    @Override
    TickBoxSelectionModel getTickBoxSelectionModel() {
        if (tickBoxSelectionModel == null) {
            tickBoxSelectionModel = new TickBoxSelectionModel();
        }
        return tickBoxSelectionModel;
    }

    @Override
    void onData(final FetchExplorerNodeResult result) {
//        GwtLogUtil.log(ExplorerTickBoxTree.class, "onData()");
        final List<ExplorerNode> rootNodes = result.getRootNodes();
        this.rootNodes = rootNodes;
        this.tickBoxSelectionModel.setRoots(rootNodes);
        super.onData(result);
    }

    @Override
    void showMenu(final CellPreviewEvent<ExplorerNode> e) {
        // Ignore.
    }

    @Override
    void doSelect(final ExplorerNode row, final SelectionType selectionType) {
        super.doSelect(row, selectionType);
//        GwtLogUtil.log(
//                ExplorerTickBoxTree.class,
//                "doSelect() - row: {}, selectionType: {}",
//                row, selectionType);
        toggleSelection(row);
//        setSelected(row, true);
        refresh(false);
    }

    @Override
    void selectAll() {
//        GwtLogUtil.log(ExplorerTickBoxTree.class, "selectAll()");
        if (rootNodes != null && rootNodes.size() == 1) {
            final ExplorerNode rootNode = rootNodes.get(0);
            toggleSelection(rootNode);
            refresh(false);
        }
    }

    public void setSelected(final ExplorerNode explorerNode, final boolean selected) {
//        GwtLogUtil.log(
//                ExplorerTickBoxTree.class,
//                "setSelected() - explorerNode: {}, selected: {}",
//                explorerNode, selected);
        tickBoxSelectionModel.setSelected(explorerNode, selected);
        refresh(false);
    }

    public Set<ExplorerNode> getSelectedSet() {
        return tickBoxSelectionModel.getSelectedSet();
    }

    private void toggleSelection(final ExplorerNode selection) {
//        GwtLogUtil.log(ExplorerTickBoxTree.class, "toggleSelection() - selection: {}", selection);
        if (selection != null) {
            tickBoxSelectionModel.setSelected(
                    selection,
                    tickBoxSelectionModel.getState(selection) != TickBoxState.TICK);
        }
    }
}
