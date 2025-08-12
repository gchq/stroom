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

import java.util.concurrent.atomic.AtomicInteger;

public class PathwayTree {

    private final static int ROW_HEIGHT = 22;
    private final static int INDENT = 20;
    private final static int START_X_OFFSET = -7;
    private final static int START_Y_OFFSET = 0;
    private final static int END_X_OFFSET = 3;
    private final static int END_Y_OFFSET = -9;

    public SafeHtml build(final Pathway selected) {
        final HtmlBuilder hb = new HtmlBuilder();
        hb.div(div -> {
            if (selected != null) {

                // Draw bezier curves.
                final HtmlBuilder svgBuilder = new HtmlBuilder();
                final HtmlBuilder nodeBuilder = new HtmlBuilder();
                final AtomicInteger count = new AtomicInteger();
                final AtomicInteger width = new AtomicInteger();
                final AtomicInteger height = new AtomicInteger();

                append(nodeBuilder,
                        selected.getRoot(),
                        svgBuilder,
                        1,
                        count,
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
        return hb.toSafeHtml();
    }

    private void append(final HtmlBuilder hb,
                        final PathNode node,
                        final HtmlBuilder svg,
                        final int depth,
                        final AtomicInteger count,
                        final AtomicInteger width,
                        final AtomicInteger height) {
        hb.div(d -> {
            d.div(icon ->
                            icon.appendTrustedString(SvgImage.PATHWAYS_NODE.getSvg()),
                    Attribute.className("pathway-nodeIcon svgIcon " +
                                        SvgImage.PATHWAYS_NODE.getClassName()));
            d.div(n -> n.append(node.getName()),
                    Attribute.className("pathway-nodeName"), new Attribute("uuid", node.getUuid()));
        }, Attribute.className("pathway-node"));

        final int parentRowNum = count.incrementAndGet();

        NullSafe.list(node.getTargets()).forEach(target -> {
            // Add bezier curve to target set.
            final int targetRowNum = count.incrementAndGet();
            appendBezier(svg, depth, parentRowNum, targetRowNum, width, height);

            // Add target set.
            hb.div(parentLevel -> {

                final String choiceCss;
                if (target.getNodes().isEmpty()) {
                    choiceCss = "pathway-nodeIcon svgIcon " +
                                SvgImage.PATHWAYS_CHOICE.getClassName() +
                                " pathway-terminal";
                } else {
                    choiceCss = "pathway-nodeIcon svgIcon " +
                                SvgImage.PATHWAYS_CHOICE.getClassName();
                }

                parentLevel.div(d -> {
                    d.div(icon -> icon.appendTrustedString(SvgImage.PATHWAYS_CHOICE.getSvg()),
                            Attribute.className(choiceCss));
                    d.div(n -> n.append(target.getPathKey().toString()),
                            Attribute.className("pathway-nodeName pathway-targetName"));
                }, Attribute.className("pathway-node pathway-target"));

                target.getNodes().forEach(pathNode -> {

                    // Add bezier curve to this node.
                    final int nodeDepth = depth + 1;
                    final int nodeRowNum = (count.get() + 1);
                    appendBezier(svg, nodeDepth, targetRowNum, nodeRowNum, width, height);

                    // Add node div.
                    parentLevel.div(childLevel -> {
                        append(childLevel, pathNode, svg, nodeDepth + 1, count, width, height);
                    }, Attribute.className("pathway-level"));
                });

            }, Attribute.className("pathway-level"));
        });
    }

    private void appendBezier(final HtmlBuilder svg,
                              final int depth,
                              final int startRow,
                              final int endRow,
                              final AtomicInteger width,
                              final AtomicInteger height) {
        final int startX = (depth * INDENT) + START_X_OFFSET;
        final int startY = (startRow * ROW_HEIGHT) + START_Y_OFFSET;
        final int endX = (depth * INDENT) + END_X_OFFSET;
        final int endY = (endRow * ROW_HEIGHT) + END_Y_OFFSET;
        Bezier.curve(svg, new Point(startX, startY), new Point(endX, endY));

        if (endX > width.get()) {
            width.set(endX);
        }
        if (endY > height.get()) {
            height.set(endY);
        }
    }
}
