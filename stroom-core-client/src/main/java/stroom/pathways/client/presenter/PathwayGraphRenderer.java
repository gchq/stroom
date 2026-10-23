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

import stroom.pathways.shared.otel.trace.NanoTime;
import stroom.pathways.shared.pathway.NodeUsage;
import stroom.pathways.shared.pathway.PathNode;
import stroom.pathways.shared.pathway.Pathway;
import stroom.util.shared.NullSafe;
import stroom.widget.htree.client.treelayout.Point;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.animation.client.Animation;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Node;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.safehtml.shared.SafeHtml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The model as a network: the operation at the centre and everything seen beneath it on rings around
 * it, one ring per level.
 *
 * <p>Says two things the tree cannot. How large a node is drawn is how much has gone through it, so a
 * node one trace reaches forty times stands out from one it reaches once. What colour it is drawn is
 * when it last changed, so the parts of the model still being learnt can be told at a glance from the
 * parts that settled long ago.
 *
 * <p>Edges are drawn in SVG behind markers drawn in HTML, the way the tree draws its curves, so the
 * element a click lands on is an HTML one — selecting puts a class on it, and that does not work on an
 * SVG element.
 */
class PathwayGraphRenderer implements PathwayRenderer {

    private static final int RING = 170;
    // Room outside the outermost nodes. Wider to the sides than above and below, because a name is
    // written beside its node rather than over it: sideways a node reaches its own radius plus the
    // gap plus the width its name is clamped to (26 + 6 + 110 in the stylesheet), while upwards it
    // reaches only its own radius. One margin for both would leave a strip of dead space along the
    // top and bottom that nothing can ever occupy.
    private static final int SIDE_MARGIN = 150;
    private static final int END_MARGIN = 40;
    private static final int MIN_RADIUS = 6;
    private static final int MAX_RADIUS = 26;
    private static final int MIN_EDGE = 1;
    private static final int MAX_EDGE = 15;

    private final Map<String, Boolean> leftOfCentre = new HashMap<>();
    // The nodes the path being looked at ran, for as long as one drawing takes. Held here rather
    // than carried down, like the rest of what a single drawing needs.
    private Set<String> onPath = Collections.emptySet();
    // The bright lines that run along the path as it is walked. Gathered apart from the lines the
    // model draws so they can be laid over the lot of them rather than in among them.
    private HtmlBuilder walkEdges = new HtmlBuilder();
    // Kept apart from the lines rather than written in beside them. Both are built as the tree is
    // walked, so a line reached later lands after a badge reached earlier and is painted over it —
    // which on a drawing of any size means badges disappearing under lines belonging to another
    // branch entirely. Gathered here and laid down once every line is in.
    private HtmlBuilder walkBadges = new HtmlBuilder();
    private HtmlBuilder gradients = new HtmlBuilder();
    // Counted up so that no two drawings can name a gradient alike. Static because two graphs can be
    // on the page at once — the pathway list has one behind the edit dialog's — and a url(#id) is
    // looked up across the whole document, so the one in front would otherwise be painted with the
    // one behind's colours.
    private static int drawings;
    // This drawing's share of that count, taken once rather than once a pass. A gradient has to keep
    // its name from one step to the next, or the line pointing at it could not be left where it is
    // and brought up to date.
    private final int drawingId = ++drawings;

    // How long ago the node last changed, in steps against the clock rather than a scale running from
    // the oldest change in this model to the newest. A scale within the model has no fixed meaning —
    // its brightest node is the latest of that model, whether that was a minute ago or last week.
    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;
    private static final String NEVER_CHANGED = "#5c6470";
    private static final String NEVER_CHANGED_RING = "#434955";
    private static final String NEVER_CHANGED_LIGHT = "#707781";

    // A colour of its own for each step rather than shades of one. Steps are a handful of named
    // things, not a measurement, so nothing is lost by their colours being unrelated — and four
    // shades of a single colour are what a node 12 pixels across cannot be told apart by.
    //
    // Warm for recent, cool for settled, which is the way round people read heat. Red against green
    // is the pair most often indistinguishable, so the middle is amber and teal rather than green.
    //
    // Ordered by how recent, newest first. Read by both the drawing and its key, so the two cannot
    // come to disagree.
    private static final List<Band> BANDS = List.of(
            new Band(5 * MINUTE, "#e8543f", "#a8341f", "#ea624e", "in the last 5 minutes"),
            new Band(HOUR, "#f0b429", "#a8780c", "#f2bd43", "in the last hour"),
            new Band(DAY, "#22a2a2", "#0f6e6e", "#3dadad", "in the last day"),
            new Band(Long.MAX_VALUE, "#4a7fc1", "#2b5488", "#608ec8", "over a day ago"));

    private static final String ZOOM_IN_ID = "pathwayZoomIn";
    private static final String ZOOM_OUT_ID = "pathwayZoomOut";
    private static final String ZOOM_FIT_ID = "pathwayZoomFit";
    private static final String KEY_ID = "pathwayKeyToggle";
    private static final String KEY_SHOWN_CLASS = "pathway-graph-key--shown";
    // The thinnest a line traced by a walk is ever drawn. The drawing is scaled to fit, and following
    // a path on a large one is what this is for, so a line as thin as the traffic through it would
    // leave nothing to follow.
    private static final int WALK_WIDTH = 3;
    // Big enough to hold a two figure number at the size the drawing is read at.
    private static final int BADGE_RADIUS = 11;
    // Wide enough for which strand of several this is, said as one over the other. A round badge fits
    // a single figure and nothing more, and the count beside it is what says how much of the block is
    // still to come.
    private static final int BADGE_WIDTH = 32;
    // How far a node's own ring stands off its rim, which is drawn onto the dot as the node is placed.
    private static final int NODE_RING = 2;
    /**
     * What covers a node the walk being watched has not reached yet. Named here and used by whatever
     * tells it when to lift, because the two have to agree.
     */
    static final String VEIL_CLASS = "pathway-graph-veil";
    /**
     * The name written beside a node. Named here for the same reason as the veil: it is held back and
     * let up as a walk passes, and whatever tells it when has to know what to look for.
     */
    static final String LABEL_CLASS = "pathway-graph-label";

    // What a line the model draws is called. Written when the drawing is built and looked for when
    // one already on the screen is brought up to date, which is how the lines the model draws are
    // told apart from the ones a path being watched lays over them.
    static final String EDGE_CLASS = "pathway-graph-edge";

    /**
     * The line a walk traces along an edge. Named here for the same reason as the veil and the name.
     */
    static final String WALK_EDGE_CLASS = "pathway-graph-walk";

    /**
     * The badge that says which run of work happening at the same time the walk is on. Drawn on the
     * line into the node that run starts at, because that is the one place a run begins. Named here
     * because whatever times the walk has to find it, and writes the number on then.
     */
    static final String WALK_BADGE_CLASS = "pathway-graph-walk-badge";

    /** The number inside the badge, named so whatever times the walk can write it. */
    static final String WALK_BADGE_TEXT_CLASS = "pathway-graph-walk-badge-text";

