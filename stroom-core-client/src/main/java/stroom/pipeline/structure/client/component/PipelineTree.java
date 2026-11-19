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

package stroom.pipeline.structure.client.component;

import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.structure.client.PipelineModel;
import stroom.pipeline.structure.client.StructureValidationUtil;
import stroom.util.shared.Severity;
import stroom.widget.contextmenu.client.event.ContextMenuEvent.Handler;
import stroom.widget.htree.client.DraggableTree;
import stroom.widget.htree.client.TreeKeyboard;
import stroom.widget.htree.client.treelayout.util.DefaultTreeForTreeLayout;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/// The pipeline structure editor's tree of elements.
///
/// It is a tree for assistive technology (`role="tree"`, see [PipelineElementRenderer]) and for
/// the keyboard (see [TreeKeyboard]), whose elements can be dragged to another parent or moved with
/// the keyboard where it may be changed. It moves nothing itself: its owner is told of each move
/// ([#setOnMove(BiConsumer)]) and moves the element in the model.
public class PipelineTree extends PipelinePicture {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    DraggableTree<PipelineElement> layoutPanel;
    // The tree, which the template declares in the draggable panel
    @UiField
    PipelineTreeDiagram treePanel;

    // The copy of what is dragged, on the page only while dragging, so not declared
    private final PipelineTreeDiagram subTreePanel = new PipelineTreeDiagram();
    private final TreeKeyboard<PipelineElement> keyboard;
    private boolean allowNullSelection = true;
    private boolean allowDragging;
    private BiConsumer<PipelineElement, PipelineElement> onMove = (parent, child) -> {
    };
    private Consumer<PipelineElement> onRemove = element -> {
    };

    /// Makes the tree, with no pipeline yet.
    public PipelineTree() {
        initWidget(BINDER.createAndBindUi(this));
        layoutPanel.setDraggedCopy(subTreePanel);

        keyboard = new TreeKeyboard<>(treePanel, layoutPanel, "element", "elements");
        keyboard.setNameOf(PipelineElement::getDisplayName);
        keyboard.setCanTake(this::canTake);
        keyboard.setOnMove(this::move);
        keyboard.setOnRemove(element -> onRemove.accept(element));

        layoutPanel.setIsValidTarget(this::canTake);
        // Dragging replaces moving with the keyboard
        layoutPanel.setOnDragStart((parent, child) -> keyboard.cancelDetach());
        layoutPanel.setOnDragEnd(this::move);
        layoutPanel.setOnSelect(item -> {
            if (item != null) {
                // A clicked element has the focus too, so that the keyboard carries on from it
                keyboard.moveTo(item);
            } else if (allowNullSelection) {
                getSelectionModel().setSelected(null, true);
            }
        });
        // A detached element and those after it are faded, with the connectors into them
        treePanel.setFaded(keyboard::isDetached);
        treePanel.setSelectionModel(getSelectionModel());
        keyboard.setSelectionModel(getSelectionModel());
    }

    /// Sets what is done when an element is moved to another parent, by dragging or with the
    /// keyboard: the owner moves it in the model, and says if it can't.
    ///
    /// @param onMove Given the new parent and the element moved.
    public void setOnMove(final BiConsumer<PipelineElement, PipelineElement> onMove) {
        this.onMove = onMove;
    }

    /// Sets what to do when the tree asks for an element to be removed, e.g. with the Delete key.
    ///
    /// @param onRemove Given the element.
    public void setOnRemove(final Consumer<PipelineElement> onRemove) {
        this.onRemove = onRemove;
    }

    /// @param allowDragging Whether elements can be moved, by dragging or with the keyboard, unless
    ///                      the tree is read only.
    public void setAllowDragging(final boolean allowDragging) {
        this.allowDragging = allowDragging;
        applyAllowDragging();
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        applyAllowDragging();
    }

    private void applyAllowDragging() {
        final boolean canMove = allowDragging && !isReadOnly();
        layoutPanel.setAllowDragging(canMove);
        keyboard.setAllowMoving(canMove);
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
    public int getTreeHeight() {
        return treePanel.getTreeHeight();
    }

    @Override
    public void setElementSeverities(final Map<String, Severity> elementIdToSeveritiesMap) {
        // The structure editor doesn't show problems (the stepping screen's pipeline bar does)
    }

    @Override
    public void redraw() {
        treePanel.refresh();
    }

    /// @return The element detached (to be attached to another parent), or null for none.
    public PipelineElement getDetached() {
        return keyboard.getDetached();
    }

    /// Detaches an element and the elements after it, to be attached to another parent, if the
    /// tree can be changed.
    ///
    /// @param element The element.
    public void detach(final PipelineElement element) {
        keyboard.detach(element);
    }

    /// @param target An element.
    /// @return Whether the detached element can be attached to it.
    public boolean canAttach(final PipelineElement target) {
        return keyboard.canAttach(target);
    }

    /// Attaches the detached element to another, if it can be.
    ///
    /// @param target The element to attach it to.
    public void attach(final PipelineElement target) {
        keyboard.attach(target);
    }

    /// Puts the detached element back where it was.
    public void cancelDetach() {
        keyboard.cancelDetach();
    }

    @Override
    protected void showPipelineModel(final PipelineModel pipelineModel) {
        keyboard.cancelDetach();
        treePanel.setPipelineModel(pipelineModel);
        subTreePanel.setPipelineModel(pipelineModel);
    }

    @Override
    protected void showTree(final DefaultTreeForTreeLayout<PipelineElement> tree) {
        treePanel.setTree(tree);
    }

    @Override
    protected void showDisabledElements(final List<PipelineElement> disabledElements) {
        treePanel.setDisabledElements(disabledElements);
    }

    // An element moved to another parent, by dragging or with the keyboard
    private void move(final PipelineElement parent, final PipelineElement child) {
        onMove.accept(parent, child);
    }

    // Whether a parent can take a child, by their types and the parent's children
    private boolean canTake(final PipelineElement parent, final PipelineElement child) {
        final PipelineModel pipelineModel = getModel();
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


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, PipelineTree> {

    }
}
