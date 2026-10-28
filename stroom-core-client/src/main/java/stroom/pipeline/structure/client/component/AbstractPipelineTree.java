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

package stroom.pipeline.structure.client.component;

import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineLayer;
import stroom.pipeline.structure.client.PipelineModel;
import stroom.pipeline.structure.client.PipelineTreeBuilder;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Severity;
import stroom.widget.component.client.Component;
import stroom.widget.contextmenu.client.event.ContextMenuEvent;
import stroom.widget.contextmenu.client.event.HasContextMenuHandlers;
import stroom.widget.htree.client.treelayout.util.DefaultTreeForTreeLayout;
import stroom.widget.util.client.MySingleSelectionModel;

import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/// What the pipeline's two pictures share: the structure editor's tree ([PipelineTree]) and the
/// stepping screen's bar of chevrons (`PipelinePathBar`). Each is given the pipeline's model and
/// the builder of its tree of elements, has a selection that refuses disabled elements, and draws
/// itself again when either changes; its owner uses either the same way.
public abstract class AbstractPipelineTree extends Component implements HasContextMenuHandlers {

    private final MySingleSelectionModel<PipelineElement> selectionModel;
    private PipelineModel pipelineModel;
    private PipelineTreeBuilder pipelineTreeBuilder;
    private List<PipelineElement> disabledElements = new ArrayList<>();

    /// Makes the selection, which a subclass gives its drawing ([#getSelectionModel()]).
    protected AbstractPipelineTree() {
        selectionModel = new MySingleSelectionModel<>() {
            @Override
            protected boolean isSelectable(final PipelineElement element) {
                return !disabledElements.contains(element);
            }
        };
        //noinspection unused e
        selectionModel.addSelectionChangeHandler(e -> redraw());
    }

    /// Shows a pipeline's model, which says each element's type.
    ///
    /// @param pipelineModel The model.
    protected abstract void showPipelineModel(PipelineModel pipelineModel);

    /// Shows the pipeline's tree of elements.
    ///
    /// @param tree The tree.
    protected abstract void showTree(DefaultTreeForTreeLayout<PipelineElement> tree);

    /// Draws the tree again, e.g. after the selection changes.
    public abstract void redraw();

    /// Shows which elements can't be chosen.
    ///
    /// @param disabledElements The elements.
    protected abstract void showDisabledElements(List<PipelineElement> disabledElements);

    /// @param allowNullSelection Whether clicking off the elements clears the selection.
    public abstract void setAllowNullSelection(boolean allowNullSelection);

    /// @return The height the tree is drawn at, in pixels.
    public abstract int getTreeHeight();

    /// Shows the problems a step has given each element, where the drawing shows them.
    ///
    /// @param elementIdToSeveritiesMap Each element's worst problem, keyed by its ID.
    public abstract void setElementSeverities(Map<String, Severity> elementIdToSeveritiesMap);

    @Override
    public abstract HandlerRegistration addContextMenuHandler(ContextMenuEvent.Handler handler);

    /// @param model The pipeline's model, whose changes the owner tells this about by asking for
    ///              the tree again ([#setPipelineTreeBuilder]).
    public void setModel(final PipelineModel model) {
        this.pipelineModel = model;
        showPipelineModel(pipelineModel);
        refresh();
    }

    /// @return The pipeline's model, or null until it is given one.
    protected PipelineModel getModel() {
        return pipelineModel;
    }

    /// @param pipelineTreeBuilder Builds the pipeline's tree of elements from its model.
    public void setPipelineTreeBuilder(final PipelineTreeBuilder pipelineTreeBuilder) {
        this.pipelineTreeBuilder = pipelineTreeBuilder;
        refresh();
    }

    /// @return The selection, which refuses disabled elements.
    public MySingleSelectionModel<PipelineElement> getSelectionModel() {
        return selectionModel;
    }

    /// @param disabledElements The elements that can't be chosen.
    public void setDisabledElements(final List<PipelineElement> disabledElements) {
        this.disabledElements = disabledElements;
        showDisabledElements(disabledElements);
    }

    /// @return All the element names currently in the pipeline.
    public Set<String> getNames() {
        final List<PipelineElement> pipelineElements = NullSafe.get(
                pipelineModel,
                PipelineModel::getPipelineLayer,
                PipelineLayer::getPipelineData,
                PipelineData::getAddedElements);

        return NullSafe.stream(pipelineElements)
                .map(PipelineElement::getDisplayName)
                .collect(Collectors.toSet());
    }

    private void refresh() {
        if (pipelineModel != null && pipelineTreeBuilder != null) {
            final DefaultTreeForTreeLayout<PipelineElement> tree = pipelineTreeBuilder.getTree(pipelineModel);
            final PipelineElement selectedElement = selectionModel.getSelectedObject();
            if (selectedElement != null) {
                // If the selected element no longer exists then deselect it.
                if (tree.getParent(selectedElement) == null && tree.getChildren(selectedElement) == null) {
                    selectionModel.clear();
                } else {
                    selectionModel.setSelected(selectedElement, false);
                }
            }

            showTree(tree);
            redraw();
        }
    }
}