    @Override
    public DrawnArea drawnArea() {
        return drawnArea;
    }

    @Override
    public void rescale(final Element element, final double zoom) {
        final int was = minEdge;
        minEdge = minEdge(zoom);
        if (minEdge == was || element == null) {
            return;
        }
        final Element curves = byClass(element, "pathway-curves");
        if (curves == null) {
            return;
        }
        final NodeList<Element> drawn = curves.getElementsByTagName("line");
        for (int i = 0; i < drawn.getLength(); i++) {
            final Element line = drawn.getItem(i);
            final EdgeAppearance want = edgeAppearance.get(line.getAttribute("edge"));
            if (want != null) {
                line.setAttribute("stroke-width", String.valueOf(width(want)));
            }
        }
    }

    @Override
    public boolean opensCentred() {
        // The root sits in the middle of the drawing and the rings grow out around it, so a view
        // starting at the top left would be looking at empty space in a corner.
        return true;
    }

    @Override
    public boolean isPannable() {
        return true;
    }

    @Override
    public boolean isZoomable() {
        return true;
    }

    @Override
    public boolean usesHistory() {
        // How much a node has changed is its size, and how long ago is its colour.
        return true;
    }

    @Override
    public boolean onControl(final String id, final PathwayControls controls) {
        if (ZOOM_IN_ID.equals(id)) {
            controls.zoomIn();
        } else if (ZOOM_OUT_ID.equals(id)) {
            controls.zoomOut();
        } else if (ZOOM_FIT_ID.equals(id)) {
            controls.zoomToExtent();
        } else if (KEY_ID.equals(id)) {
            controls.toggleLegend();
        } else {
            return false;
        }
        return true;
    }

    // What the last pass gave each node, by uuid. Filled as the drawing is built and read back when a
    // drawing already on the screen is being brought up to date, so the two say the same thing by
    // construction rather than by two pieces of code agreeing with each other.
    private final Map<String, Appearance> appearance = new HashMap<>();
    // The same for the lines between them. Separate because a line is drawn into the svg layer rather
    // than as an element of its own in the run of nodes, and is found a different way.
    private final Map<String, EdgeAppearance> edgeAppearance = new HashMap<>();
    // What the drawing on the screen was built with, for the two things that decide its shape rather
    // than its colours: whether the key is open, and whether a path is being watched — which is what
    // puts a veil over every node. Neither is part of a node's appearance, so neither can be changed
    // in place; a drawing that wants either of them different has to be built again.
    // How long a line takes to reach where it is going. Has to be what `--pathway-step-ease` in the
    // stylesheet gives the nodes, because the two move together and nothing holds the number for
    // both — a line is drawn in svg, where its ends are attributes rather than properties, so the
    // stylesheet cannot reach it.
    private static final int EASE_MILLIS = 600;
    // The four numbers behind the curve css calls `ease`, which is the curve the stylesheet moves the
    // nodes along.
    private static final double EASE_X1 = 0.25;
    private static final double EASE_Y1 = 0.1;
    private static final double EASE_X2 = 0.25;
    private static final double EASE_Y2 = 1.0;
    // Reading the curve means closing in on an answer rather than working it out. Both of these are
    // far past what a screen can show: the run is a few hundred pixels and a step of it is under a
    // thousandth of one.
    private static final int EASE_STEPS = 8;
    private static final double EASE_CLOSE_ENOUGH = 0.000001;

    private boolean drawnLegend;
    private boolean drawnVeiled;
    // The one run of a step's movement — the nodes and the lines together — stopped when another
    // step arrives before it has finished. Null while nothing is moving.
    private Animation stepEase;
    // The box the drawing on the screen was built in, so that a step taken in place can be laid out
    // in the same one rather than in a box of its own. Null before anything has been drawn.
    private Size drawnSize;
    // Which part of that box the model came to. Null before anything has been drawn.
    private DrawnArea drawnArea;
    // The thinnest a line may be drawn at the scale the drawing is being shown at. Kept because the
    // scale changes without the drawing being made again.
    private int minEdge = MIN_EDGE;

    private record Appearance(String className,
                              String style,
                              String dotStyle,
                              String title,
                              double cx,
                              double cy,
                              double size) {

    }

    // Where a node stands at one moment: the middle of it, and how far across it is. The middle
    // rather than the corner, because the middle is what a line joins and what has to be worked out
    // once for both.
    private record Spot(double cx, double cy, double size) {

    }

    // What the last pass gave each line, by the uuid of the node it arrives at — which names a line,
    // because a node is arrived at from one parent. Start and end are the two ends of its gradient,
    // which is held apart from the line and has to be brought up to date with it.
    private record EdgeAppearance(String parent,
                                  String className,
                                  int x1,
                                  int y1,
                                  int x2,
                                  int y2,
                                  int width,
                                  String stroke,
                                  String start,
                                  String end) {

    }

    private record Painted(Size size, SafeHtml curves, SafeHtml markers, SafeHtml badges) {

    }

    // A line on its way from where it was to where it now belongs, with the gradient that paints it
    // carried along. The two are held apart in the markup and share a pair of ends, so a gradient
    // left behind would light the line from somewhere the line no longer runs.
    // A line on its way, named by the two nodes it joins. It keeps no ends of its own: where it runs
    // is read off those two nodes every frame, so it cannot say anything different from them.
    private record MovingLine(Element line, Element paint, String parent, String child) {

    }

    // A node on its way from where it stood to where it now belongs.
    private record MovingNode(Element node, Element dot, String uuid, Spot from, Spot to) {

    }

    // The model being shown, placed from every node it has ever held so that a node arriving does not
    // move the ones around it. Null where there is nothing to draw.
    private static PathNode layoutRoot(final RenderRequest request) {
        final PathNode shown = NullSafe.get(request.getPathway(), Pathway::getRoot);
        return request.getLayout() == null
                ? shown
                : request.getLayout();
    }

    @Override
    public SafeHtml render(final RenderRequest request) {
        final PathNode root = layoutRoot(request);
        if (root == null) {
            return new HtmlBuilder().toSafeHtml();
        }
        appearance.clear();
        edgeAppearance.clear();
        final Painted painted = paint(request, root, null);
        drawnSize = painted.size();
        drawnLegend = request.isLegendVisible();
        drawnVeiled = !NullSafe.set(request.getOnPath()).isEmpty();

        final HtmlBuilder canvas = new HtmlBuilder();
        canvas.div(d -> d.append(painted.curves()), Attribute.className("pathway-curves"));
        canvas.div(d -> d.append(painted.markers()), Attribute.className("pathway-nodes"));
        // After the nodes, so a badge is read over the name of whatever it lands on. The names are
        // written horizontally and so are many of the links, so the two share a band of the drawing
        // often; a badge is up for a moment and a name is up always, so the badge is the one that has
        // to be legible while it is there.
        canvas.div(d -> d.append(painted.badges()), Attribute.className("pathway-badges"));

        // Drawn at its own size and scaled by the view. The box around it is what carries the scaled
        // size, because scaling does not change what a thing takes up and the scrollbars would
        // otherwise never know it had grown.
        final HtmlBuilder scaled = new HtmlBuilder();
        scaled.div(d -> d.append(canvas.toSafeHtml()),
                Attribute.className("pathway-graph-canvas"),
                Attribute.style(painted.size().style()));

        final HtmlBuilder hb = new HtmlBuilder();
        hb.div(d -> d.div(inner -> inner.append(scaled.toSafeHtml()),
                        Attribute.className("pathway-graph-sizer"),
                        Attribute.style(painted.size().style())),
                Attribute.className("pathway pathway-graph"));
        // Beside the drawing rather than inside it, so it stays put while the drawing is scrolled and
        // is not scaled along with it when the view is zoomed.
        appendKey(hb, request.isLegendVisible());
        appendZoom(hb);
        return hb.toSafeHtml();
    }

