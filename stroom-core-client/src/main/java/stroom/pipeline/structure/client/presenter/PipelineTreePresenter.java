/*
 * Copyright 2016-2025 Crown Copyright
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
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
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
import java.util.stream.Collectors;

public class PipelineTreePresenter extends MyPresenterWidget<PipelineTreePresenter.PipelineTreeView>
        implements HasDirtyHandlers, PipelineTreeUiHandlers, HasContextMenuHandlers {

    private final MySingleSelectionModel<PipelineElement> selectionModel;
    private PipelineModel pipelineModel;
    private PipelineTreeBuilder pipelineTreeBuilder;
    private List<PipelineElement> disabledElements = new ArrayList<>();

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
                if (pipelineModel.moveElement(parent, child)) {
                    setDirty(true);
                }
            } catch (final RuntimeException e) {
                AlertEvent.fireError(PipelineTreePresenter.this, e.getMessage(), null);
            }
        }
    }

    private void refresh() {
        if (pipelineModel != null && pipelineTreeBuilder != null) {
            final DefaultTreeForTreeLayout<PipelineElement> tree = pipelineTreeBuilder.getTree(pipelineModel);
            final PipelineElement selectedElement = selectionModel.getSelectedObject();
            if (selectedElement != null) {
                // If the selected element no longer exists then
                // deselect it.
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
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }

    private void setDirty(final boolean dirty) {
        if (dirty) {
            DirtyEvent.fire(this, dirty);
        }
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
    }
}
