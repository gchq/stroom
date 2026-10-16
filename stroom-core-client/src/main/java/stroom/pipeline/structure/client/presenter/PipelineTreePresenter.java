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

package stroom.pipeline.structure.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineLayer;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Severity;
import stroom.widget.contextmenu.client.event.ContextMenuEvent.Handler;
import stroom.widget.contextmenu.client.event.HasContextMenuHandlers;
import stroom.widget.htree.client.treelayout.util.DefaultTreeForTreeLayout;
import stroom.widget.util.client.MySingleSelectionModel;

import com.google.gwt.view.client.SelectionModel;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class PipelineTreePresenter
        extends MyPresenterWidget<PipelineTreePresenter.PipelineTreeView>
        implements PipelineTreeUiHandlers, HasContextMenuHandlers {

    private final MySingleSelectionModel<PipelineElement> selectionModel;
    private PipelineModel pipelineModel;
    private PipelineTreeBuilder pipelineTreeBuilder;
    private List<PipelineElement> disabledElements = new ArrayList<>();
    // What to do when the tree asks for an element to be removed, e.g. with the Delete key
    private Consumer<PipelineElement> removeHandler;

    @Inject
    public PipelineTreePresenter(final EventBus eventBus, final PipelineTreeView view) {
        super(eventBus, view);

        selectionModel = new MySingleSelectionModel<>() {
            @Override
            protected boolean isSelectable(final PipelineElement element) {
                return !disabledElements.contains(element);
            }
        };
        view.setSelectionModel(selectionModel);
        view.setUiHandlers(this);
    }

    @Override
    protected void onBind() {
        super.onBind();
        if (selectionModel != null) {
            registerHandler(selectionModel.addSelectionChangeHandler(event -> getView().refresh()));
        }
    }

    public void setModel(final PipelineModel model) {
        this.pipelineModel = model;
        getView().setPipelineModel(pipelineModel);
        refresh();
    }

    @Override
    public void onMove(final PipelineElement parent, final PipelineElement child) {
        if (pipelineModel != null && pipelineModel.getParentMap() != null) {
            try {
                // A successful move fires a change event from the model, which is what redraws the
                // tree and re-evaluates whether the pipeline is dirty.
                pipelineModel.moveElement(parent, child);
            } catch (final RuntimeException e) {
                AlertEvent.fireError(PipelineTreePresenter.this, e.getMessage(), null);
            }
        }
    }

    @Override
    public void onRemove(final PipelineElement element) {
        if (removeHandler != null) {
            removeHandler.accept(element);
        }
    }

    /// Sets what to do when the tree asks for an element to be removed, e.g. with the Delete key.
    ///
    /// @param removeHandler What to do, or null for nothing.
    public void setRemoveHandler(final Consumer<PipelineElement> removeHandler) {
        this.removeHandler = removeHandler;
    }

    /// @return The element detached (to be attached to another parent), or null for none.
    public PipelineElement getDetached() {
        return getView().getDetached();
    }

    /// Detaches an element and the elements after it, to be attached to another parent.
    ///
    /// @param element The element.
    public void detach(final PipelineElement element) {
        getView().detach(element);
    }

    /// @param target An element.
    /// @return Whether the detached element can be attached to it.
    public boolean canAttach(final PipelineElement target) {
        return getView().canAttach(target);
    }

    /// Attaches the detached element to another, if it can be.
    ///
    /// @param target The element to attach it to.
    public void attach(final PipelineElement target) {
        getView().attach(target);
    }

    /// Puts the detached element back where it was.
    public void cancelDetach() {
        getView().cancelDetach();
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

            getView().setTree(tree);
            getView().refresh();
        }
    }

    public void setPipelineTreeBuilder(final PipelineTreeBuilder pipelineTreeBuilder) {
        this.pipelineTreeBuilder = pipelineTreeBuilder;
        refresh();
    }

    public MySingleSelectionModel<PipelineElement> getSelectionModel() {
        return selectionModel;
    }

    public void setAllowDragging(final boolean allowDragging) {
        getView().setAllowDragging(allowDragging);
    }

    public void setAllowNullSelection(final boolean allowNullSelection) {
        getView().setAllowNullSelection(allowNullSelection);
    }

    @Override
    public HandlerRegistration addContextMenuHandler(final Handler handler) {
        return getView().addContextMenuHandler(handler);
    }

    public int getTreeHeight() {
        return getView().getTreeHeight();
    }

    public void setElementSeverities(final Map<String, Severity> elementIdToSeveritiesMap) {
        getView().setSeverities(elementIdToSeveritiesMap);
    }

    public void setDisabledElements(final List<PipelineElement> disabledElements) {
        this.disabledElements = disabledElements;
        getView().setDisabledElements(disabledElements);
    }

    /**
     * @return All the element names currently in the pipeline
     */
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


    // --------------------------------------------------------------------------------


    public interface PipelineTreeView extends View, HasContextMenuHandlers, HasUiHandlers<PipelineTreeUiHandlers> {

        void setPipelineModel(PipelineModel pipelineModel);

        void setTree(DefaultTreeForTreeLayout<PipelineElement> tree);

        void setSelectionModel(SelectionModel<PipelineElement> selectionModel);

        void refresh();

        void setAllowDragging(boolean allowDragging);

        void setAllowNullSelection(boolean allowNullSelection);

        int getTreeHeight();

        void setDisabledElements(final List<PipelineElement> disabledElements);

        void setSeverities(final Map<String, Severity> elementIdToSeveritiesMap);

        /// @return The element detached (to be attached to another parent), or null for none.
        default PipelineElement getDetached() {
            return null;
        }

        /// Detaches an element and the elements after it, to be attached to another parent, if
        /// the tree can be changed.
        ///
        /// @param element The element.
        default void detach(final PipelineElement element) {
        }

        /// @param target An element.
        /// @return Whether the detached element can be attached to it.
        default boolean canAttach(final PipelineElement target) {
            return false;
        }

        /// Attaches the detached element to another, if it can be.
        ///
        /// @param target The element to attach it to.
        default void attach(final PipelineElement target) {
        }

        /// Puts the detached element back where it was.
        default void cancelDetach() {
        }
    }
}
