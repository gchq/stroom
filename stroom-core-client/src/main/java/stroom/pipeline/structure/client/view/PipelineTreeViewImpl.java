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
 */

package stroom.pipeline.structure.client.view;

import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.structure.client.presenter.PipelineModel;
import stroom.pipeline.structure.client.presenter.PipelineTreePresenter.PipelineTreeView;
import stroom.pipeline.structure.client.presenter.PipelineTreeUiHandlers;
import stroom.pipeline.structure.client.presenter.StructureValidationUtil;
import stroom.util.shared.Severity;
import stroom.widget.contextmenu.client.event.ContextMenuEvent.Handler;
import stroom.widget.htree.client.treelayout.util.DefaultTreeForTreeLayout;

import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.SelectionModel;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.List;
import java.util.Map;

public class PipelineTreeViewImpl extends ViewWithUiHandlers<PipelineTreeUiHandlers> implements PipelineTreeView {

    private final PipelineTreePanel treePanel;
    private final PipelineTreePanel subTreePanel;
    private final DraggableTreePanel<PipelineElement> layoutPanel;
    private SelectionModel<PipelineElement> selectionModel;
    private boolean allowNullSelection = true;
    private PipelineModel pipelineModel;

    @Inject
    public PipelineTreeViewImpl() {
        treePanel = new PipelineTreePanel();
        subTreePanel = new PipelineTreePanel();

        layoutPanel = new DraggableTreePanel<PipelineElement>(treePanel, subTreePanel) {
            @Override
            protected boolean isValidTarget(final PipelineElement parent, final PipelineElement child) {
                final PipelineElementType parentType = pipelineModel.getElementType(parent);
                final PipelineElementType childType = pipelineModel.getElementType(child);
                if (parentType == null || childType == null) {
                    return false;
                }

                int childCount = 0;

                final List<PipelineElement> children = treePanel.getTree().getChildren(parent);
                if (children != null) {
                    childCount = children.size();
                }

                return StructureValidationUtil.isValidChildType(parentType, childType, childCount);
            }

            @Override
            protected void endDragging(final PipelineElement parent, final PipelineElement child) {
                if (getUiHandlers() != null) {
                    getUiHandlers().onMove(parent, child);
                }
            }

            @Override
            protected void setSelected(final PipelineElement item) {
                if (allowNullSelection || item != null) {
                    selectionModel.setSelected(item, true);
                }
            }
        };
        layoutPanel.setWidth("100%");
        layoutPanel.setHeight("100%");
    }

    @Override
    public void setPipelineModel(final PipelineModel pipelineModel) {
        this.pipelineModel = pipelineModel;
        treePanel.setPipelineModel(pipelineModel);
        subTreePanel.setPipelineModel(pipelineModel);
    }

    @Override
    public void setTree(final DefaultTreeForTreeLayout<PipelineElement> tree) {
        treePanel.setTree(tree);
    }

    @Override
    public void setSelectionModel(final SelectionModel<PipelineElement> selectionModel) {
        this.selectionModel = selectionModel;
        treePanel.setSelectionModel(selectionModel);
    }

    @Override
    public void refresh() {
        treePanel.refresh();
    }

    @Override
    public Widget asWidget() {
        return layoutPanel;
    }

    @Override
    public void setAllowDragging(final boolean allowDragging) {
        layoutPanel.setAllowDragging(allowDragging);
    }

    @Override
    public void setAllowNullSelection(final boolean allowNullSelection) {
        this.allowNullSelection = allowNullSelection;
    }

    @Override
    public HandlerRegistration addContextMenuHandler(final Handler handler) {
        return layoutPanel.addContextMenuHandler(handler);
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        layoutPanel.fireEvent(event);
    }

    @Override
    public int getTreeHeight() {
        return treePanel.getTreeHeight();
    }

    @Override
    public void setSeverities(final Map<String, Severity> elementIdToSeveritiesMap) {
        treePanel.setSeverities(elementIdToSeveritiesMap);
    }
}