    /**
     * Brings a drawing already on the screen up to date rather than building it again, and says
     * whether it managed to. The nodes are the same elements as before, moved and recoloured in
     * place; only the lines are replaced, because where a line starts and ends depends on how large
     * the two nodes it joins have grown and there is nothing on a line to find it by.
     *
     * <p>What this is for is the model being stepped through its own history: the drawing is remade
     * every step and a drawing remade is a drawing that flickers, loses the reader's scroll and
     * starts all of its animation again. The same nodes stand from one step to the next whatever the
     * model held at it, so every step is a matter of moving and recolouring what is already there —
     * which is what lets a node growing, fading, or sliding as its branch re-slices be eased rather
     * than jumped.
     *
     * <p>Refuses where the drawing is laid out differently from the one on the screen, or where it
     * cannot find what it expects. The caller then builds the whole thing, which is always correct.
     */
    @Override
    public boolean update(final Element element, final RenderRequest request) {
        final PathNode root = layoutRoot(request);
        if (root == null || element == null) {
            return false;
        }
        if (request.isLegendVisible() != drawnLegend
            || !NullSafe.set(request.getOnPath()).isEmpty() != drawnVeiled) {
            return false;
        }
        final Element curves = byClass(element, "pathway-curves");
        final Element nodes = byClass(element, "pathway-nodes");
        final Element badges = byClass(element, "pathway-badges");
        if (curves == null || nodes == null || badges == null) {
            return false;
        }

        appearance.clear();
        edgeAppearance.clear();
        // Laid out in the box the drawing on the screen already has. The box and the corner it is
        // measured from are worked out from where the nodes came to, and the nodes move from one step
        // to the next, so working them out again would shift the corner — and every node with it, by
        // the same amount. The drawing would wander about the panel as it was stepped through. The
        // box is the one the model as it stands needs, and every step of a replay holds a part of
        // that model, so it is big enough for all of them.
        final Painted painted = paint(request, root, drawnSize);

        // Every node the drawing now wants has to be one already on the screen. Anything else means a
        // different layout, and moving what happens to match would leave the rest of it stale.
        final Map<String, Element> onScreen = new HashMap<>();
        final NodeList<Node> children = nodes.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            final Node node = children.getItem(i);
            if (Element.is(node)) {
                final Element child = Element.as(node);
                onScreen.put(child.getAttribute("uuid"), child);
            }
        }
        if (!onScreen.keySet().containsAll(appearance.keySet())
            || onScreen.size() != appearance.size()) {
            return false;
        }

        // Where everything stands at this moment, read before any of it is written over. What is
        // written below is where the step ends; this is where it starts from, and the two are what
        // the run between them is worked out from.
        final Map<String, Spot> stood = standing(onScreen);

        // The lines are left where they are and brought up to date, so a join arriving or going can
        // ease the way the nodes do. Where that cannot be done — a path being watched lays a second
        // run of lines and a set of badges over these, none of which are named — the layer is drawn
        // again instead. Either way the run below takes the lines from where they stood, so a layer
        // drawn again does not arrive ahead of the nodes.
        if (!onPath.isEmpty() || !changeEdges(curves, painted.size())) {
            curves.setInnerHTML(painted.curves().asString());
        }
        badges.setInnerHTML(painted.badges().asString());
        appearance.forEach((uuid, want) -> {
            final Element node = onScreen.get(uuid);
            node.setAttribute("class", want.className());
            node.setAttribute("style", want.style());
            node.setAttribute("title", want.title());
            final Element dot = byClass(node, "pathway-graph-dot");
            if (dot != null) {
                dot.setAttribute("style", want.dotStyle());
            }
        });

