package stroom.pathways.client.presenter;

import stroom.pathways.client.presenter.PathwayTreePresenter.PathwayTreeView;
import stroom.pathways.shared.pathway.PathNode;
import stroom.pathways.shared.pathway.Pathway;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ButtonView;
import stroom.widget.htree.client.treelayout.Point;
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
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class PathwayTreePresenter
        extends MyPresenterWidget<PathwayTreeView> {

    private static final int ROW_HEIGHT = 22;
    private static final int INDENT = 56;
    private static final int START_X_OFFSET = 13;
    private static final int START_Y_OFFSET = 0;
    private static final int END_X_OFFSET = 23;
    private static final int END_Y_OFFSET = -9;

    private final ButtonView newButton;
    private final ButtonView editButton;
    private final ButtonView removeButton;

    private final HTML html;
    private final MySingleSelectionModel<PathNode> selectionModel = new MySingleSelectionModel<PathNode>();

    private Pathway pathway;
    private Element selected;
    private boolean readOnly = true;
    private final Map<String, PathNode> nodeMap = new HashMap<>();

    @Inject
    public PathwayTreePresenter(final EventBus eventBus,
                                final PathwayTreeView view) {
        super(eventBus, view);
        newButton = view.addButton(SvgPresets.NEW_ITEM);
        editButton = view.addButton(SvgPresets.EDIT);
        removeButton = view.addButton(SvgPresets.DELETE);
        enableButtons();

        html = new HTML();
        html.addStyleName("max");
        view.setDataWidget(html);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(html.addClickHandler(e -> {
            final Element target = e.getNativeEvent().getEventTarget().cast();
            if (target != null) {
                final Element node = ElementUtil.findParent(target, element ->
                        NullSafe.isNonBlankString(element.getAttribute("uuid")), 3);
                if (node != null) {
                    final String uuid = node.getAttribute("uuid");
//                    AlertEvent.fireInfo(this, uuid, null);

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

    public void read(final Pathway pathway, final boolean readOnly) {
        this.readOnly = readOnly;
        this.pathway = pathway;
        this.selected = null;

        nodeMap.clear();

        final HtmlBuilder hb = new HtmlBuilder();
        hb.div(div -> {
            if (pathway != null) {
                addNode(pathway.getRoot());

                // Draw bezier curves.
                final HtmlBuilder svgBuilder = new HtmlBuilder();
                final HtmlBuilder nodeBuilder = new HtmlBuilder();
                final AtomicInteger count = new AtomicInteger();
                final AtomicInteger width = new AtomicInteger();
                final AtomicInteger height = new AtomicInteger();

                append(nodeBuilder,
                        pathway.getRoot(),
                        svgBuilder,
                        0,
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
                        final AtomicInteger count,
                        final AtomicInteger width,
                        final AtomicInteger height) {
        hb.div(d -> {
            // Render node icon and text.
            d.div(nodeDiv -> {
                nodeDiv.div(icon ->
                                icon.appendTrustedString(SvgImage.PATHWAYS_NODE.getSvg()),
                        Attribute.className("pathway-nodeIcon svgIcon " +
                                            SvgImage.PATHWAYS_NODE.getClassName()));
                nodeDiv.div(n -> n.append(node.getName()),
                        Attribute.className("pathway-nodeName"), new Attribute("uuid", node.getUuid()));
            }, Attribute.className("pathway-node"));

            // Add child node targets.
            final int parentRowNum = count.incrementAndGet();

            NullSafe.list(node.getTargets()).forEach(target -> {
                if (!target.getNodes().isEmpty()) {
                    // Add bezier curve to target set.
                    final int targetRowNum = count.get() + 1;
                    appendBezier(svg, nodeDepth, parentRowNum, targetRowNum, width, height);

                    // Add target set.
                    final String choiceCss;
                    if (target.getNodes().isEmpty()) {
                        choiceCss = "pathway-nodeIcon svgIcon " +
                                    SvgImage.PATHWAYS_CHOICE.getClassName() +
                                    " pathway-terminal";
                    } else {
                        choiceCss = "pathway-nodeIcon svgIcon " +
                                    SvgImage.PATHWAYS_CHOICE.getClassName();
                    }

                    d.div(targetDiv -> {
                        targetDiv.div(icon -> icon.appendTrustedString(SvgImage.PATHWAYS_CHOICE.getSvg()),
                                Attribute.className(choiceCss));

                        final int choiceRowNum = count.get();

                        targetDiv.div(targetsDiv -> {
                            target.getNodes().forEach(pathNode -> {
                                // Add bezier curve to this node.
//                                final int nodeDepth = depth + 1;
                                final int nodeRowNum = (count.get() + 1);
//                                    appendBezier(svg, nodeDepth, targetRowNum, nodeRowNum, width, height);


                                final int startX = (nodeDepth * INDENT) + 43;
                                final int startY = (choiceRowNum * ROW_HEIGHT) + 13;
                                final int endX = (nodeDepth * INDENT) + 60;
                                final int endY = (count.get() * ROW_HEIGHT) + 13;
                                Bezier.quadratic(svg, new Point(startX, startY), new Point(endX, endY));


                                if (endX > width.get()) {
                                    width.set(endX);
                                }
                                if (endY > height.get()) {
                                    height.set(endY);
                                }


                                // Add node div.
                                append(targetsDiv, pathNode, svg, nodeDepth + 1, count, width, height);
                            });
                        }, Attribute.className("pathway-targets"));


                    }, Attribute.className("pathway-target"));
                }
            });


        }, Attribute.className("pathway-row"));


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

    private void enableButtons() {
        newButton.setEnabled(!readOnly);
        if (!readOnly) {
            final PathNode pathNode = selectionModel.getSelectedObject();
            final boolean enabled = pathNode != null;
            editButton.setEnabled(enabled);
            removeButton.setEnabled(enabled);
        } else {
            editButton.setEnabled(false);
            removeButton.setEnabled(false);
        }

        if (readOnly) {
            newButton.setTitle("New path disabled as read only");
            editButton.setTitle("Edit path disabled as read only");
            removeButton.setTitle("Remove path disabled as read only");
        } else {
            newButton.setTitle("New Path");
            editButton.setTitle("Edit Path");
            removeButton.setTitle("Remove Path");
        }
    }

    public MySingleSelectionModel<PathNode> getSelectionModel() {
        return selectionModel;
    }

    public interface PathwayTreeView extends View {

        ButtonView addButton(Preset preset);

        void addButton(ButtonView buttonView);

        void setDataWidget(Widget widget);
    }
}
