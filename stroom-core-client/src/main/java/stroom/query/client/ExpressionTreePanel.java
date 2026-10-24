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

package stroom.query.client;

import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.pipeline.structure.client.view.Box;
import stroom.pipeline.structure.client.view.TreePanel;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.client.presenter.FieldSelectionListModel;
import stroom.security.client.presenter.UserRefSelectionBoxPresenter;
import stroom.ui.config.client.UiConfigCache;
import stroom.widget.component.client.ControllerNode;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.datagrid.client.MouseHelper;
import stroom.widget.htree.client.BracketConnectorRenderer;
import stroom.widget.htree.client.ConnectorRenderer;
import stroom.widget.htree.client.LayeredCanvas;
import stroom.widget.htree.client.TreeRenderer;
import stroom.widget.htree.client.TreeRenderer2;
import stroom.widget.htree.client.treelayout.CenteredParentTreeLayout;
import stroom.widget.htree.client.treelayout.Configuration.AlignmentInLevel;
import stroom.widget.htree.client.treelayout.Configuration.Location;
import stroom.widget.htree.client.treelayout.NodeExtentProvider;
import stroom.widget.htree.client.treelayout.TreeLayout;
import stroom.widget.htree.client.treelayout.util.DefaultConfiguration;
import stroom.widget.htree.client.treelayout.util.DefaultTreeForTreeLayout;

import com.google.gwt.canvas.dom.client.Context2d;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.view.client.SelectionModel;
import com.google.inject.Provider;

import java.util.Objects;
import java.util.function.Predicate;

public class ExpressionTreePanel extends TreePanel<Item> implements HasControllerNode {

    private static final double HORIZONTAL_SEPARATION = 20;
    private static final double VERTICAL_SEPARATION = 0;
    private final FlowPanel boxPanel;
    private final OperatorEditor operatorEditor;
    private final TermEditor termEditor;
    private TreeRenderer2<Item> renderer;
    private TreeLayout<Item> treeLayout;
    private ExpressionItemRenderer cellRenderer;
    private DefaultTreeForTreeLayout<Item> tree;
    private Predicate<Item> faded = item -> false;
    // Whether the tree is being drawn, and whether it was asked to be drawn again meanwhile
    private boolean drawing;
    private boolean redrawAfterDraw;

    public ExpressionTreePanel(final Provider<DocSelectionBoxPresenter> docRefProvider,
                               final Provider<UserRefSelectionBoxPresenter> userRefProvider,
                               final UiConfigCache uiConfigCache) {

        operatorEditor = new OperatorEditor();
        termEditor = new TermEditor(docRefProvider, userRefProvider, uiConfigCache);

        final FlowPanel panel = new FlowPanel();
        panel.setStyleName("expression-tree");
        boxPanel = new FlowPanel();
        boxPanel.setStyleName("expression-tree__items");
        // The boxes are the tree's items (ExpressionItemRenderer), for assistive technology
        boxPanel.getElement().setAttribute("role", "tree");
        boxPanel.getElement().setAttribute("aria-label", "Expression");

        final LayeredCanvas canvas = LayeredCanvas.createIfSupported();
        if (canvas != null) {
            final Context2d arrowContext = canvas.getLayer(TreeRenderer.ARROW_LAYER).getContext2d();

            cellRenderer = new ExpressionItemRenderer(boxPanel, operatorEditor, termEditor);
            final ConnectorRenderer<Item> connectorRenderer = new BracketConnectorRenderer<>(arrowContext,
                    item -> faded.test(item), "--expression-tree__bracket");

            // setup the tree layout configuration
            final DefaultConfiguration<Item> layoutConfig = new DefaultConfiguration<>(HORIZONTAL_SEPARATION,
                    VERTICAL_SEPARATION, Location.Left, AlignmentInLevel.TowardsRoot);
            final NodeExtentProvider<Item> extentProvider = cellRenderer;

            treeLayout = new CenteredParentTreeLayout<>(extentProvider, layoutConfig);

            renderer = new TreeRenderer2<>(canvas, cellRenderer, connectorRenderer);
            renderer.setTreeLayout(treeLayout);

            canvas.addStyleName("expression-tree__canvas");
            panel.add(canvas);
        }

        panel.add(boxPanel);

        initWidget(panel);
    }

    /// @return Its term editor's node, which owns the presenters the editor makes.
    @Override
    public ControllerNode getControllerNode() {
        return termEditor.getControllerNode();
    }