        move(onScreen, curves, stood);
        return true;
    }

    // Brings the lines already on the screen up to what the last pass wants. Everything is found
    // before anything is written, so answering false leaves the drawing exactly as it was and the
    // caller can draw the layer again instead.
    private boolean changeEdges(final Element curves, final Size size) {
        final Element svg = firstChild(curves);
        if (svg == null) {
            return false;
        }
        final Map<String, Element> lines = edgeLines(curves);
        if (!lines.keySet().equals(edgeAppearance.keySet())) {
            return false;
        }
        // A line is painted with a gradient held away from it, so both have to be there to be moved.
        final Map<String, Element> paints = new HashMap<>();
        for (final String uuid : edgeAppearance.keySet()) {
            final Element paint = Document.get().getElementById(gradientId(uuid));
            if (paint == null
                || paint.getChildCount() != 2
                || !Element.is(paint.getChild(0))
                || !Element.is(paint.getChild(1))) {
                return false;
            }
            paints.put(uuid, paint);
        }

        svg.setAttribute("width", String.valueOf(size.width));
        svg.setAttribute("height", String.valueOf(size.height));

        // Everything about a line but where it runs. How wide it is and whether it is there at all
        // are properties, so the stylesheet eases them; the colours it is painted with are read off
        // as it is drawn.
        edgeAppearance.forEach((uuid, want) -> {
            final Element line = lines.get(uuid);
            line.setAttribute("class", want.className());
            line.setAttribute("stroke-width", String.valueOf(width(want)));
            final Element paint = paints.get(uuid);
            Element.as(paint.getChild(0)).setAttribute("stop-color", want.start());
            Element.as(paint.getChild(1)).setAttribute("stop-color", want.end());
        });
        return true;
    }

    // Walks every line that has somewhere new to be from where it is to where it now belongs, over
    // the same time and along the same curve the stylesheet moves the nodes by.
    //
    // Where an svg line runs is held in attributes rather than properties, and nothing in css reaches
    // an attribute. Left to itself a line arrives the instant a step is taken while the node it points
    // at is still a drawing's width away, so the nodes are seen sliding up to lines already waiting
    // for them.
    // Walks the nodes and the lines between them from where they stand to where this step wants
    // them, as one run on one clock.
    //
    // Both together rather than the lines here and the nodes left to the stylesheet. A line is drawn
    // in svg, where its ends are attributes that no rule can reach, so the two were being moved by
    // different things — and whichever of them set off first arrived first, which is a line reaching
    // its place while the node it joins is still sliding up to meet it. Moved from here, there is one
    // clock and one curve, and a line's ends are not its own at all: they are read off the middles of
    // the two nodes it joins, worked out once a frame and used by both. A line cannot come away from
    // a node it takes its own ends from.
    private void move(final Map<String, Element> onScreen,
                      final Element curves,
                      final Map<String, Spot> stood) {
        if (stepEase != null) {
            // Left wherever it had got to. What replaces it has already read that, and carries on
            // from there rather than from where the step before was headed.
            stepEase.cancel();
            stepEase = null;
        }

        final List<MovingNode> movingNodes = new ArrayList<>();
        appearance.forEach((uuid, want) -> {
            final Element node = onScreen.get(uuid);
            if (node == null) {
                return;
            }
            final Spot from = stood.getOrDefault(uuid, new Spot(want.cx(), want.cy(), want.size()));
            movingNodes.add(new MovingNode(node, byClass(node, "pathway-graph-dot"), uuid,
                    from, new Spot(want.cx(), want.cy(), want.size())));
        });

        final List<MovingLine> movingLines = new ArrayList<>();
        final Map<String, Element> lines = edgeLines(curves);
        edgeAppearance.forEach((uuid, want) -> {
            final Element line = lines.get(uuid);
            final Element paint = Document.get().getElementById(gradientId(uuid));
            if (line != null && paint != null) {
                movingLines.add(new MovingLine(line, paint, want.parent(), uuid));
            }
        });

        final Runner runner = new Runner(movingNodes, movingLines);
        // The first frame now rather than at the next one. The lines may have just been drawn again
        // at where they are going, and a frame of them sitting there before the run starts is the
        // jump this is here to stop.
        runner.at(0);
        if (runner.settled()) {
            return;
        }
        stepEase = new Animation() {
            @Override
            protected double interpolate(final double progress) {
                return ease(progress);
            }

            @Override
            protected void onUpdate(final double progress) {
                runner.at(progress);
            }

            @Override
            protected void onCancel() {
                // Left where it stands; see above.
            }

            @Override
            protected void onComplete() {
                runner.at(1);
                stepEase = null;
            }
        };
        stepEase.run(EASE_MILLIS);
    }

    // One step's worth of movement. Holds the nodes and the lines together because the lines are
    // worked out from the nodes: every frame places each node first, then runs each line between the
    // two it joins.
    private static final class Runner {

        private final List<MovingNode> nodes;
        private final List<MovingLine> lines;
        private final Map<String, Spot> placed = new HashMap<>();

        private Runner(final List<MovingNode> nodes, final List<MovingLine> lines) {
            this.nodes = nodes;
            this.lines = lines;
        }

        private boolean settled() {
            for (final MovingNode node : nodes) {
                if (!same(node.from(), node.to())) {
                    return false;
                }
            }
            return true;
        }

        private static boolean same(final Spot a, final Spot b) {
            return Math.round(a.cx()) == Math.round(b.cx())
                   && Math.round(a.cy()) == Math.round(b.cy())
                   && Math.round(a.size()) == Math.round(b.size());
        }

        private void at(final double progress) {
            placed.clear();
            for (final MovingNode moving : nodes) {
                final Spot from = moving.from();
                final Spot to = moving.to();
                final Spot now = new Spot(
                        between(from.cx(), to.cx(), progress),
                        between(from.cy(), to.cy(), progress),
                        between(from.size(), to.size(), progress));
                placed.put(moving.uuid(), now);

                final int size = (int) Math.round(now.size());
                // Placed by its corner, which is its middle less half of how far across it is. Worked
                // out from the middle rather than the corner being walked in its own right, so that
                // the middle a line is drawn to is the middle the node is actually at.
                moving.node().getStyle().setPropertyPx("left", (int) Math.round(now.cx()) - (size / 2));
                moving.node().getStyle().setPropertyPx("top", (int) Math.round(now.cy()) - (size / 2));
                if (moving.dot() != null) {
                    moving.dot().getStyle().setPropertyPx("width", size);
                    moving.dot().getStyle().setPropertyPx("height", size);
                }
            }

            for (final MovingLine line : lines) {
                final Spot from = placed.get(line.parent());
                final Spot to = placed.get(line.child());
                if (from == null || to == null) {
                    continue;
                }
                put(line, "x1", from.cx());
                put(line, "y1", from.cy());
                put(line, "x2", to.cx());
                put(line, "y2", to.cy());
            }
        }

        private static void put(final MovingLine line, final String name, final double value) {
            final String at = String.valueOf((int) Math.round(value));
            line.line().setAttribute(name, at);
            // The gradient is held away from the line and shares its two ends, so one left behind
            // would light the line from somewhere the line no longer runs.
            line.paint().setAttribute(name, at);
        }

        private static double between(final double from, final double to, final double progress) {
            return from + ((to - from) * progress);
        }
    }

    // Where every node stands at this moment, read off the drawing rather than remembered. A step
    // taken while the one before it is still running has to set off from where things actually are.
    private static Map<String, Spot> standing(final Map<String, Element> onScreen) {
        final Map<String, Spot> stood = new HashMap<>();
        onScreen.forEach((uuid, node) -> {
            final Element dot = byClass(node, "pathway-graph-dot");
            final double size = dot == null
                    ? 0
                    : px(dot.getStyle().getProperty("width"));
            stood.put(uuid, new Spot(
                    px(node.getStyle().getProperty("left")) + (size / 2),
                    px(node.getStyle().getProperty("top")) + (size / 2),
                    size));
        });
        return stood;
    }

    // A length the drawing wrote itself, as a number. Anything that will not read as one means the
    // markup is not what it was left as, and nought moves the node from the corner rather than
    // throwing; the run ends in the right place either way.
    private static double px(final String value) {
        try {
            return Double.parseDouble(value.replace("px", "").trim());
        } catch (final RuntimeException e) {
            return 0;
        }
    }

    // The lines this drawing made, by the node each arrives at. Told apart from the ones a walk lays
    // over them by the class they carry.
    private static Map<String, Element> edgeLines(final Element curves) {
        final Map<String, Element> lines = new HashMap<>();
        final NodeList<Element> drawn = curves.getElementsByTagName("line");
        for (int i = 0; i < drawn.getLength(); i++) {
            final Element line = drawn.getItem(i);
            if ((" " + line.getAttribute("class") + " ").contains(" " + EDGE_CLASS + " ")) {
                lines.put(line.getAttribute("edge"), line);
            }
        }
        return lines;
    }

    // How far along a run from one place to another, which is the curve css calls `ease` — the one
    // the stylesheet moves the nodes along. Worked out again here because a line cannot be moved by
    // the stylesheet, and two layers of one drawing travelling along different curves pull apart in
    // the middle of every step even when they set off and arrive together.
    private static double ease(final double progress) {
        if (progress <= 0 || progress >= 1) {
            return progress;
        }
        // The curve gives how far along and how far through against a third quantity rather than one
        // against the other, so how far through has to be turned back into that before how far along
        // can be read off it.
        double point = progress;
        for (int i = 0; i < EASE_STEPS; i++) {
            final double off = curve(EASE_X1, EASE_X2, point) - progress;
            final double slope = slope(EASE_X1, EASE_X2, point);
            if (Math.abs(off) < EASE_CLOSE_ENOUGH || slope == 0) {
                break;
            }
            point -= off / slope;
        }
        return curve(EASE_Y1, EASE_Y2, point);
    }

    private static double curve(final double first, final double second, final double point) {
        final double rest = 1 - point;
        return (3 * rest * rest * point * first)
               + (3 * rest * point * point * second)
               + (point * point * point);
    }

    private static double slope(final double first, final double second, final double point) {
        final double rest = 1 - point;
        return (3 * rest * rest * first)
               + (6 * rest * point * (second - first))
               + (3 * point * point * (1 - second));
    }

    private static Element firstChild(final Element element) {
        final NodeList<Node> children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (Element.is(children.getItem(i))) {
                return Element.as(children.getItem(i));
            }
        }
        return null;
    }

    // The first element below this one carrying the given class. Read as an attribute rather than
    // through the class name methods, which go at a property that is not a string on an svg element.
    private static Element byClass(final Element element, final String className) {
        final NodeList<Node> children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            final Node node = children.getItem(i);
            if (Element.is(node)) {
                final Element child = Element.as(node);
                if ((" " + child.getAttribute("class") + " ").contains(" " + className + " ")) {
                    return child;
                }
                final Element found = byClass(child, className);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    // Everything inside the box: the lines as one svg, the nodes as a run of divs, and the size both
    // are laid out at. One pass, used both to build a drawing and to bring one already on the screen
    // up to date, so there is no second idea of what a drawing looks like to fall out of step. What
    // each node and each line should look like is written down as it goes, which is what updating one
    // already on the screen reads back.
    private Painted paint(final RenderRequest request, final PathNode root, final Size box) {
        minEdge = minEdge(request.getZoom());
        final PathNode shown = NullSafe.get(request.getPathway(), Pathway::getRoot);
        final Map<String, PathNode> shownNodes = new HashMap<>();
        collect(shown, shownNodes);
        onPath = NullSafe.set(request.getOnPath());

        final Map<String, NodeChange> changes = request.getChanges();
        final Map<String, NodeUsage> usage = request.getUsage();
        leftOfCentre.clear();
        final Map<String, Point> places = new HashMap<>();
        place(root, 0, 0, 2 * Math.PI, places, 0, shownNodes);

        // Only part of the outermost ring is ever occupied — a branch that goes deep reaches it, the
        // rest stop short — so a canvas sized to the ring is mostly empty. Measured to what was
        // actually placed instead, with room for the names that hang off it.
        final Size fitted = new Size(places);
        // Laid out in that, unless the caller has a box already, in which case the drawing goes in
        // the one it has.
        final Size size = box == null
                ? fitted
                : box;
        places.replaceAll((uuid, at) -> new Point(at.getX() - size.left, at.getY() - size.top));
        // Where the model came to within the box, which is the whole of it only when the box was cut
        // to fit. Kept because fitting the drawing to the panel has to frame the model rather than
        // the room it was laid out in.
        drawnArea = new DrawnArea(
                (int) Math.round(fitted.left - size.left),
                (int) Math.round(fitted.top - size.top),
                fitted.width,
                fitted.height);

        gradients = new HtmlBuilder();
        final HtmlBuilder edges = new HtmlBuilder();
        final HtmlBuilder markers = new HtmlBuilder();
        walkEdges = new HtmlBuilder();
        walkBadges = new HtmlBuilder();
        draw(root, places, edges, markers,
                new Scale(request.getChangeCeiling(), request.getUsageCeiling()),
                request.getAsAt(), changes, shownNodes, usage);

        // The gradients come before the lines that point at them.
        final HtmlBuilder inner = new HtmlBuilder();
        inner.elem(defs -> defs.append(gradients.toSafeHtml()), SafeHtmlUtil.from("defs"));
        inner.append(edges.toSafeHtml());
        // Over every line the model draws rather than under the ones drawn after them.
        inner.append(walkEdges.toSafeHtml());

        final HtmlBuilder curves = new HtmlBuilder();
        curves.elem(svg -> svg.append(inner.toSafeHtml()),
                SafeHtmlUtil.from("svg"),
                new Attribute("width", String.valueOf(size.width)),
                new Attribute("height", String.valueOf(size.height)),
                new Attribute("xmlns", "http://www.w3.org/2000/svg"));

        // A drawing of their own, laid over the nodes rather than under them.
        final HtmlBuilder badges = new HtmlBuilder();
        badges.elem(svg -> svg.append(walkBadges.toSafeHtml()),
                SafeHtmlUtil.from("svg"),
                new Attribute("width", String.valueOf(size.width)),
                new Attribute("height", String.valueOf(size.height)),
                new Attribute("xmlns", "http://www.w3.org/2000/svg"));

        return new Painted(size, curves.toSafeHtml(), markers.toSafeHtml(), badges.toSafeHtml());
    }

    // Over the drawing at the bottom right, where a map puts them. Ids rather than a class, because
    // the drawing is rebuilt as one piece of html and the view around it finds a button by id.
    private void appendZoom(final HtmlBuilder hb) {
        hb.div(zoom -> {
            zoom.div("+", Attribute.className("pathway-graph-control"),
                    Attribute.id(ZOOM_IN_ID), Attribute.title("Zoom in"));
            zoom.div("\u2212", Attribute.className("pathway-graph-control"),
                    Attribute.id(ZOOM_OUT_ID), Attribute.title("Zoom out"));
            zoom.div("⤢", Attribute.className("pathway-graph-control"),
                    Attribute.id(ZOOM_FIT_ID), Attribute.title("Fit the whole model in the panel"));
        }, Attribute.className("pathway-graph-controls pathway-graph-zoom"));
    }

    // What the colours mean, kept behind a button: it takes a corner of the drawing to say something
    // that only has to be read once. Built from the same numbers the nodes are coloured with, so it
    // cannot come to say something the drawing does not.
    private void appendKey(final HtmlBuilder hb, final boolean visible) {
        hb.div(panel -> {
            appendScale(panel, visible);
            panel.div(controls -> controls.div("i",
                            Attribute.className("pathway-graph-control"),
                            Attribute.id(KEY_ID),
                            Attribute.title("What the colours and sizes mean")),
                    Attribute.className("pathway-graph-controls"));
        }, Attribute.className("pathway-graph-key-panel"));
    }

    private void appendScale(final HtmlBuilder hb, final boolean visible) {
        hb.div(key -> {
            key.div("Last updated", Attribute.className("pathway-graph-key-title"));
            BANDS.forEach(band -> appendSwatch(key, band.colour, band.ring, band.label));
            appendSwatch(key, NEVER_CHANGED, NEVER_CHANGED_RING, "never changed");
            key.div("Size shows how many times the node has changed",
                    Attribute.className("pathway-graph-key-note"));
            key.div("Line thickness shows how much goes through it, and its colour when that last "
                    + "happened, on the same scale",
                    Attribute.className("pathway-graph-key-note"));
        }, Attribute.className(visible
                ? "pathway-graph-key " + KEY_SHOWN_CLASS
                : "pathway-graph-key"));
    }

    private void appendSwatch(final HtmlBuilder hb,
                              final String colour,
                              final String ring,
                              final String label) {
        hb.div(row -> {
            row.div("", Attribute.className("pathway-graph-key-swatch"),
                    Attribute.style("background-color: " + colour + ";"
                                    + " box-shadow: 0 0 0 2px " + ring + ";"));
            row.div(label, Attribute.className("pathway-graph-key-end"));
        }, Attribute.className("pathway-graph-key-scale"));
    }



    // Each node takes the slice of its parent's arc that its share of the leaves below it comes to, so
    // a branch with more under it is given more room and no two nodes are placed on top of each other.
    private void place(final PathNode node,
                       final int depth,
                       final double from,
                       final double to,
                       final Map<String, Point> places,
                       final int centre,
                       final Map<String, PathNode> shownNodes) {
        final double angle = (from + to) / 2;
        final int radius = depth * RING;
        final Point at = new Point(
                centre + (radius * Math.cos(angle)),
                centre + (radius * Math.sin(angle)));
        places.put(node.getUuid(), at);
        // Which side of the diagram the node sits on, so its name can be written away from the middle
        // rather than across whatever is next to it.
        final boolean left = Math.cos(angle) < 0;
        leftOfCentre.put(node.getUuid(), left);

        final List<PathNode> children = NullSafe.list(node.getChildren());
        if (children.isEmpty()) {
            return;
        }

        final int total = leaves(node, shownNodes);
        double start = from;
        for (final PathNode child : children) {
            // A node the model does not do at this moment takes no share of its parent's arc. Given
            // one it would hold a wedge of the drawing that it is not drawn in, so a model with much
            // churn is drawn around holes nothing can ever fill.
            if (onDrawing(child, shownNodes)) {
                final double share = (to - from) * ((double) leaves(child, shownNodes) / total);
                place(child, depth + 1, start, start + share, places, centre, shownNodes);
                start += share;
            } else {
                gather(child, places, at, left);
            }
        }
    }

    // How wide a line is actually drawn: what the traffic through it says, but never so thin that the
    // screen cannot hold it.
    private int width(final EdgeAppearance want) {
        return Math.max(want.width(), minEdge);
    }

    // The thinnest a line is drawn, in the drawing's own units rather than the screen's.
    //
    // A stroke thinner than a screen pixel is not drawn thin, it is drawn faint: the browser spreads
    // what little of it there is over the pixels it falls between, and a line crossing a pixel
    // boundary fades out along the stretches where it falls unluckily. At a scale of about a half, a
    // third of the links in one model came out broken or missing altogether. So the floor is held at
    // a whole pixel on the screen, whatever the drawing is scaled to.
    private static int minEdge(final double zoom) {
        return zoom <= 0 || zoom >= 1
                ? MIN_EDGE
                : (int) Math.ceil(MIN_EDGE / zoom);
    }

    // Whether the model being shown does this node, which is what decides whether it takes a share of
    // its parent's arc. A node the model has not learnt yet and one the work no longer does are alike
    // here: neither is drawn, so neither holds a wedge of the picture that nothing is drawn in.
    private static boolean onDrawing(final PathNode node, final Map<String, PathNode> shownNodes) {
        final PathNode asShown = shownNodes.get(node.getUuid());
        return asShown != null && !asShown.isRetired();
    }

    // Where a node with no share of the arc goes, along with everything beneath it: on top of the node
    // it hangs off. Left where it last stood it would sit among the nodes that have since taken its
    // room; put here it grows out of its parent as the model learns it and folds back into it as the
    // work stops doing it, which is the same movement either way round.
    private void gather(final PathNode node,
                        final Map<String, Point> places,
                        final Point at,
                        final boolean left) {
        places.put(node.getUuid(), at);
        leftOfCentre.put(node.getUuid(), left);
        for (final PathNode child : NullSafe.list(node.getChildren())) {
            gather(child, places, at, left);
        }
    }

    private void draw(final PathNode node,
                      final Map<String, Point> places,
                      final HtmlBuilder edges,
                      final HtmlBuilder markers,
                      final Scale scale,
                      final long now,
                      final Map<String, NodeChange> changes,
                      final Map<String, PathNode> shownNodes,
                      final Map<String, NodeUsage> usage) {
        // What this node was at the point being shown. The one being walked is the layout's, which is
        // the node as it stands now, so whether it had been retired by then is asked of this instead.
        final PathNode asShown = shownNodes.get(node.getUuid());
        final boolean here = asShown != null;
        final boolean retired = here && asShown.isRetired();
        final Point at = places.get(node.getUuid());
        final NodeChange change = changes.get(node.getUuid());
        final int radius = scale.radius(change);
        final NanoTime updated = NullSafe.get(change, NodeChange::getLastUpdated);

        final String side = (Boolean.TRUE.equals(leftOfCentre.get(node.getUuid()))
                ? " pathway-graph-node--left"
                : "")
                + (here
                        ? ""
                        : " pathway-graph-node--absent")
                + (ran(node)
                        ? ""
                        : " pathway-graph-node--off-path")
                + (retired
                        ? " pathway-graph-node--retired"
                        : "");
        final String nodeClass = "pathway-graph-node" + side;
        final String nodeStyle = "left: " + ((int) at.getX() - radius) + "px;"
                                 + " top: " + ((int) at.getY() - radius) + "px;";
        final String dotStyle = "width: " + (radius * 2) + "px;"
                                + " height: " + (radius * 2) + "px;"
                                + " background-color: " + colour(updated, now) + ";"
                                + " box-shadow: 0 0 0 " + NODE_RING + "px " + ring(updated, now) + ";";
        // The name and nothing else. How much the node has changed is its size and how long ago is its
        // colour, both of which the key explains, so a tooltip saying them again is a second answer to
        // a question the drawing has already given.
        final String title = node.getName();
        appearance.put(node.getUuid(), new Appearance(nodeClass, nodeStyle, dotStyle, title,
                (int) at.getX(), (int) at.getY(), radius * 2));

        markers.div(marker -> {
            marker.div("", Attribute.className("pathway-graph-dot"),
                    // A shadow rather than a border: the width given here is what says how much the
                    // node has changed, and a border would eat into it. Selecting draws an outline
                    // instead of a second shadow, so the two do not fight over one property.
                    Attribute.style(dotStyle));
            if (!onPath.isEmpty()) {
                // Over the node rather than making the node see-through, whether it is held back until
                // the walk arrives or never ran at all. Faded out instead, a node shows through itself
                // the lines that end under it.
                marker.div("", Attribute.className(VEIL_CLASS));
            }
            marker.div(label -> label.append(node.getName()),
                    Attribute.className(LABEL_CLASS));
        }, Attribute.className(nodeClass),
                new Attribute("uuid", node.getUuid()),
                Attribute.style(nodeStyle),
                Attribute.title(title));

        for (final PathNode child : NullSafe.list(node.getChildren())) {
            final Point childAt = places.get(child.getUuid());
            // As thick as the traffic reaching what it points at, and coloured by how recently that
            // traffic last came through, on the same scale as the nodes. So the thick bright lines
            // are the paths being taken now and the thin dim ones are the paths that have stopped.
            //
            // Drawn between the middles of the two nodes rather than between their rims, and hidden
            // under them for the part that is inside. A node is drawn over the lines and is never
            // smaller than the widest line that can reach it — the smallest node is 12 across and
            // wears a 2 ring either side, against a widest line of MAX_EDGE — so nothing of the line
            // shows past the circle meant to be covering it.
            //
            // Which middle a node is at is the one thing about it that moves exactly as the
            // stylesheet moves the node itself: a rim point is the middle plus an offset that turns
            // as the two nodes swing round, and an offset that turns cannot be walked in a straight
            // line. Lines drawn to the rim came away from their nodes while a step was being taken,
            // by as much as the node's own width, and the more the model rearranged the worse it
            // looked. Drawn to the middle there is nothing to come away: the end of the line is the
            // middle of the node, wherever the node has got to.
            final Point from = at;
            final Point to = childAt;
            // Lighter where it leaves the parent, full strength where it arrives, so a link reads in
            // the direction the work flows without needing an arrow head on it.
            final int edgeWidth = scale.edge(timesUsed(child, usage));
            final PathNode childShown = shownNodes.get(child.getUuid());
            final EdgeAppearance want = new EdgeAppearance(
                    node.getUuid(),
                    edgeClass(childShown != null,
                            ran(child),
                            childShown != null && childShown.isRetired()),
                    (int) from.getX(), (int) from.getY(),
                    (int) to.getX(), (int) to.getY(),
                    edgeWidth,
                    "url(#" + gradientId(child.getUuid()) + ")",
                    light(lastUsed(child, usage), now),
                    colour(lastUsed(child, usage), now));
            edgeAppearance.put(child.getUuid(), want);
            gradient(child.getUuid(), from, to, want.start(), want.end());
            line(edges, child.getUuid(), want);
            if (!onPath.isEmpty() && ran(child)) {
                walkLine(walkEdges, child.getUuid(), from, to, Math.max(edgeWidth, WALK_WIDTH));
                walkBadge(walkBadges, child.getUuid(), from, to);
            }
            draw(child, places, edges, markers, scale, now, changes, shownNodes, usage);
        }
    }

    // The bright line that runs along an edge as the walk reaches the node at its far end. A second
    // line laid over the one the model draws rather than a change to it: that one says what joins what
    // and how busy the join is, and a path being watched must not take that away while it plays.
    //
    // It is held off the end of itself to start with, so nothing of it is on show until the walk says
    // so. Its length is given as one rather than measured: a dash the whole length of the line and an
    // offset of the same puts it out of sight, and saying that as one means the stylesheet can name
    // both ends of the run without knowing how long any particular line is. It has to name both,
    // because a walk that runs along the same line twice leaves the line drawn from the first time,
    // and a run that only said where to finish would start from there and be there already.
    private static void walkLine(final HtmlBuilder svg,
                                 final String reaches,
                                 final Point start,
                                 final Point end,
                                 final int width) {
        svg.elem(SafeHtmlUtil.from("line"),
                // The node at the far end, which is how the walk finds this line. Not the uuid the
                // nodes carry: a walk looks for both and one is not the other.
                new Attribute("edge", reaches),
                new Attribute("x1", String.valueOf((int) start.getX())),
                new Attribute("y1", String.valueOf((int) start.getY())),
                new Attribute("x2", String.valueOf((int) end.getX())),
                new Attribute("y2", String.valueOf((int) end.getY())),
                new Attribute("stroke-width", String.valueOf(width)),
                new Attribute("pathLength", "1"),
                new Attribute("stroke-dasharray", "1"),
                new Attribute("stroke-dashoffset", "1"),
                Attribute.className(WALK_EDGE_CLASS));
    }

    // The round badge that flashes the number of a run on the line that run starts at. Drawn empty
    // and held out of sight, because which runs there are and which of them begins here is not known
    // until a path is picked; whatever marks the walk writes the number on and says when.
    //
    // Halfway along the line, which is the one place on it belonging to neither node it joins.
    private static void walkBadge(final HtmlBuilder svg,
                                  final String reaches,
                                  final Point start,
                                  final Point end) {
        final int x = (int) ((start.getX() + end.getX()) / 2);
        final int y = (int) ((start.getY() + end.getY()) / 2);
        svg.elem(badge -> {
            badge.elem(SafeHtmlUtil.from("rect"),
                    new Attribute("x", String.valueOf(x - (BADGE_WIDTH / 2))),
                    new Attribute("y", String.valueOf(y - BADGE_RADIUS)),
                    new Attribute("width", String.valueOf(BADGE_WIDTH)),
                    new Attribute("height", String.valueOf(BADGE_RADIUS * 2)),
                    // Ends as round as the badge was when it held one figure.
                    new Attribute("rx", String.valueOf(BADGE_RADIUS)));
            // Held in the middle both ways, so the number sits in the circle whatever it is.
            badge.elem("", SafeHtmlUtil.from("text"),
                    new Attribute("x", String.valueOf(x)),
                    new Attribute("y", String.valueOf(y)),
                    new Attribute("text-anchor", "middle"),
                    new Attribute("dominant-baseline", "central"),
                    Attribute.className(WALK_BADGE_TEXT_CLASS));
        }, SafeHtmlUtil.from("g"),
                // The node at the far end, the same way the line into it is found.
                new Attribute("edge", reaches),
                // Out of sight on the drawing itself rather than in the stylesheet. Every line into a
                // node the path ran carries one of these and most are never given a run to say, so
                // what hides them must not depend on a sheet being found.
                new Attribute("opacity", "0"),
                Attribute.className(WALK_BADGE_CLASS));
    }

    // Whether the path being looked at ran this node. Everything counts while none is being looked
    // at, which is most of the time.
    private boolean ran(final PathNode node) {
        return onPath.isEmpty() || onPath.contains(node.getUuid());
    }

    // Straight, not the curves the tree draws: those bend towards a left-to-right layout, and on a
    // ring the bend would point the edge away from the node it joins.
    // A gradient of its own for each link, laid along the line it paints. Along the line rather than
    // across the drawing, which is what gradientUnits="userSpaceOnUse" with the line's own ends does —
    // the default would measure against the shape's box and turn with it.
    private void gradient(final String uuid,
                          final Point from,
                          final Point to,
                          final String start,
                          final String end) {
        gradients.elem(gradient -> {
            gradient.elem(SafeHtmlUtil.from("stop"),
                    new Attribute("offset", "0"),
                    new Attribute("stop-color", start));
            gradient.elem(SafeHtmlUtil.from("stop"),
                    new Attribute("offset", "1"),
                    new Attribute("stop-color", end));
        }, SafeHtmlUtil.from("linearGradient"),
                new Attribute("id", gradientId(uuid)),
                new Attribute("gradientUnits", "userSpaceOnUse"),
                new Attribute("x1", String.valueOf((int) from.getX())),
                new Attribute("y1", String.valueOf((int) from.getY())),
                new Attribute("x2", String.valueOf((int) to.getX())),
                new Attribute("y2", String.valueOf((int) to.getY())));
    }

    // Named after the node the line arrives at, so a gradient keeps its name across a step and the
    // line pointing at it can be left where it is. The drawing's own number goes in front, because a
    // url(#id) is looked up across the whole document and two graphs can be on the page at once.
    private String gradientId(final String uuid) {
        return "pathwayEdge" + drawingId + "-" + uuid;
    }


    private void line(final HtmlBuilder svg, final String uuid, final EdgeAppearance want) {
        svg.elem(SafeHtmlUtil.from("line"),
                // Which node this line arrives at, so the line can be found again rather than drawn
                // again when the drawing is brought up to date.
                new Attribute("edge", uuid),
                new Attribute("x1", String.valueOf(want.x1())),
                new Attribute("y1", String.valueOf(want.y1())),
                new Attribute("x2", String.valueOf(want.x2())),
                new Attribute("y2", String.valueOf(want.y2())),
                new Attribute("stroke-width", String.valueOf(width(want))),
                new Attribute("stroke", want.stroke()),
                Attribute.className(want.className()));
    }

    private static String edgeClass(final boolean present, final boolean ran, final boolean retired) {
        return EDGE_CLASS
               + (present
                       ? ""
                       : " pathway-graph-edge--absent")
               + (ran
                       ? ""
                       : " pathway-graph-edge--off-path")
               + (retired
                       ? " pathway-graph-edge--retired"
                       : "");
    }

    // What the reading kept at the moment being shown says, or what the node says where there is no
    // reading — which is the case when the current model is on show, there being nothing to wind back.
    private static long timesUsed(final PathNode node, final Map<String, NodeUsage> usage) {
        final NodeUsage reading = usage.get(node.getUuid());
        return reading == null
                ? node.getTimesUsed()
                : reading.getTimesUsed();
    }

    private static NanoTime lastUsed(final PathNode node, final Map<String, NodeUsage> usage) {
        final NodeUsage reading = usage.get(node.getUuid());
        return reading == null
                ? node.getLastUsedTime()
                : reading.getLastUsedTime();
    }

    // The model being shown, by uuid. The drawing is laid out from every node the pathway has ever
    // held, so a node is looked up here to find out what it was at the point being shown rather than
    // read off the one standing in for it in the layout, which is always the node as it is now.
    private static void collect(final PathNode node, final Map<String, PathNode> shownNodes) {
        if (node != null) {
            shownNodes.put(node.getUuid(), node);
            NullSafe.list(node.getChildren()).forEach(child -> collect(child, shownNodes));
        }
    }

    private static String colour(final NanoTime updated, final long now) {
        final Band band = band(updated, now);
        return band == null
                ? NEVER_CHANGED
                : band.colour;
    }

    private static String ring(final NanoTime updated, final long now) {
        final Band band = band(updated, now);
        return band == null
                ? NEVER_CHANGED_RING
                : band.ring;
    }

    private static String light(final NanoTime updated, final long now) {
        final Band band = band(updated, now);
        return band == null
                ? NEVER_CHANGED_LIGHT
                : band.light;
    }

    // The first band the age falls inside, or null where it is not known.
    private static Band band(final NanoTime updated, final long now) {
        if (updated == null) {
            return null;
        }
        final long age = now - updated.toEpochMillis();
        if (age < 0) {
            // After the moment being shown, which the readings taken as the model changed should have
            // ruled out. Not known rather than brand new.
            return null;
        }
        for (final Band band : BANDS) {
            if (age < band.within) {
                return band;
            }
        }
        return BANDS.get(BANDS.size() - 1);
    }

    // How many of the nodes beneath this one are on the drawing, which is what its share of its
    // parent's arc is worked out from. One where none of them is, because the node itself still needs
    // room whether or not anything hangs off it.
    private static int leaves(final PathNode node, final Map<String, PathNode> shownNodes) {
        int count = 0;
        for (final PathNode child : NullSafe.list(node.getChildren())) {
            if (onDrawing(child, shownNodes)) {
                count += leaves(child, shownNodes);
            }
        }
        return count == 0
                ? 1
                : count;
    }






    // --------------------------------------------------------------------------------


    // What the drawing came to, and where it starts. Worked out from the nodes once they are placed,
    // because where a ring is occupied depends on the shape of the model rather than on its depth.
    private static class Size {

        private final int width;
        private final int height;
        private final double left;
        private final double top;

        private Size(final Map<String, Point> places) {
            double minX = 0;
            double maxX = 0;
            double minY = 0;
            double maxY = 0;
            for (final Point at : places.values()) {
                minX = Math.min(minX, at.getX());
                maxX = Math.max(maxX, at.getX());
                minY = Math.min(minY, at.getY());
                maxY = Math.max(maxY, at.getY());
            }
            this.left = minX - SIDE_MARGIN;
            this.top = minY - END_MARGIN;
            this.width = (int) Math.round((maxX - minX) + (SIDE_MARGIN * 2));
            this.height = (int) Math.round((maxY - minY) + (END_MARGIN * 2));
        }

        private String style() {
            return "width: " + width + "px; height: " + height + "px;";
        }
    }


    // --------------------------------------------------------------------------------


    // How the numbers turn into sizes. Scaled against the biggest this model has ever reached rather
    // than a fixed number, so a model whose counts are all small is still readable, and square rooted
    // because the counts run over orders of magnitude — on a straight scale everything but the biggest
    // would be drawn at the minimum.
    private static class Scale {

        private final long mostChanged;
        private final long mostUsed;

        private Scale(final long mostChanged, final long mostUsed) {
            this.mostChanged = mostChanged;
            this.mostUsed = mostUsed;
        }

        private int radius(final NodeChange change) {
            return MIN_RADIUS + step(NullSafe.getOrElse(change, NodeChange::getCount, 0L),
                    mostChanged, MAX_RADIUS - MIN_RADIUS);
        }

        private int edge(final long timesUsed) {
            return MIN_EDGE + step(timesUsed, mostUsed, MAX_EDGE - MIN_EDGE);
        }

        private static int step(final long value, final long most, final int range) {
            if (value <= 0 || most <= 0) {
                return 0;
            }
            return (int) Math.round(Math.sqrt((double) value / most) * range);
        }

    }


    // --------------------------------------------------------------------------------


    // One step of the scale: how recent a change has to be to fall in it, and what it is drawn as.
    private static class Band {

        private final long within;
        private final String colour;
        private final String ring;
        // The same colour lightened, for the end of a link where it leaves its parent. Given rather
        // than worked out, for two reasons: on a dark panel, thinning a colour makes it darker rather
        // than lighter; and the same step toward white does not look the same step on every colour.
        // Red turns pink and washes out well before blue or teal do, so it is lightened less — the
        // aim is a fade that looks alike, not one that measures alike.
        private final String light;
        private final String label;

        private Band(final long within,
                     final String colour,
                     final String ring,
                     final String light,
                     final String label) {
            this.within = within;
            this.colour = colour;
            this.ring = ring;
            this.light = light;
            this.label = label;
        }
    }
}
