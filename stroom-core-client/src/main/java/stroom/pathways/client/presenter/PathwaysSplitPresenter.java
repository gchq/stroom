package stroom.pathways.client.presenter;

import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.pathways.client.presenter.PathwaysSplitPresenter.PathwaysSplitView;
import stroom.pathways.shared.PathwaysDoc;
import stroom.pathways.shared.pathway.PathNode;
import stroom.pathways.shared.pathway.Pathway;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.widget.htree.client.treelayout.Point;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

import java.util.concurrent.atomic.AtomicInteger;

public class PathwaysSplitPresenter extends DocumentEditPresenter<PathwaysSplitView, PathwaysDoc> {

    private final PathwayListPresenter pathwayListPresenter;

    private final static int ROW_HEIGHT = 22;
    private final static int INDENT = 20;
    private final static int START_X_OFFSET = -7;
    private final static int START_Y_OFFSET = 0;
    private final static int END_X_OFFSET = 3;
    private final static int END_Y_OFFSET = -9;

    @Inject
    public PathwaysSplitPresenter(final EventBus eventBus,
                                  final PathwaysSplitView view,
                                  final PathwayListPresenter pathwayListPresenter) {
        super(eventBus, view);
        this.pathwayListPresenter = pathwayListPresenter;
        view.setTable(pathwayListPresenter.getView());
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(pathwayListPresenter.getSelectionModel().addSelectionHandler(e -> {
            final Pathway selected = pathwayListPresenter.getSelectionModel().getSelected();
            final HtmlBuilder hb = new HtmlBuilder();
            hb.div(div -> {
                if (selected != null) {

                    // Draw bezier curves.
                    final HtmlBuilder svgBuilder = new HtmlBuilder();
                    final HtmlBuilder nodeBuilder = new HtmlBuilder();
                    nodeBuilder.div(rootNodesElement ->
                            svgBuilder.elem(rootSvgElement -> append(rootNodesElement,
                                            selected.getRoot(),
                                            rootSvgElement,
                                            1,
                                            new AtomicInteger()),
                                    SafeHtmlUtil.from("svg"),
                                    new Attribute("width", "400"),
                                    new Attribute("height", "500"),
                                    new Attribute("xmlns", "http://www.w3.org/2000/svg")));

                    div.div(d -> d.append(svgBuilder.toSafeHtml()), Attribute.className("pathway-curves"));
                    div.div(d -> d.append(nodeBuilder.toSafeHtml()), Attribute.className("pathway-nodes"));
                }
            }, Attribute.className("pathway"));
            getView().setDetails(hb.toSafeHtml());
        }));
    }

    private void append(final HtmlBuilder hb,
                        final PathNode node,
                        final HtmlBuilder svg,
                        final int depth,
                        final AtomicInteger count) {
        hb.div(d -> {
            d.div(icon ->
                            icon.appendTrustedString(SvgImage.PATHWAYS_NODE.getSvg()),
                    Attribute.className("pathway-nodeIcon svgIcon " +
                                        SvgImage.PATHWAYS_NODE.getClassName()));
            d.div(n -> n.append(node.getName()),
                    Attribute.className("pathway-nodeName"));
        }, Attribute.className("pathway-node"));

        final int parentRowNum = count.incrementAndGet();

        NullSafe.list(node.getTargets()).forEach(target -> {
            // Add bezier curve to target set.
            final int targetRowNum = count.incrementAndGet();
            appendBezier(svg, depth, parentRowNum, targetRowNum);

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
                    appendBezier(svg, nodeDepth, targetRowNum, nodeRowNum);

                    // Add node div.
                    parentLevel.div(childLevel -> {
                        append(childLevel, pathNode, svg, nodeDepth + 1, count);
                    }, Attribute.className("pathway-level"));
                });

            }, Attribute.className("pathway-level"));
        });
    }

    private void appendBezier(final HtmlBuilder svg,
                              final int depth,
                              final int startRow,
                              final int endRow) {
        final int startX = (depth * INDENT) + START_X_OFFSET;
        final int startY = (startRow * ROW_HEIGHT) + START_Y_OFFSET;
        final int endX = (depth * INDENT) + END_X_OFFSET;
        final int endY = (endRow * ROW_HEIGHT) + END_Y_OFFSET;
        Bezier.curve(svg, new Point(startX, startY), new Point(endX, endY));
    }

    @Override
    protected void onRead(final DocRef docRef, final PathwaysDoc document, final boolean readOnly) {
        pathwayListPresenter.onRead(docRef, document, readOnly);
    }

    @Override
    protected PathwaysDoc onWrite(final PathwaysDoc document) {
        return pathwayListPresenter.onWrite(document);
    }

    public interface PathwaysSplitView extends View {

        void setTable(View view);

        void setDetails(SafeHtml html);
    }
}
