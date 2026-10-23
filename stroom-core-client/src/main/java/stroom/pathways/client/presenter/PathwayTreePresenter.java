/*
 * Copyright 2025 Crown Copyright
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

package stroom.pathways.client.presenter;

import stroom.pathways.client.presenter.PathwayTreePresenter.PathwayTreeView;
import stroom.pathways.shared.PathwaysDoc;
import stroom.pathways.shared.pathway.PathNode;
import stroom.pathways.shared.pathway.PathNodeSequence;
import stroom.pathways.shared.pathway.Pathway;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ButtonView;
import stroom.widget.button.client.Toolbar;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Contexts;
import stroom.widget.htree.client.treelayout.Point;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.presenter.client.View;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.MySingleSelectionModel;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class PathwayTreePresenter
        extends MyPresenterWidget<PathwayTreeView> {

    private static final int ROW_HEIGHT = 22;
    private static final int INDENT = 20;
    private static final String NEW_TITLE = "New Path";
    private static final String EDIT_TITLE = "Edit Selected Path";
    private static final String DELETE_TITLE = "Delete Selected Path";
    private static final String READ_ONLY_SUFFIX = " (read only)";

    private final ButtonView newButton;
    private final ButtonView editButton;
    private final ButtonView removeButton;
    private final ToolbarButton viewTracesButton;

    private final HTML html;
    private final MySingleSelectionModel<PathNode> selectionModel = new MySingleSelectionModel<PathNode>();

    private PathwaysDoc pathwaysDoc;
    private Pathway pathway;
    private Element selected;
    private boolean readOnly = true;
    private final Map<String, PathNode> nodeMap = new HashMap<>();

    @Inject
    public PathwayTreePresenter(final EventBus eventBus,
                                final PathwayTreeView view) {
        super(eventBus, view);
        newButton = view.getToolbar().button(SvgImage.ADD, NEW_TITLE, null);
        editButton = view.getToolbar().button(SvgImage.EDIT, EDIT_TITLE, null);
        editButton.setEnabled(false);
        removeButton = view.getToolbar().button(SvgImage.DELETE, DELETE_TITLE, null);
        removeButton.setEnabled(false);

        viewTracesButton = view.getToolbar().button(SvgImage.EYE, "View Matching Traces", this::onViewTraces);
        enableButtons();

        html = new HTML();
        html.addStyleName("max");
        view.setDataWidget(html);

        own(html.addClickHandler(e -> {
            final Element target = e.getNativeEvent().getEventTarget().cast();
            if (target != null) {
                final Element node = ElementUtil.findParent(target, element ->
                        NullSafe.isNonBlankString(element.getAttribute("uuid")), 3);
                if (node != null) {
                    final String uuid = node.getAttribute("uuid");
                    if (!Objects.equals(selected, node)) {
                        if (selected != null) {
                            selected.removeClassName("pathway-nodeName--selected");
                        }
                        selected = node;
                        selected.addClassName("pathway-nodeName--selected");
                        selectionModel.setSelected(nodeMap.get(uuid), true);
                        enableButtons();
                    }
                }
            }
        }));
    }

    private void onViewTraces() {
        final Map<String, String> parentMap = new HashMap<>();
        final Map<String, PathNodeSequence> pathNodeSequenceMap = new HashMap<>();
        final Map<String, PathNode> pathNodeMap = new HashMap<>();
        addToMap(pathway.getRoot(), parentMap, pathNodeSequenceMap, pathNodeMap);

        PathNode pathNode = selectionModel.getSelectedObject();
        PathNode root = pathNode;
        while (pathNode != null) {
            // Get parent sequence.
            final String parentSequenceUuid = parentMap.get(pathNode.getUuid());
            if (parentSequenceUuid != null) {
                final PathNodeSequence pathNodeSequence = pathNodeSequenceMap.get(parentSequenceUuid);
                // Get parent node.
                final String parentNodeUuid = parentMap.get(pathNodeSequence.getUuid());
                pathNode = pathNodeMap.get(parentNodeUuid);

                root = pathNode.copy().targets(Collections.singletonList(pathNodeSequence)).build();
            } else {
                pathNode = null;
            }
        }

        final Pathway pathway;
        if (root != null) {
            pathway = this.pathway.copy().root(root).build();
        } else {
            pathway = this.pathway;
        }

        ShowTracesEvent.fire(
                this,
                pathwaysDoc.getTracesDocRef(),
                null,
                pathway);
    }

    private void addToMap(final PathNode pathNode,
                          final Map<String, String> parentMap,
                          final Map<String, PathNodeSequence> pathNodeSequenceMap,
                          final Map<String, PathNode> pathNodeMap) {
        pathNodeMap.put(pathNode.getUuid(), pathNode);
        NullSafe.list(pathNode.getTargets()).forEach(target -> {
            pathNodeSequenceMap.put(target.getUuid(), target);
            parentMap.put(target.getUuid(), pathNode.getUuid());
            NullSafe.list(target.getNodes()).forEach(node -> {
                parentMap.put(node.getUuid(), target.getUuid());
                addToMap(node, parentMap, pathNodeSequenceMap, pathNodeMap);
            });
        });
    }

    public void read(final PathwaysDoc pathwaysDoc,
                     final Pathway pathway) {
        // Read only as its document is
        final boolean readOnly = context(Contexts.READ_ONLY);
        this.pathwaysDoc = pathwaysDoc;
        this.pathway = pathway;
        this.readOnly = readOnly;
        this.selected = null;

        nodeMap.clear();

        final HtmlBuilder hb = new HtmlBuilder();
        hb.div(div -> {
            if (pathway != null) {
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
                }, Attribute.className("pathway-curves"));
                div.div(d -> d.append(nodeBuilder.toSafeHtml()), Attribute.className("pathway-nodes"));
            }
        }, Attribute.className("pathway"));
        html.setHTML(hb.toSafeHtml());
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
                    Attribute.className("pathway-nodeIcon svgIcon " +
                                        SvgImage.PATHWAYS_NODE.getClassName()));
            nodeDiv.div(n -> n.append(node.getName()),
                    Attribute.className("pathway-nodeName"), new Attribute("uuid", node.getUuid()));
        }, Attribute.className("pathway-node"));

        // Add child node targets.
        final List<PathNodeSequence> targets = NullSafe.list(node.getTargets());
        if (targets.size() > 1) {
            // Add bezier curve to target set.
            appendBezier(svg, nodeDepth, sourceRowNum, rowNum.get(), width, height);

            // Add target set.
            final String choiceCss = "pathway-nodeIcon svgIcon " +
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
                }, Attribute.className("pathway-targets-inner"));


            }, Attribute.className("pathway-targets-outer"));

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
            final String choiceCss = "pathway-nodeIcon svgIcon " +
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
                    }, Attribute.className("pathway-target-inner"));
                }, Attribute.className("pathway-targets-inner"));


            }, Attribute.className("pathway-target"));
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

    public MySingleSelectionModel<PathNode> getSelectionModel() {
        return selectionModel;
    }

    public interface PathwayTreeView extends View {

        /// The toolbar for the view's buttons, e.g. `getToolbar().button(SvgImage.ADD, "Add", this::add)`.
        ///
        /// @return The toolbar.
        Toolbar getToolbar();

        void setDataWidget(Widget widget);
    }
}
