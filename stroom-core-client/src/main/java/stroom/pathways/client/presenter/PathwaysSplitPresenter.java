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
                    div.div(d -> {
                        d.elem(svg -> {
                                    appendBezier(svg, selected.getRoot(), 1, new AtomicInteger());
                                }, SafeHtmlUtil.from("svg"),
                                new Attribute("width", "400"),
                                new Attribute("height", "500"),
                                new Attribute("xmlns", "http://www.w3.org/2000/svg"));
                    }, Attribute.className("pathway-bezier"));

                    append(div, selected.getRoot());

                }
            }, Attribute.className("pathway"));
            getView().setDetails(hb.toSafeHtml());
        }));
    }

    private void append(final HtmlBuilder hb,
                        final PathNode node) {
        hb.div(d -> {
            d.div(icon ->
                            icon.appendTrustedString(SvgImage.PATHWAYS_NODE.getSvg()),
                    Attribute.className("pathway-nodeIcon svgIcon " +
                                        SvgImage.PATHWAYS_NODE.getClassName()));
            d.div(n -> n.append(node.getName()),
                    Attribute.className("pathway-nodeName"));
        }, Attribute.className("pathway-node"));

        NullSafe.list(node.getTargets()).forEach(target -> {
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

                target.getNodes().forEach(pathNode ->
                        parentLevel.div(childLevel ->
                                append(childLevel, pathNode), Attribute.className("pathway-level")));

            }, Attribute.className("pathway-level"));
        });
    }

    private void appendBezier(final HtmlBuilder hb,
                              final PathNode node,
                              final int depth,
                              final AtomicInteger count) {
        final int rowHeight = 22;
        final int indent = 20;

        final int start = count.incrementAndGet();

        NullSafe.list(node.getTargets()).forEach(target -> {
            final int end = count.incrementAndGet();
            final int startX = (depth * indent) - 7;
            final int startY = (start * rowHeight);
            final int endX = (depth * indent) + 3;
            final int endY = (end * rowHeight) - 9;

            Bezier.curve(hb, new Point(startX, startY), new Point(endX, endY));

            target.getNodes().forEach(child -> {
                final int posBefore = (count.get() + 1);

                appendBezier(hb, child, depth + 2, count);


                final int startX2 = ((depth + 1) * indent) - 7;
                final int startY2 = (end * rowHeight);
                final int endX2 = ((depth + 1) * indent) + 3;
                final int endY2 = (posBefore * rowHeight) - 9;

                Bezier.curve(hb, new Point(startX2, startY2), new Point(endX2, endY2));
            });
        });


//        int rowHeight = 22;
//        int indent = 22;
//        int startX = depth * indent;
//        int startY = (start * rowHeight) - 11;
////        int midX = (depth * indent) + (indent / 2);
////        int midY = (((count.get() - start) * rowHeight) / 2) + startY;
//        int endX = (depth * indent) + indent;
//        int endY = (((count.get() - start) * rowHeight)) + startY;
//
//        Bezier.quadratic(hb, new Point(startX, startY), new Point(endX, endY));


//        hb.elem(path -> {
//                }, SafeHtmlUtil.from("path"),
//                new Attribute("d",
//                        "M " + startX + " " + startY +
//                        " Q " + (startX + 30) + " " + startY +
//                        ", " + midX + " " + midY +
//                        " T " + endX + " " + endY));

//        <svg width="190" height="160" xmlns="http://www.w3.org/2000/svg">
//  <path
//                d="M 10 80 C 40 10, 65 10, 95 80 S 150 150, 180 80"
//        stroke="black"
//        fill="transparent" />
//</svg>
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
