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

import stroom.dispatch.client.RestFactory;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.FetchExplorerNodeResult;
import stroom.widget.util.client.MultiSelectionModelImpl;
import stroom.widget.util.client.SelectionType;

import com.google.gwt.view.client.CellPreviewEvent;

import java.util.Set;

public class ExplorerTickBoxTree extends AbstractExplorerTree {

    private TickBoxSelectionModel tickBoxSelectionModel;

    public ExplorerTickBoxTree(final RestFactory restFactory) {
        super(restFactory, false);
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
        tickBoxSelectionModel.setRoots(result.getRootNodes());
        super.onData(result);
    }

    @Override
    void showMenu(final CellPreviewEvent<ExplorerNode> e) {
        // Ignore.
    }

    @Override
    void doSelect(final ExplorerNode row, final SelectionType selectionType) {
        super.doSelect(row, selectionType);
        toggleSelection(row);
        refresh();
    }

    public void setSelected(final ExplorerNode explorerNode, final boolean selected) {
        tickBoxSelectionModel.setSelected(explorerNode, selected);
        refresh();
    }

    public Set<ExplorerNode> getSelectedSet() {
        return tickBoxSelectionModel.getSelectedSet();
    }

    private void toggleSelection(final ExplorerNode selection) {
        if (selection != null) {
            tickBoxSelectionModel.setSelected(selection, !tickBoxSelectionModel.isSelected(selection));
        }
    }
}