    @Override
    public Box<Item> getBox(final Item item) {
        if (renderer != null) {
            for (final ExpressionItemBox box : cellRenderer.getBoxes()) {
                if (Objects.equals(box.getItem(), item)) {
                    return box;
                }
            }
        }

        return null;
    }

    @Override
    public Box<Item> getTargetBox(final Event event, final boolean usePosition) {
        if (renderer != null) {
            final Element target = event.getEventTarget().cast();
            for (final ExpressionItemBox box : cellRenderer.getBoxes()) {
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
    public void setSelectionModel(final SelectionModel<Item> selectionModel) {
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
        if (drawing) {
            // Asked for while drawing, e.g. by a pending selection change that drawing resolves
            // (reading the selection fires it): drawn again once this draw is done, rather than
            // inside it, which would leave the first boxes in place too
            redrawAfterDraw = true;
            return;
        }
        // Drawing replaces the boxes, so the focus is put back on the same item's box
        final Item focused = getFocusedItem();
        boxPanel.clear();
        if (renderer != null) {
            cellRenderer.clear();
            drawing = true;
            try {
                renderer.draw();
            } finally {
                drawing = false;
            }
            if (redrawAfterDraw) {
                redrawAfterDraw = false;
                boxPanel.clear();
                cellRenderer.clear();
                renderer.draw();
            }
            focus(focused);
            if (callback != null) {
                callback.onRefresh();
            }
            fireAfterRefresh();
        }
    }

    // The item whose box itself (not a field in it) has the focus, if any
    private Item getFocusedItem() {
        if (renderer != null) {
            final Element active = getActiveElement();
            for (final ExpressionItemBox box : cellRenderer.getBoxes()) {
                if (box.getElement().equals(active)) {
                    return box.getItem();
                }
            }
        }
        return null;
    }

    private void focus(final Item item) {
        if (item != null) {
            final Box<Item> box = getBox(item);
            if (box != null) {
                box.getElement().focus();
            }
        }
    }

    private static native Element getActiveElement() /*-{
        return $doc.activeElement;
    }-*/;

    /// Sets the item opened for editing, which shows its editor while it is selected, or none.
    ///
    /// @param item The item, or null for none.
    public void setEditingItem(final Item item) {
        if (cellRenderer != null) {
            cellRenderer.setEditingItem(item);
        }
    }

    /// @param item An item.
    /// @return Whether it is the item opened for editing (see [#setEditingItem]).
    public boolean isEditingItem(final Item item) {
        return cellRenderer != null && cellRenderer.isEditingItem(item);
    }

    @Override
    public DefaultTreeForTreeLayout<Item> getTree() {
        return tree;
    }

    @Override
    public void setTree(final DefaultTreeForTreeLayout<Item> tree) {
        this.tree = tree;
        if (treeLayout != null) {
            treeLayout.setTree(tree);
        }
    }

    public void init(final RestFactory restFactory,
                     final DocRef dataSource,
                     final FieldSelectionListModel fieldSelectionListModel) {
        termEditor.init(restFactory, dataSource, fieldSelectionListModel);
    }

    public void setConditionFilter(final Predicate<Condition> conditionFilter) {
        termEditor.setConditionFilter(conditionFilter);
    }

    public void write() {
        if (operatorEditor != null) {
            operatorEditor.write();
        }
        if (termEditor != null) {
            termEditor.write();
        }
    }

    /// Sets which items are faded, with the brackets to their items, e.g. those detached to be
    /// attached to another operator. They are faded when the tree is next drawn.
    ///
    /// @param faded Whether an item is faded.
    public void setFaded(final Predicate<Item> faded) {
        this.faded = faded;
    }

    /// @return Whether a field of the editor has its own popup open (see
    /// [TermEditor#isPopupShowing]).
    public boolean isEditorPopupShowing() {
        return termEditor != null && termEditor.isPopupShowing();
    }

    /// Closes the editor's fields' own popups.
    public void hideEditorPopups() {
        if (termEditor != null) {
            termEditor.hidePopups();
        }
    }

    public void endEditing() {
        if (operatorEditor != null) {
            operatorEditor.endEdit();
        }
        if (termEditor != null) {
            termEditor.endEdit();
        }
    }

    public void setUiHandlers(final ExpressionUiHandlers uiHandlers) {
        if (operatorEditor != null) {
            operatorEditor.setOnChange(uiHandlers == null
                    ? null
                    : uiHandlers::onChange);
        }
        if (termEditor != null) {
            termEditor.setUiHandlers(uiHandlers);
        }
    }
}
