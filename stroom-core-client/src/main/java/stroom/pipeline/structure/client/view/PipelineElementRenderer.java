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

package stroom.pipeline.structure.client.view;

import stroom.pipeline.shared.data.PipelineElement;
import stroom.widget.htree.client.CellRenderer2;
import stroom.widget.htree.client.treelayout.Bounds;
import stroom.widget.htree.client.treelayout.Dimension;
import stroom.widget.htree.client.treelayout.NodeExtentProvider;
import stroom.widget.htree.client.treelayout.TreeLayout;

import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.view.client.SelectionModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PipelineElementRenderer
        implements CellRenderer2<PipelineElement>, NodeExtentProvider<PipelineElement> {

    private final PipelineElementBoxFactory pipelineElementBoxFactory;
    private final FlowPanel panel;
    private final List<PipelineElementBox> boxes = new ArrayList<>();

    // Cache the PipelineElementBox objects so we don't need to keep creating them.
    // This avoids flickr as we can just change their style rather than creating new and adding style.
    private final Map<String, PipelineElementBox> elementIdToBoxMap = new HashMap<>();
    private SelectionModel<PipelineElement> selectionModel;

    public PipelineElementRenderer(final FlowPanel panel,
                                   final PipelineElementBoxFactory pipelineElementBoxFactory) {
        this.panel = panel;
        this.pipelineElementBoxFactory = pipelineElementBoxFactory;
    }

    @Override
    public void render(final TreeLayout<PipelineElement> treeLayout,
                       final Bounds bounds,
                       final PipelineElement pipelineElement) {
        final boolean selected = selectionModel != null && selectionModel.isSelected(pipelineElement);
        final PipelineElementBox pipelineElementBox = createBox(pipelineElement);
        pipelineElementBox.setSelected(selected);

        final Style style = pipelineElementBox.getElement().getStyle();
        style.setLeft(bounds.getX(), Unit.PX);
        style.setTop(bounds.getY(), Unit.PX);

        panel.add(pipelineElementBox);
        boxes.add(pipelineElementBox);
    }

    @Override
    public Dimension getExtents(final PipelineElement pipelineElement) {
        double width = 0;
        double height = 0;

        final PipelineElementBox pipelineElementBox = createBox(pipelineElement);
        RootPanel.get().add(pipelineElementBox);
        width += pipelineElementBox.getElement().getScrollWidth();
        height += pipelineElementBox.getElement().getScrollHeight();
        RootPanel.get().remove(pipelineElementBox);

        return new Dimension(width, height);
    }

    private PipelineElementBox createBox(final PipelineElement pipelineElement) {
        if (pipelineElement == null) {
            return null;
        } else {
            final PipelineElementBox pipelineElementBox = elementIdToBoxMap.computeIfAbsent(
                    pipelineElement.getId(),
                    k -> pipelineElementBoxFactory.create(pipelineElement));
            // Refresh any styling on the box based on the PipelineElement state
            pipelineElementBox.refresh(pipelineElement);
            return pipelineElementBox;
        }
    }

    public void clear() {
        boxes.clear();
    }

    public List<PipelineElementBox> getBoxes() {
        return boxes;
    }

    public void setSelectionModel(final SelectionModel<PipelineElement> selectionModel) {
        this.selectionModel = selectionModel;
    }
}
