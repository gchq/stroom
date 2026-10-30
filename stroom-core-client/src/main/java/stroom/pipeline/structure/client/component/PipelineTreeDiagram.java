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
import stroom.pipeline.structure.client.PipelineModel;
import stroom.widget.component.client.AriaPanel;
import stroom.widget.datagrid.client.MouseHelper;
import stroom.widget.htree.client.ConnectorRenderer;
import stroom.widget.htree.client.LayeredCanvas;
import stroom.widget.htree.client.TreeRenderer;
import stroom.widget.htree.client.treelayout.AbegoTreeLayout;
import stroom.widget.htree.client.treelayout.Configuration.AlignmentInLevel;
import stroom.widget.htree.client.treelayout.Configuration.Location;
import stroom.widget.htree.client.treelayout.NodeExtentProvider;
import stroom.widget.htree.client.treelayout.TreeLayout;
import stroom.widget.htree.client.treelayout.util.DefaultConfiguration;
import stroom.widget.htree.client.treelayout.util.DefaultTreeForTreeLayout;

import com.google.gwt.canvas.client.Canvas;
import com.google.gwt.canvas.dom.client.Context2d;
import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.SelectionModel;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public class PipelineTreeDiagram extends TreePanel<PipelineElement> {

    private static final Binder BINDER = GWT.create(Binder.class);

    // Room between columns for the connectors' dots and curves
    private static final double HORIZONTAL_SEPARATION = 40;
    private static final double VERTICAL_SEPARATION = 10;
    private static final String FADED_LAYER = "faded";
    @UiField
    FlowPanel panel;
    private LayeredCanvas canvas;
    @UiField
    AriaPanel boxPanel;
    // The faded elements' boxes and their connectors (those into them, drawn on their own canvas
    // layer), faded as one by its opacity, so that nothing in it shows through anything else in it
    // (e.g. a line through a dot). It is in the box panel, under the other boxes (so that a faded
    // line doesn't cover the dot it starts from), and positions nothing itself: what is in it is
    // where it would otherwise be.
    @UiField
    AriaPanel fadedGroup;
    private Predicate<PipelineElement> faded = element -> false;
    private final PendingOperation pendingOperation = new PendingOperation();
    private TreeRenderer<PipelineElement> renderer;
    private TreeLayout<PipelineElement> treeLayout;
    private PipelineElementRenderer cellRenderer;
    private DefaultTreeForTreeLayout<PipelineElement> tree;
    private SelectionModel<PipelineElement> selectionModel;

    /// Makes an empty tree; [#setPipelineModel] gives it its pipeline.
    public PipelineTreeDiagram() {
        initWidget(BINDER.createAndBindUi(this));
    }

    @Override
    public Box<PipelineElement> getBox(final PipelineElement item) {
        if (renderer != null) {
            for (final PipelineElementBox box : cellRenderer.getBoxes()) {
                if (Objects.equals(box.getItem(), item)) {
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
        this.selectionModel = selectionModel;
        if (renderer != null) {
            cellRenderer.setSelectionModel(selectionModel);
        }
    }

    public void setPipelineModel(final PipelineModel pipelineModel) {
        if (pipelineModel == null) {
            throw new NullPointerException("Null model");
        }

        // The boxes, the tree's items (PipelineElementRenderer), go in the template's panels
        clear();

        // setup the tree layout configuration.
        canvas = LayeredCanvas.createIfSupported();
        if (canvas != null) {
            final Context2d arrowContext = canvas.getLayer(TreeRenderer.ARROW_LAYER).getContext2d();
            // The faded layer is sized and cleared with the others, but drawn in the faded group
            final Canvas fadedLayer = canvas.getLayer(FADED_LAYER);
            fadedGroup.getElement().appendChild(fadedLayer.getElement());

            final PipelineElementBoxFactory pipelineElementBoxFactory = new PipelineElementBoxFactory(pipelineModel);
            cellRenderer = new PipelineElementRenderer(
                    boxPanel, fadedGroup, this::isFaded, pipelineElementBoxFactory);
            cellRenderer.setSelectionModel(selectionModel);
            final ConnectorRenderer<PipelineElement> connectorRenderer = new PipelineConnectorRenderer(
                    arrowContext, fadedLayer.getContext2d(), this::isFaded, pipelineModel);

            // Setup the tree layout configuration
            final DefaultConfiguration<PipelineElement> layoutConfig = new DefaultConfiguration<>(
                    HORIZONTAL_SEPARATION, VERTICAL_SEPARATION, Location.Left, AlignmentInLevel.TowardsRoot);
            final NodeExtentProvider<PipelineElement> extentProvider = cellRenderer;

            treeLayout = new AbegoTreeLayout<>(extentProvider, layoutConfig);

            renderer = new TreeRenderer<>(canvas, cellRenderer, connectorRenderer);
            renderer.setTreeLayout(treeLayout);

            canvas.addStyleName("pipeline-tree-diagram__canvas");
            // Under the boxes
            panel.insert(canvas, 0);
        }
    }

    // Empties the panels of the last pipeline's boxes (keeping the faded group), and takes away
    // its canvas, with the faded layer it put in the faded group
    private void clear() {
        for (int i = boxPanel.getWidgetCount() - 1; i >= 0; i--) {
            if (boxPanel.getWidget(i) != fadedGroup) {
                boxPanel.remove(i);
            }
        }
        fadedGroup.clear();
        fadedGroup.getElement().removeAllChildren();

        if (canvas != null) {
            canvas.clear();
            panel.remove(canvas);
            canvas = null;
        }
    }

    @Override
    public void refresh() {
        refresh(null);
    }

    @Override
    public void refresh(final RefreshCallback callback) {
        if (renderer != null) {
            // Drawing replaces the boxes, so the focus is put back on the same element's box (or
            // keys pressed before the redraw below would be lost)
            final PipelineElement focused = getFocusedItem();
            clearBoxes();
            cellRenderer.clear();
            renderer.draw();
            focus(focused);
            pendingOperation.scheduleOperation(() -> {
                final PipelineElement stillFocused = getFocusedItem();
                clearBoxes();
                if (renderer != null) {
                    cellRenderer.clear();
                    renderer.draw();
                    focus(stillFocused);

                    if (callback != null) {
                        callback.onRefresh();
                    }
                    fireAfterRefresh();
                }
            });
        }
    }

    // Removes the boxes, keeping the faded group (and its canvas layer), emptied, under those to come
    private void clearBoxes() {
        boxPanel.clear();
        // Not clear(), which would remove the canvas layer too (it removes all of its children)
        while (fadedGroup.getWidgetCount() > 0) {
            fadedGroup.remove(0);
        }
        boxPanel.add(fadedGroup);
    }

    /// Sets which elements are faded, as one, with the connectors into them, e.g. those detached
    /// to be attached to another parent. They are faded when the tree is next drawn.
    ///
    /// @param faded Whether an element is faded.
    public void setFaded(final Predicate<PipelineElement> faded) {
        this.faded = faded;
    }

    private boolean isFaded(final PipelineElement element) {
        return faded.test(element);
    }

    // The element whose box has the focus, if any
    private PipelineElement getFocusedItem() {
        final com.google.gwt.dom.client.Element active = getActiveElement();
        if (active != null) {
            for (final PipelineElementBox box : cellRenderer.getBoxes()) {
                if (box.getElement().isOrHasChild(active)) {
                    return box.getItem();
                }
            }
        }
        return null;
    }

    private static native com.google.gwt.dom.client.Element getActiveElement() /*-{
        return $doc.activeElement;
    }-*/;

    private void focus(final PipelineElement item) {
        if (item != null) {
            final Box<PipelineElement> box = getBox(item);
            if (box != null) {
                box.getElement().focus();
            }
        }
    }

    @Override
    public DefaultTreeForTreeLayout<PipelineElement> getTree() {
        return tree;
    }

    @Override
    public void setTree(final DefaultTreeForTreeLayout<PipelineElement> tree) {
        this.tree = tree;
        if (cellRenderer != null) {
            cellRenderer.setTree(tree);
        }
        if (treeLayout != null) {
            treeLayout.setTree(tree);
        }
    }

    public int getTreeHeight() {
        if (renderer == null || renderer.getBounds() == null) {
            return 0;
        }

        return (int) renderer.getBounds().getHeight();
    }

    public void setDisabledElements(final List<PipelineElement> disabledElements) {
        if (cellRenderer != null) {
            cellRenderer.getBoxes().stream()
                    .filter(b -> disabledElements.contains(b.getItem()))
                    .forEach(b -> b.setDisabled(true));
        }
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, PipelineTreeDiagram> {

    }
}
