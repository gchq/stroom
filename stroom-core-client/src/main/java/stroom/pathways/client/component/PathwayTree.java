/*
 * Copyright 2021 Crown Copyright
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

package stroom.pathways.client.component;

import stroom.pathways.shared.pathway.PathNode;
import stroom.pathways.shared.pathway.PathNodeSequence;
import stroom.pathways.shared.pathway.Pathway;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.htree.client.treelayout.Point;
import stroom.widget.text.client.Html;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.SafeHtmlUtil;
import stroom.widget.util.client.SingleItemSelectionModel;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/// A pathway's graph of nodes, which its [PathwayTreeController] fills, under a toolbar of its
/// actions. A node is chosen by clicking its name.
public class PathwayTree extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    private static final int ROW_HEIGHT = 22;
    private static final int INDENT = 20;
    private static final String NEW_TITLE = "New Path";
    private static final String EDIT_TITLE = "Edit Selected Path";
    private static final String DELETE_TITLE = "Delete Selected Path";
    private static final String READ_ONLY_SUFFIX = " (read only)";
    private static final String SELECTED_CLASS = "pathway-tree__node-name--selected";

    @UiField
    ToolbarButton newButton;
    @UiField
    ToolbarButton editButton;
    @UiField
    ToolbarButton removeButton;
    @UiField
    ToolbarButton viewTracesButton;
    @UiField
    Html tree;

    private final SingleItemSelectionModel<PathNode> selectionModel = new SingleItemSelectionModel<>();
    private final Map<String, PathNode> nodeMap = new HashMap<>();
    private Element selected;
    // Read only until a pathway is read, and only shown on the buttons when a node is chosen, as it
    // was (see ISSUES.md)
    private boolean readOnly = true;
    private Runnable onViewTraces = () -> {
    };

    /// Makes the graph, with no pathway yet.
    public PathwayTree() {
        initWidget(BINDER.createAndBindUi(this));
        viewTracesButton.setOnClick(() -> onViewTraces.run());
        //noinspection unused e
        tree.addClickHandler(e -> {
            final Element target = e.getNativeEvent().getEventTarget().cast();
            if (target != null) {
                final Element node = ElementUtil.findParent(target, element ->
                        NullSafe.isNonBlankString(element.getAttribute("uuid")), 3);
                if (node != null) {
                    final String uuid = node.getAttribute("uuid");
                    if (!Objects.equals(selected, node)) {
                        if (selected != null) {
                            selected.removeClassName(SELECTED_CLASS);
                        }
                        selected = node;
                        selected.addClassName(SELECTED_CLASS);
                        selectionModel.setSelected(nodeMap.get(uuid), true);
                        enableButtons();
                    }
                }
            }
        });
        enableButtons();
    }

    /// @param onViewTraces Run when View Matching Traces is pressed, with a node chosen.
    public void setOnViewTraces(final Runnable onViewTraces) {
        this.onViewTraces = onViewTraces;
    }

    /// @return The chosen node.
    public SingleItemSelectionModel<PathNode> getSelectionModel() {
        return selectionModel;
    }

    /// Draws a pathway's graph, with no node chosen.
    ///
    /// @param pathway The pathway, or null for none.
    public void read(final Pathway pathway) {
        // Read only as its document is, when it is read
        this.readOnly = isReadOnly();
        this.selected = null;
        nodeMap.clear();

        final HtmlBuilder hb = new HtmlBuilder();
        hb.div(div -> {
            // A new pathway has no nodes yet
            if (pathway != null && pathway.getRoot() != null) {
                addNode(pathway.getRoot());

                // Draw bezier curves.
                final HtmlBuilder svgBuilder = new HtmlBuilder();
                final HtmlBuilder nodeBuilder = new HtmlBuilder();
                final AtomicInteger rowNum = new AtomicInteger();
                final AtomicInteger width = new AtomicInteger();
                final AtomicInteger height = new AtomicInteger();

                append(nodeBuilder,
                        pathway.getRoot(),
                        svgBuilder,
                        0,
                        rowNum,
                        width,
                        height);

                div.div(d -> {
                    d.elem(rootSvgElement -> rootSvgElement.append(svgBuilder.toSafeHtml()),
                            SafeHtmlUtil.from("svg"),
                            new Attribute("width", String.valueOf(width.get() + 10)),
                            new Attribute("height", String.valueOf(height.get() + 10)),
                            new Attribute("xmlns", "http://www.w3.org/2000/svg"));
                }, Attribute.className("pathway-tree__curves"));
                div.div(d -> d.append(nodeBuilder.toSafeHtml()), Attribute.className("pathway-tree__nodes"));
            }
        }, Attribute.className("pathway-tree"));
        tree.setHTML(hb.toSafeHtml());
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        this.readOnly = readOnly;
    }

    private void addNode(final PathNode node) {
        nodeMap.put(node.getUuid(), node);
        node.getTargets().forEach(target -> {
            target.getNodes().forEach(this::addNode);
        });
    }

    private void append(final HtmlBuilder hb,
                        final PathNode node,
                        final HtmlBuilder svg,
                        final int nodeDepth,
                        final AtomicInteger rowNum,
                        final AtomicInteger width,
                        final AtomicInteger height) {
        final int sourceRowNum = rowNum.incrementAndGet();

        // Render node icon and text.
        hb.div(nodeDiv -> {
            nodeDiv.div(icon ->
                            icon.appendTrustedString(SvgImage.PATHWAYS_NODE.getSvg()),
                    Attribute.className("pathway-tree__node-icon icon-wrap " +
                                        SvgImage.PATHWAYS_NODE.getClassName()));
            nodeDiv.div(n -> n.append(node.getName()),
                    Attribute.className("pathway-tree__node-name"), new Attribute("uuid", node.getUuid()));
        }, Attribute.className("pathway-tree__node"));

        // Add child node targets.
        final List<PathNodeSequence> targets = NullSafe.list(node.getTargets());
        if (targets.size() > 1) {
            // Add bezier curve to target set.
            appendBezier(svg, nodeDepth, sourceRowNum, rowNum.get(), width, height);

            // Add target set.
            final String choiceCss = "pathway-tree__node-icon icon-wrap " +
                                     SvgImage.PATHWAYS_CHOICE.getClassName();

            hb.div(targetsOuterDiv -> {
                targetsOuterDiv.div(icon -> icon.appendTrustedString(SvgImage.PATHWAYS_CHOICE.getSvg()),
                        Attribute.className(choiceCss));

                targetsOuterDiv.div(inner -> {
                    targets.forEach(target -> {

                        // Add quadratic curve to this node.
                        appendQuadratic(svg, nodeDepth + 2, sourceRowNum, rowNum.get(), width, height);

                        addTargets(inner, target, svg, nodeDepth + 3, rowNum, width, height);
                    });
                }, Attribute.className("pathway-tree__targets-inner"));


            }, Attribute.className("pathway-tree__targets-outer"));

        } else if (!targets.isEmpty()) {
            final PathNodeSequence target = targets.get(0);
            if (!target.getNodes().isEmpty()) {
                // Add bezier curve to target set.
                appendBezier(svg, nodeDepth, sourceRowNum, rowNum.get(), width, height);

                addTargets(hb, target, svg, nodeDepth + 1, rowNum, width, height);
            }
        }
    }

    private void addTargets(final HtmlBuilder hb,
                            final PathNodeSequence target,
                            final HtmlBuilder svg,
                            final int nodeDepth,
                            final AtomicInteger rowNum,
                            final AtomicInteger width,
                            final AtomicInteger height) {
        if (!target.getNodes().isEmpty()) {
            final int sourceRowNum = rowNum.get();

            // Add target set.
            final String choiceCss = "pathway-tree__node-icon icon-wrap " +
                                     SvgImage.PATHWAYS_SEQUENCE.getClassName();

            hb.div(targetDiv -> {
                targetDiv.div(icon -> icon.appendTrustedString(SvgImage.PATHWAYS_SEQUENCE.getSvg()),
                        Attribute.className(choiceCss));

                targetDiv.div(o -> {

                    o.div(targetsDiv -> {
                        target.getNodes().forEach(pathNode -> {
                            // Add quadratic curve to this node.
                            appendQuadratic(svg, nodeDepth + 1, sourceRowNum, rowNum.get(), width, height);

                            // Add node div.
                            append(targetsDiv,
                                    pathNode,
                                    svg,
                                    nodeDepth + 2,
                                    rowNum,
                                    width,
                                    height);
                        });
                    }, Attribute.className("pathway-tree__target-inner"));
                }, Attribute.className("pathway-tree__targets-inner"));


            }, Attribute.className("pathway-tree__target"));
        }
    }

    private void appendBezier(final HtmlBuilder svg,
                              final int depth,
                              final int startRow,
                              final int endRow,
                              final AtomicInteger width,
                              final AtomicInteger height) {
        final int startX = (depth * INDENT) + 8;
        final int startY = (startRow * ROW_HEIGHT) - 4;
        final int endX = (depth * INDENT) + 18;
        final int endY = (endRow * ROW_HEIGHT) + 8;
        Bezier.curve(svg, new Point(startX, startY), new Point(endX, endY));

        if (endX > width.get()) {
            width.set(endX);
        }
        if (endY > height.get()) {
            height.set(endY);
        }
    }

    private void appendQuadratic(final HtmlBuilder svg,
                                 final int depth,
                                 final int startRow,
                                 final int endRow,
                                 final AtomicInteger width,
                                 final AtomicInteger height) {
        final int startX = (depth * INDENT) - 2;
        final int startY = (startRow * ROW_HEIGHT) + 8;
        final int endX = (depth * INDENT) + 18;
        final int endY = (endRow * ROW_HEIGHT) + 8;
        Bezier.quadratic(svg, new Point(startX, startY), new Point(endX, endY));
        if (endX > width.get()) {
            width.set(endX);
        }
        if (endY > height.get()) {
            height.set(endY);
        }
    }

    private void enableButtons() {
        newButton.setEnabled(!readOnly);
        final PathNode pathNode = selectionModel.getSelectedObject();
        if (!readOnly) {
            final boolean enabled = pathNode != null;
            editButton.setEnabled(enabled);
            removeButton.setEnabled(enabled);
        } else {
            editButton.setEnabled(false);
            removeButton.setEnabled(false);
        }
        viewTracesButton.setEnabled(pathNode != null);

        final String readOnlySuffix = readOnly
                ? READ_ONLY_SUFFIX
                : "";
        newButton.setTitle(NEW_TITLE + readOnlySuffix);
        editButton.setTitle(EDIT_TITLE + readOnlySuffix);
        removeButton.setTitle(DELETE_TITLE + readOnlySuffix);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, PathwayTree> {

    }
}
