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

package stroom.pathways.client.presenter;

import stroom.pathways.shared.pathway.PathNode;
import stroom.pathways.shared.pathway.Pathway;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.widget.htree.client.treelayout.Point;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.safehtml.shared.SafeHtml;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The model as an indented tree, a row per node, with curves joining a node to what was seen beneath
 * it. The shape the pathway view has always had.
 */
class PathwayTreeRenderer implements PathwayRenderer {

    private static final int ROW_HEIGHT = 22;
    private static final int INDENT = 20;

    // The nodes the path being looked at ran, for as long as one drawing takes.
    private Set<String> onPath = Collections.emptySet();

    @Override
    public boolean opensCentred() {
        // Rows run down from the root, so the top left is where the model starts.
        return false;
    }

    @Override
    public boolean isPannable() {
        // Rows read top to bottom. There is nowhere to move to that scrolling does not reach.
        return false;
    }

    @Override
    public boolean isZoomable() {
        return false;
    }

    @Override
    public boolean usesHistory() {
        // Names and constraints only. What has changed and when is not drawn here.
        return false;
    }

    @Override
    public boolean onControl(final String id, final PathwayControls controls) {
        // No buttons of its own.
        return false;
    }

    @Override
    public SafeHtml render(final RenderRequest request) {
        final Pathway pathway = request.getPathway();
        onPath = NullSafe.set(request.getOnPath());
        final HtmlBuilder hb = new HtmlBuilder();
        hb.div(div -> {
            if (pathway != null) {
                final HtmlBuilder svgBuilder = new HtmlBuilder();
                final HtmlBuilder nodeBuilder = new HtmlBuilder();
                final AtomicInteger rowNum = new AtomicInteger();
                final AtomicInteger width = new AtomicInteger();
                final AtomicInteger height = new AtomicInteger();

                append(nodeBuilder, pathway.getRoot(), svgBuilder, 0, rowNum, width, height);

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
        return hb.toSafeHtml();
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
        }, Attribute.className(onPath.isEmpty() || onPath.contains(node.getUuid())
                ? "pathway-node"
                : "pathway-node pathway-node--off-path"));

        // Add the things seen beneath this node.
        final List<PathNode> children = NullSafe.list(node.getChildren());
        if (!children.isEmpty()) {
            // Add bezier curve to child set.
            appendBezier(svg, nodeDepth, sourceRowNum, rowNum.get(), width, height);

            addChildren(hb, children, svg, nodeDepth + 1, rowNum, width, height);
        }
    }

    private void addChildren(final HtmlBuilder hb,
                             final List<PathNode> children,
                             final HtmlBuilder svg,
                             final int nodeDepth,
                             final AtomicInteger rowNum,
                             final AtomicInteger width,
                             final AtomicInteger height) {
        if (!children.isEmpty()) {
            final int sourceRowNum = rowNum.get();

            // Add child set.
            final String choiceCss = "pathway-nodeIcon svgIcon " +
                                     SvgImage.PATHWAYS_SEQUENCE.getClassName();

            hb.div(targetDiv -> {
                targetDiv.div(icon -> icon.appendTrustedString(SvgImage.PATHWAYS_SEQUENCE.getSvg()),
                        Attribute.className(choiceCss));

                targetDiv.div(o -> {

                    o.div(targetsDiv -> {
                        children.forEach(pathNode -> {
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
}
