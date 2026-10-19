/*
 * Copyright 2016 Crown Copyright
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

/// The pipeline structure editor's tree of elements.
///
/// It is a tree for assistive technology (`role="tree"`, see [PipelineElementRenderer]) and for
/// the keyboard (see [TreeKeyboard]), whose elements can be dragged to another parent or moved with
/// the keyboard where it may be changed.
public class PipelineTreeViewImpl extends ViewWithUiHandlers<PipelineTreeUiHandlers> implements PipelineTreeView {

    private final PipelineTreePanel treePanel;
    private final PipelineTreePanel subTreePanel;
    private final DraggableTreePanel<PipelineElement> layoutPanel;
    private final TreeKeyboard<PipelineElement> keyboard;
    private SelectionModel<PipelineElement> selectionModel;
    private boolean allowNullSelection = true;
    private PipelineModel pipelineModel;

    @Inject
    public PipelineTreeViewImpl() {
        treePanel = new PipelineTreePanel();
        subTreePanel = new PipelineTreePanel();
        // The copy of what is dragged, which follows the pointer, is for sight only: not a second
        // tree for assistive technology, nor focusable
        subTreePanel.getElement().setAttribute("aria-hidden", "true");
        subTreePanel.getElement().setAttribute("inert", "");

        layoutPanel = new DraggableTreePanel<>(treePanel, subTreePanel) {
            @Override
            protected boolean isValidTarget(final PipelineElement parent, final PipelineElement child) {
                return canTake(parent, child);
            }

            @Override
            protected void startDragging(final PipelineElement parent, final PipelineElement child) {
                // Dragging replaces moving with the keyboard
                keyboard.cancelDetach();
            }

            @Override
            protected void endDragging(final PipelineElement parent, final PipelineElement child) {
                if (getUiHandlers() != null) {
                    getUiHandlers().onMove(parent, child);
                }
            }

            @Override
            protected void setSelected(final PipelineElement item) {
                if (item != null) {
                    // A clicked element has the focus too, so that the keyboard carries on from it
                    keyboard.moveTo(item);
                } else if (allowNullSelection) {
                    selectionModel.setSelected(null, true);
                }
            }
        };
        // The order of an element's children doesn't matter (the model moves nothing), so dropping
        // an element where it came from leaves it as it was
        layoutPanel.setPutBackOnOwnParent(true);
        layoutPanel.setWidth("100%");
        layoutPanel.setHeight("100%");

        keyboard = new TreeKeyboard<>(treePanel, layoutPanel, "pipeline-tree-status", "element", "elements") {
            @Override
            protected String getName(final PipelineElement element) {
                return element.getDisplayName();
            }

            @Override
            protected boolean canTake(final PipelineElement parent, final PipelineElement child) {
                return PipelineTreeViewImpl.this.canTake(parent, child);
            }

            @Override
            protected void move(final PipelineElement parent, final PipelineElement child) {
                if (getUiHandlers() != null) {
                    getUiHandlers().onMove(parent, child);
                }
            }

            @Override
            protected void remove(final PipelineElement element) {
                if (getUiHandlers() != null) {
                    getUiHandlers().onRemove(element);
                }
            }
        };
        // A detached element and those after it are faded, with the connectors into them
        treePanel.setFaded(keyboard::isDetached);
    }

    @Override
    public void setPipelineModel(final PipelineModel pipelineModel) {
        this.pipelineModel = pipelineModel;
        keyboard.cancelDetach();
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
        keyboard.setSelectionModel(selectionModel);
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
        keyboard.setAllowMoving(allowDragging);
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
    public void setDisabledElements(final List<PipelineElement> disabledElements) {
        treePanel.setDisabledElements(disabledElements);
    }

    @Override
    public void setSeverities(final Map<String, Severity> elementIdToSeveritiesMap) {
        // The structure editor doesn't show problems (the stepping screen's pipeline bar does)
    }

    @Override
    public PipelineElement getDetached() {
        return keyboard.getDetached();
    }

    @Override
    public void detach(final PipelineElement element) {
        keyboard.detach(element);
    }

    @Override
    public boolean canAttach(final PipelineElement target) {
        return keyboard.canAttach(target);
    }

    @Override
    public void attach(final PipelineElement target) {
        keyboard.attach(target);
    }

    @Override
    public void cancelDetach() {
        keyboard.cancelDetach();
    }

    // Whether a parent can take a child, by their types and the parent's children
    private boolean canTake(final PipelineElement parent, final PipelineElement child) {
        final PipelineElementType parentType = pipelineModel.getElementType(parent);
        final PipelineElementType childType = pipelineModel.getElementType(child);
        if (parentType == null || childType == null) {
            return false;
        }
        final List<PipelineElement> children = treePanel.getTree().getChildren(parent);
        final int childCount = children == null
                ? 0
                : children.size();
        return StructureValidationUtil.isValidChildType(parentType, childType, childCount);
    }
}
