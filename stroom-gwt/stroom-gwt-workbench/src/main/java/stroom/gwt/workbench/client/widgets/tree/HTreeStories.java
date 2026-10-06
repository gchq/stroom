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


package stroom.gwt.workbench.client.widgets.tree;

import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.htree.client.ArrowConnectorRenderer;
import stroom.widget.htree.client.BracketConnectorRenderer;
import stroom.widget.htree.client.ConnectorRenderer;
import stroom.widget.htree.client.LayeredCanvas;
import stroom.widget.htree.client.TextCellRenderer;
import stroom.widget.htree.client.TreeRenderer;
import stroom.widget.htree.client.treelayout.AbegoTreeLayout;
import stroom.widget.htree.client.treelayout.CenteredParentTreeLayout;
import stroom.widget.htree.client.treelayout.Configuration.AlignmentInLevel;
import stroom.widget.htree.client.treelayout.Configuration.Location;
import stroom.widget.htree.client.treelayout.TreeLayout;
import stroom.widget.htree.client.treelayout.util.DefaultConfiguration;
import stroom.widget.htree.client.treelayout.util.DefaultTreeForTreeLayout;
import stroom.widget.util.client.MySingleSelectionModel;

import com.google.gwt.canvas.dom.client.Context2d;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HasText;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

/// Stories for Stroom's horizontal tree (`stroom.widget.htree`), matching `Widgets/Tree/HTree` in
/// the React Storybook: a tree laid out left to right by `AbegoTreeLayout` (Stroom's pipeline
/// layout) or `CenteredParentTreeLayout` (its expression layout), drawn on a [LayeredCanvas] by
/// [TreeRenderer] with [TextCellRenderer]'s shadow boxes and an arrow or bracket connector.
public final class HTreeStories {

    private static final String ON_SELECT = "onSelect";
    // As PipelineTreePanel and ExpressionTreePanel
    private static final double HORIZONTAL_SEPARATION = 20;
    private static final double ARROW_VERTICAL_SEPARATION = 10;
    private static final double BRACKET_VERTICAL_SEPARATION = 0;

    private HTreeStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        // React's renderNode, getKey, nodeClassFor/nodeTitleFor and drag and drop (onMove) are
        // for the React port's own uses; Stroom's text tree draws each node's text in a shadow
        // box, as React's default node does.
        registry.component("Widgets/Tree/HTree", HTreeStories.class)
                .layout(StoryLayout.PADDED)
                // Pipeline layout with click-to-select and an arrow connector (the default)
                .story("Basic", context -> {
                    final Label echo = new Label("Selected: (none)");
                    echo.getElement().getStyle().setProperty("marginTop", "8px");
                    echo.getElement().getStyle().setProperty("fontSize", "12px");
                    final FlowPanel panel = new FlowPanel();
                    panel.add(tree(context, pipelineTree(), false, echo));
                    panel.add(echo);
                    return panel;
                })
                // Deeply nested tree - exercises the recursive parent-centring layout
                .story("Deep", context -> tree(context, deepTree(), false, null))
                // Bracket connector style (ExpressionTreePanel equivalent)
                .story("BracketConnector", context -> tree(context, deepTree(), true, null));
    }

    private static DefaultTreeForTreeLayout<Node> pipelineTree() {
        final Node source = new Node("Source");
        final DefaultTreeForTreeLayout<Node> tree = new DefaultTreeForTreeLayout<>(source);
        final Node parser = add(tree, source, "Parser");
        add(tree, parser, "XML Schema");
        final Node filter = add(tree, source, "Filter");
        add(tree, filter, "XSLT");
        add(tree, filter, "Record Output");
        return tree;
    }

    private static DefaultTreeForTreeLayout<Node> deepTree() {
        final Node root = new Node("Root");
        final DefaultTreeForTreeLayout<Node> tree = new DefaultTreeForTreeLayout<>(root);
        final Node a = add(tree, root, "A");
        final Node a1 = add(tree, a, "A1");
        add(tree, a1, "A1a");
        add(tree, a1, "A1b");
        add(tree, a, "A2");
        add(tree, root, "B");
        final Node c = add(tree, root, "C");
        add(tree, c, "C1");
        return tree;
    }

    private static Node add(final DefaultTreeForTreeLayout<Node> tree, final Node parent, final String text) {
        final Node node = new Node(text);
        tree.addChild(parent, node);
        return node;
    }

    /// Lays the tree out and draws it, as PipelineTreePanel (arrows) or ExpressionTreePanel
    /// (brackets) do, selecting nodes when clicked.
    private static Widget tree(final StoryContext context,
                               final DefaultTreeForTreeLayout<Node> tree,
                               final boolean bracket,
                               final Label echo) {
        final LayeredCanvas canvas = LayeredCanvas.createIfSupported();
        if (canvas == null) {
            return new Label("Canvas isn't supported");
        }
        // The layers in drawing order: the arrow layer is on top, and takes the clicks
        final Context2d shadowContext = canvas.getLayer(TreeRenderer.SHADOW_LAYER).getContext2d();
        final Context2d itemContext = canvas.getLayer(TreeRenderer.ITEM_LAYER).getContext2d();
        final Context2d arrowContext = canvas.getLayer(TreeRenderer.ARROW_LAYER).getContext2d();

        final TextCellRenderer<Node> cellRenderer = new TextCellRenderer<>(shadowContext, itemContext, itemContext);
        final ConnectorRenderer<Node> connectorRenderer = bracket
                ? new BracketConnectorRenderer<>(arrowContext)
                : new ArrowConnectorRenderer<>(arrowContext);
        final TreeLayout<Node> treeLayout;
        if (bracket) {
            treeLayout = new CenteredParentTreeLayout<>(cellRenderer, new DefaultConfiguration<>(
                    HORIZONTAL_SEPARATION, BRACKET_VERTICAL_SEPARATION, Location.Left, AlignmentInLevel.TowardsRoot));
        } else {
            treeLayout = new AbegoTreeLayout<>(cellRenderer, new DefaultConfiguration<>(
                    HORIZONTAL_SEPARATION, ARROW_VERTICAL_SEPARATION, Location.Left, AlignmentInLevel.TowardsRoot));
        }
        treeLayout.setTree(tree);

        final TreeRenderer<Node> renderer = new TreeRenderer<>(canvas, cellRenderer, connectorRenderer);
        renderer.setTreeLayout(treeLayout);
        final MySingleSelectionModel<Node> selectionModel = new MySingleSelectionModel<>();
        final Spy onSelect = context.fn(ON_SELECT);
        selectionModel.addSelectionChangeHandler(event -> {
            final Node selected = selectionModel.getSelectedObject();
            onSelect.call(selected == null
                    ? null
                    : selected.getText());
            if (echo != null) {
                echo.setText("Selected: " + (selected == null
                        ? "(none)"
                        : selected.getText()));
            }
        });
        renderer.setSelectionModel(selectionModel);
        renderer.draw();

        final FlowPanel panel = new FlowPanel();
        panel.getElement().getStyle().setProperty("position", "relative");
        panel.add(canvas);
        return panel;
    }


    // --------------------------------------------------------------------------------


    /// A node of the tree, with its text.
    private static final class Node implements HasText {

        private String text;

        private Node(final String text) {
            this.text = text;
        }

        @Override
        public String getText() {
            return text;
        }

        @Override
        public void setText(final String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return text;
        }
    }
}
