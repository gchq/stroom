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

import com.google.gwt.canvas.dom.client.Context2d;
import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.Position;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.view.client.SelectionModel;
import stroom.data.grid.client.MouseHelper;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.widget.htree.client.ArrowConnectorRenderer;
import stroom.widget.htree.client.ConnectorRenderer;
import stroom.widget.htree.client.LayeredCanvas;
import stroom.widget.htree.client.TreeRenderer;
import stroom.widget.htree.client.TreeRenderer2;
import stroom.widget.htree.client.treelayout.AbegoTreeLayout;
import stroom.widget.htree.client.treelayout.Configuration.AlignmentInLevel;
import stroom.widget.htree.client.treelayout.Configuration.Location;
import stroom.widget.htree.client.treelayout.NodeExtentProvider;
import stroom.widget.htree.client.treelayout.TreeLayout;
import stroom.widget.htree.client.treelayout.util.DefaultConfiguration;
import stroom.widget.htree.client.treelayout.util.DefaultTreeForTreeLayout;

public class PipelineTreePanel extends TreePanel<PipelineElement> {
    private static final double HORIZONTAL_SEPARATION = 20;
    private static final double VERTICAL_SEPARATION = 10;

    private TreeRenderer2<PipelineElement> renderer;
    private TreeLayout<PipelineElement> treeLayout;

    private final LayeredCanvas canvas;
    private PipelineElementRenderer cellRenderer;

    private final FlowPanel panel;
    private final FlowPanel boxPanel;
    private DefaultTreeForTreeLayout<PipelineElement> tree;

    private final PendingOperation pendingOperation = new PendingOperation();

    public PipelineTreePanel(final PipelineElementBoxFactory pipelineElementBoxFactory) {
        panel = new FlowPanel();
        setAbsoluteLeftTop(panel.getElement());
        boxPanel = new FlowPanel();
        setAbsoluteLeftTop(boxPanel.getElement());

        // setup the tree layout configuration.
        canvas = LayeredCanvas.createIfSupported();
        if (canvas != null) {
            final Context2d arrowContext = canvas.getLayer(TreeRenderer.ARROW_LAYER).getContext2d();

            cellRenderer = new PipelineElementRenderer(boxPanel, pipelineElementBoxFactory);
            final ConnectorRenderer<PipelineElement> connectorRenderer = new ArrowConnectorRenderer<PipelineElement>(
                    arrowContext);

            // setup the tree layout configuration
            final DefaultConfiguration<PipelineElement> layoutConfig = new DefaultConfiguration<PipelineElement>(
                    HORIZONTAL_SEPARATION, VERTICAL_SEPARATION, Location.Left, AlignmentInLevel.TowardsRoot);
            final NodeExtentProvider<PipelineElement> extentProvider = cellRenderer;

            treeLayout = new AbegoTreeLayout<PipelineElement>(extentProvider, layoutConfig);

            renderer = new TreeRenderer2<PipelineElement>(canvas, cellRenderer, connectorRenderer);
            renderer.setTreeLayout(treeLayout);

            setAbsoluteLeftTop(canvas.getElement());
            panel.add(canvas);
        }

        panel.add(boxPanel);
        initWidget(panel);
    }

    private void setAbsoluteLeftTop(final com.google.gwt.dom.client.Element element) {
        final Style style = element.getStyle();
        style.setPosition(Position.ABSOLUTE);
        style.setLeft(0, Unit.PX);
        style.setTop(0, Unit.PX);
    }

    @Override
    public void setTree(final DefaultTreeForTreeLayout<PipelineElement> tree) {
        this.tree = tree;
        if (treeLayout != null) {
            treeLayout.setTree(tree);
        }
    }

    @Override
    public Box<PipelineElement> getBox(final PipelineElement item) {
        if (renderer != null) {
            for (final PipelineElementBox box : cellRenderer.getBoxes()) {
                if (box.getItem() == item) {
                    return box;
                }
            }
        }

        return null;
    }

    @Override
    public Box<PipelineElement> getTargetBox(final Event event, final boolean usePosition) {
        if (renderer != null) {
            final com.google.gwt.dom.client.Element target = event.getEventTarget().cast();
            for (final PipelineElementBox box : cellRenderer.getBoxes()) {
                if (usePosition) {
                    if (MouseHelper.mouseIsOverElement(event, box.getElement())) {
                        return box;
                    }
                } else {
                    if (box.getElement().isOrHasChild(target)) {
                        return box;
                    }
                }
            }
        }

        return null;
    }

    @Override
    public void setSelectionModel(final SelectionModel<PipelineElement> selectionModel) {
        if (renderer != null) {
            cellRenderer.setSelectionModel(selectionModel);
        }
    }

    @Override
    public void refresh() {
        refresh(null);
    }

    @Override
    public void refresh(final RefreshCallback callback) {
        boxPanel.clear();
        if (renderer != null) {
            cellRenderer.clear();
            renderer.draw();
        }

        pendingOperation.scheduleOperation(() -> {
            boxPanel.clear();
            if (renderer != null) {
                cellRenderer.clear();
                renderer.draw();

                if (callback != null) {
                    callback.onRefresh();
                }
            }
        });
    }

    @Override
    public DefaultTreeForTreeLayout<PipelineElement> getTree() {
        return tree;
    }

    public int getTreeHeight() {
        return canvas.getOffsetHeight();
    }
}
