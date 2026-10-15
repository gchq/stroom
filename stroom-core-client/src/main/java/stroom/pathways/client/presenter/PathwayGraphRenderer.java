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

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Node;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.safehtml.shared.SafeHtml;

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
    private boolean drawnLegend;
    private boolean drawnVeiled;

    private record Appearance(String className, String style, String dotStyle, String title) {

    }

    // What the last pass gave each line, by the uuid of the node it arrives at — which names a line,
    // because a node is arrived at from one parent. Start and end are the two ends of its gradient,
    // which is held apart from the line and has to be brought up to date with it.
    private record EdgeAppearance(String className,
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
        final Painted painted = paint(request, root);
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
     * starts all of its animation again. Nothing moves between steps but how large each node is, what
     * colour it is and whether it is there yet.
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
        final Painted painted = paint(request, root);

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

        // The lines are left where they are and brought up to date, so a join arriving or going can
        // ease the way the nodes do. Not while a path is being watched: that lays a second run of
        // lines and a set of badges over these, none of which are named, so the layer goes back to
        // being drawn again.
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
        final Map<String, Element> lines = new HashMap<>();
        final NodeList<Element> drawn = curves.getElementsByTagName("line");
        for (int i = 0; i < drawn.getLength(); i++) {
            final Element line = drawn.getItem(i);
            if ((" " + line.getAttribute("class") + " ").contains(" " + EDGE_CLASS + " ")) {
                lines.put(line.getAttribute("edge"), line);
            }
        }
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
        edgeAppearance.forEach((uuid, want) -> {
            final Element line = lines.get(uuid);
            line.setAttribute("class", want.className());
            line.setAttribute("x1", String.valueOf(want.x1()));
            line.setAttribute("y1", String.valueOf(want.y1()));
            line.setAttribute("x2", String.valueOf(want.x2()));
            line.setAttribute("y2", String.valueOf(want.y2()));
            line.setAttribute("stroke-width", String.valueOf(want.width()));
            final Element paint = paints.get(uuid);
            paint.setAttribute("x1", String.valueOf(want.x1()));
            paint.setAttribute("y1", String.valueOf(want.y1()));
            paint.setAttribute("x2", String.valueOf(want.x2()));
            paint.setAttribute("y2", String.valueOf(want.y2()));
            Element.as(paint.getChild(0)).setAttribute("stop-color", want.start());
            Element.as(paint.getChild(1)).setAttribute("stop-color", want.end());
        });
        return true;
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
    private Painted paint(final RenderRequest request, final PathNode root) {
        final PathNode shown = NullSafe.get(request.getPathway(), Pathway::getRoot);
        final Map<String, PathNode> shownNodes = new HashMap<>();
        collect(shown, shownNodes);
        onPath = NullSafe.set(request.getOnPath());

        final Map<String, NodeChange> changes = request.getChanges();
        final Map<String, NodeUsage> usage = request.getUsage();
        leftOfCentre.clear();
        final Map<String, Point> places = new HashMap<>();
        place(root, 0, 0, 2 * Math.PI, places, 0);

        // Only part of the outermost ring is ever occupied — a branch that goes deep reaches it, the
        // rest stop short — so a canvas sized to the ring is mostly empty. Sized to what was actually
        // placed instead, with room for the names that hang off it.
        final Size size = new Size(places);
        places.replaceAll((uuid, at) -> new Point(at.getX() - size.left, at.getY() - size.top));

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
                       final int centre) {
        final double angle = (from + to) / 2;
        final int radius = depth * RING;
        places.put(node.getUuid(), new Point(
                centre + (radius * Math.cos(angle)),
                centre + (radius * Math.sin(angle))));
        // Which side of the diagram the node sits on, so its name can be written away from the middle
        // rather than across whatever is next to it.
        leftOfCentre.put(node.getUuid(), Math.cos(angle) < 0);

        final List<PathNode> children = NullSafe.list(node.getChildren());
        if (children.isEmpty()) {
            return;
        }

        final int total = leaves(node);
        double start = from;
        for (final PathNode child : children) {
            final double share = (to - from) * ((double) leaves(child) / total);
            place(child, depth + 1, start, start + share, places, centre);
            start += share;
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
        appearance.put(node.getUuid(), new Appearance(nodeClass, nodeStyle, dotStyle, title));

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
            // Drawn between the two circles rather than between their middles. A line runs as wide as
            // its traffic says and a node is as large as its changes say, so a line can be wider than
            // what it joins; ending at the middle, it would show either side of the circle meant to
            // be hiding it. Ending at the rim, neither has to be held back for the other.
            final Point from = toward(at, childAt, radius);
            final Point to = toward(childAt, at, scale.radius(changes.get(child.getUuid())));
            // Lighter where it leaves the parent, full strength where it arrives, so a link reads in
            // the direction the work flows without needing an arrow head on it.
            final int edgeWidth = scale.edge(timesUsed(child, usage));
            final PathNode childShown = shownNodes.get(child.getUuid());
            final EdgeAppearance want = new EdgeAppearance(
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

    // The point on a node's rim facing the other end of the line, which is where the line starts.
    private static Point toward(final Point from, final Point to, final int radius) {
        final double dx = to.getX() - from.getX();
        final double dy = to.getY() - from.getY();
        final double length = Math.sqrt((dx * dx) + (dy * dy));
        if (length <= 0) {
            return from;
        }
        return new Point(from.getX() + ((dx / length) * radius),
                from.getY() + ((dy / length) * radius));
    }

    private static void line(final HtmlBuilder svg, final String uuid, final EdgeAppearance want) {
        svg.elem(SafeHtmlUtil.from("line"),
                // Which node this line arrives at, so the line can be found again rather than drawn
                // again when the drawing is brought up to date.
                new Attribute("edge", uuid),
                new Attribute("x1", String.valueOf(want.x1())),
                new Attribute("y1", String.valueOf(want.y1())),
                new Attribute("x2", String.valueOf(want.x2())),
                new Attribute("y2", String.valueOf(want.y2())),
                new Attribute("stroke-width", String.valueOf(want.width())),
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

    private static int leaves(final PathNode node) {
        final List<PathNode> children = NullSafe.list(node.getChildren());
        if (children.isEmpty()) {
            return 1;
        }
        int count = 0;
        for (final PathNode child : children) {
            count += leaves(child);
        }
        return count;
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
