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

import stroom.data.grid.client.DefaultResources;
import stroom.dispatch.client.DefaultErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.pathways.client.presenter.PathwayTreePresenter.PathwayTreeView;
import stroom.pathways.shared.FindPathwayMutationCriteria;
import stroom.pathways.shared.PathwaysResource;
import stroom.pathways.shared.otel.trace.NanoTime;
import stroom.pathways.shared.pathway.NodeUsage;
import stroom.pathways.shared.pathway.PathNode;
import stroom.pathways.shared.pathway.Pathway;
import stroom.pathways.shared.pathway.PathwayMutation;
import stroom.pathways.shared.pathway.PathwayUsage;
import stroom.svg.client.Preset;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.PageResponse;
import stroom.widget.button.client.ButtonView;
import stroom.widget.button.client.InlineSvgToggleButton;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.tab.client.view.GlobalResizeObserver;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.MySingleSelectionModel;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Node;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.Position;
import com.google.gwt.event.dom.client.ContextMenuEvent;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

public class PathwayTreePresenter
        extends MyPresenterWidget<PathwayTreeView> {

    private static final int ROW_HEIGHT = 22;
    private static final int INDENT = 20;
    private static final String SELECTED_CLASS = "pathway-nodeName--selected";
    private static final String HIGHLIGHT_CLASS = "pathway-node--changed";
    // The same marking, over once rather than over and over. While the model is being stepped
    // through, the next step arrives before a long run has finished and every node is caught part
    // way through one; a single pass finishes and settles before the drawing is replaced.
    private static final String HIGHLIGHT_ONCE_CLASS = "pathway-node--changed-once";
    // A path walked, which is marked differently from a change: each node keeps a ring once the walk
    // has reached it, so what has been covered can be seen rather than held in the reader's head.
    private static final String WALK_CLASS = "pathway-node--walk";
    // What draws the line reaching a node, and what clears it again once the node has been reached.
    // Named here because when each runs is written onto the line rather than held in the stylesheet —
    // only the walk knows the order.
    private static final String WALK_BADGE_SHOWN = "pathway-walk-badge-shown";
    private static final String WALK_BADGE_GONE = "pathway-walk-badge-gone";
    private static final String WALK_EDGE_DRAWN = "pathway-walk-drawn";
    private static final String WALK_EDGE_CLEARED = "pathway-walk-cleared";
    // What marks a node the walk has reached, and how long one of those takes. Said here rather than
    // only in the stylesheet because a node can be reached more than once, and only the walk knows
    // that — a rule can hold one run, not a list of them.
    private static final String GRAPH_PULSE = "pathway-node-changed-ring";
    private static final String TREE_PULSE = "pathway-node-changed";
    private static final int PULSE_MS = 1100;
    // How long the walk waits between one node and the next. The same for every path however many
    // nodes it has: worked out from the length instead, a long path would travel so fast that the
    // walk could not be followed, which is the whole of what it is for.
    private static final int PATH_STAGGER_MS = 400;
    private static final String GRAPH_TITLE = "Show as a graph";
    private static final String TREE_TITLE = "Show as a tree";
    private static final int MAX_HISTORY = 20000;
    private static final int HISTORY_DELAY_MILLIS = 400;
    private static final PathwaysResource PATHWAYS_RESOURCE = GWT.create(PathwaysResource.class);

    private final InlineSvgToggleButton viewButton;
    private final RestFactory restFactory;
    private final PathwayViewport viewport;
    private Runnable viewChangeHandler;
    // Told when a node is asked about on the drawing, so whoever knows what can be done with one can
    // offer it. Not decided here: this draws the model and says what was picked, and what a reader may
    // do with a node belongs with the things that would do it.
    private BiConsumer<PathNode, PopupPosition> nodeMenuHandler;
    private boolean historyPending;
    private boolean blanked;
    private String wantedSelection;
    // Whether the drawing was last made against a panel with no size. Put right when the panel has
    // one, rather than on every showing, so a reader who zoomed in and looked at something else keeps
    // the drawing where they left it.
    private boolean drewWhileHidden;
    private final Timer historyRequest = new Timer() {
        @Override
        public void run() {
            fetchHistoryNow();
        }
    };
    private boolean stepping;

    private final HTML html;
    private final MySingleSelectionModel<PathNode> selectionModel = new MySingleSelectionModel<PathNode>();
    private final PathwayRenderer treeRenderer = new PathwayTreeRenderer();
    private final PathwayRenderer graphRenderer = new PathwayGraphRenderer();
    // The graph to begin with: it says how much has gone through each part of the model and how
    // recently, which is what a reader opening a pathway is usually after. The tree is a click away
    // for reading the structure itself.
    private PathwayRenderer renderer = graphRenderer;
    // How much each node has changed and when it last did, by uuid. Worked out from the stored changes
    // rather than held on the node: the changes already say it, and holding it twice would let the two
    // differ.
    private MutationCounts counts = MutationCounts.of(Collections.emptyList(), 0);
    // The moment being looked at. Zero while the model on show is the current one, in which case it
    // is the time now.
    private long asAt;
    // Every node the model has ever held, which the drawing places from so that a node appearing does
    // not move the ones around it. Null where the model on show is the current one and is its own
    // answer.
    private PathNode layout;
    private List<PathwayUsage> usage = Collections.emptyList();
    // The change being wound back to, which says which reading applies.
    private long upTo;
    private List<PathwayMutation> history = Collections.emptyList();
    private long changeCeiling;
    // Nodes to draw attention to, each path key against every place it takes in the order they were
    // given in — every place, because a walk can reach one node more than once. Held rather than
    // applied once, because the drawing is rebuilt whenever the model is and the elements it was put
    // on go with it.
    private Map<String, List<Integer>> highlighted = Collections.emptyMap();
    // Whether what is picked out is a walk, which is shown one node after another and once, rather
    // than a change, which moved every node it touched at the same moment.
    private boolean highlightedPath;
    // Which runs of work that happened at the same time begin at each node, against the moment each
    // begins at. By node uuid, which is how the line into it is found.
    private Map<String, List<int[]>> runStarts = Collections.emptyMap();
    private List<Integer> holds = Collections.emptyList();
    private boolean showKey;
    // Where to ask for the changes if nothing hands them over. The view around this one may already
    // hold them, in which case it gives them and nothing is fetched.
    private DocRef docRef;
    private String historyFor;

    private Pathway pathway;
    private Element selectedElement;
    private PathNode selectedNode;
    private final Map<String, PathNode> nodeMap = new HashMap<>();

    @Inject
    public PathwayTreePresenter(final EventBus eventBus,
                                final PathwayTreeView view,
                                final RestFactory restFactory,
                                final DefaultResources resources) {
        super(eventBus, view);
        this.restFactory = restFactory;
        // Which way the model is drawn, not what is drawn, so it sits with the tree rather than with
        // the buttons that change the model. It holds which drawing is on show, so nothing else has to.
        //
        // Made but not put on the toolbar, so the graph is the only drawing a reader gets. Kept rather
        // than taken out because what it holds is where the choice lives, and putting it back is the
        // one line that adds it.
        viewButton = new InlineSvgToggleButton();
        viewButton.setSvg(SvgImage.NODES);
        // Turned on to match the drawing it starts on, so the button and what is on show agree.
        viewButton.setState(true);
        viewButton.setTitle(TREE_TITLE);

        html = new HTML();
        html.addStyleName("max");
        // What the graph's key is placed against. The drawing inside scrolls and is scaled; the key
        // is neither, so it hangs off the panel rather than off the drawing.
        html.getElement().getStyle().setPosition(Position.RELATIVE);
        viewport = new PathwayViewport(html);
        // Asked of whichever drawing is on show rather than of the one on show now, because the
        // toolbar swaps them and fitting is a button press that can come at any time after that.
        viewport.setArea(() -> renderer.drawnArea());
        // Scaling the drawing does not draw it again, so what it measures in screen pixels rather
        // than in its own units is put right here instead.
        viewport.setScaleHandler(() -> renderer.rescale(html.getElement(), viewport.scale()));
        view.setDataWidget(html);
    }

    @Override
    protected void onBind() {
        // A drawing is fitted and placed against the room it has. Made while its panel was off screen
        // it was fitted against nothing, and the fit is spent, so showing the panel again leaves it
        // zoomed out and off centre. Watched here rather than waiting to be told: what matters is the
        // panel having a size, and nothing above knows that better than the panel does — a tab behind
        // another measures nothing whichever way it came to be hidden.
        GlobalResizeObserver.addListener(html.getElement(), element -> {
            if (drewWhileHidden && html.getOffsetWidth() > 0 && html.getOffsetHeight() > 0) {
                drewWhileHidden = false;
                viewport.fitOnNextDraw();
                refresh(false);
            }
        });

        super.onBind();
        // Follows the button rather than deciding for itself which clicks count: the button turns
        // itself over on any click it accepts, and a drawing that disagreed with the icon on it would
        // be worse than a drawing swapped by an unusual click.
        registerHandler(viewButton.addClickHandler(e -> swapView()));
        registerHandler(html.addMouseDownHandler(e -> {
            // Only the graph is dragged. The tree scrolls a row at a time and reads top to bottom;
            // there is nothing to move around in it.
            if (!renderer.isPannable()) {
                return;
            }

            // Not from the buttons sitting over the drawing: they are there to be pressed.
            if (isControl(e.getNativeEvent().getEventTarget().cast())) {
                return;
            }
            viewport.startDrag(e.getClientX(), e.getClientY());
        }));

        registerHandler(html.addMouseMoveHandler(e -> viewport.drag(e.getClientX(), e.getClientY())));

        // The wheel scales the drawing rather than scrolling it. Only the graph: the tree is a list
        // read top to bottom, where a wheel means what it means everywhere else. The browser's own
        // answer is refused, or the panel would scroll as well as scale.
        registerHandler(html.addMouseWheelHandler(e -> {
            if (!renderer.isPannable()) {
                return;
            }
            e.preventDefault();
            if (e.isNorth()) {
                viewport.zoomIn(e.getClientX(), e.getClientY());
            } else {
                viewport.zoomOut(e.getClientX(), e.getClientY());
            }
        }));

        // A closer look at whatever was double clicked. The two clicks inside it still do what a click
        // does, so a node is picked out as well as moved in on, which is what someone double clicking
        // a node is asking for.
        registerHandler(html.addDoubleClickHandler(e -> {
            if (!renderer.isPannable() || isControl(e.getNativeEvent().getEventTarget().cast())) {
                return;
            }
            viewport.zoomIn(e.getClientX(), e.getClientY());
        }));

        // Showing another tab takes the drawing off the screen, and putting anything back on the
        // screen starts every animation it carries over again. A path already walked would walk
        // itself a second time for no reason but the reader having looked at something else, so the
        // drawing is put into the state the walk leaves it in on the way out.
        registerHandler(html.addAttachHandler(e -> {
            if (!e.isAttached()) {
                settle(html.getElement());
            }
        }));
        registerHandler(html.addMouseUpHandler(e -> viewport.endDrag()));

        registerHandler(html.addClickHandler(e -> {
            // A drag ends in a click. Selecting whatever the pointer happened to come to rest on would
            // be a surprise, so the click that ends one is let go.
            if (viewport.wasDragged()) {
                return;
            }

            final Element target = e.getNativeEvent().getEventTarget().cast();
            if (target == null) {
                return;
            }

            // The drawing carries its own controls, so a click is theirs before it is a node's.
            final Element button = ElementUtil.findParent(target, element ->
                    NullSafe.isNonBlankString(element.getId()), 3);
            if (button != null && onControl(button.getId())) {
                return;
            }

            final Element node = ElementUtil.findParent(target, element ->
                    NullSafe.isNonBlankString(element.getAttribute("uuid")), 3);
            if (node != null) {
                select(nodeMap.get(node.getAttribute("uuid")), node);
            }
        }));

        // Asking about a node on the drawing. The node is picked out first, because everything offered
        // acts on whatever is picked and a menu over a node the reader had not chosen would act
        // somewhere else. The browser's own menu is refused: what it offers over a drawing is nothing
        // anyone wants here.
        registerHandler(html.addDomHandler(e -> {
            final Element target = e.getNativeEvent().getEventTarget().cast();
            if (target == null || nodeMenuHandler == null) {
                return;
            }
            final Element node = ElementUtil.findParent(target, element ->
                    NullSafe.isNonBlankString(element.getAttribute("uuid")), 3);
            if (node == null) {
                return;
            }
            e.preventDefault();
            e.stopPropagation();
            final PathNode picked = nodeMap.get(node.getAttribute("uuid"));
            select(picked, node);
            nodeMenuHandler.accept(picked, new PopupPosition(
                    e.getNativeEvent().getClientX(), e.getNativeEvent().getClientY()));
        }, ContextMenuEvent.getType()));
    }

    @Override
    protected void onUnbind() {
        GlobalResizeObserver.removeListener(html.getElement());
        super.onUnbind();
    }

    // The drawing's own buttons, which are the only things on it carrying an id.
    private static boolean isControl(final Element target) {
        return target != null && ElementUtil.findParent(target, element ->
                NullSafe.isNonBlankString(element.getId()), 3) != null;
    }

    // Selecting must not redraw the tree. Rebuilding it throws away where the view is scrolled to, so
    // the picture jumps back to the top every time a node is clicked. Only the two nodes whose state
    // changed are touched.
    private void select(final PathNode pathNode, final Element element) {
        if (Objects.equals(uuid(selectedNode), uuid(pathNode))) {
            return;
        }

        if (selectedElement != null) {
            selectedElement.removeClassName(SELECTED_CLASS);
        }
        selectedElement = element;
        if (selectedElement != null) {
            selectedElement.addClassName(SELECTED_CLASS);
        }

        selectedNode = pathNode;
        if (pathNode == null) {
            selectionModel.clear();
        } else {
            selectionModel.setSelected(pathNode, true);
        }
    }

    // The two drawings answer different questions of the same model, so which one is on show is not
    // worth a redraw of anything but the drawing. What is selected is kept: a node is the same node
    // however it is drawn.
    //
    // The button has already turned itself over by the time this runs — it handles its own click
    // before the one registered here — so which drawing to use is read from it rather than tracked.
    private void swapView() {
        renderer = viewButton.getState()
                ? graphRenderer
                : treeRenderer;
        viewButton.setTitle(viewButton.getState()
                ? TREE_TITLE
                : GRAPH_TITLE);

        fetchHistory();
        // Where the drawing arriving was last left, rather than the middle. Only the first sight of a
        // model opens in the middle; after that the reader put it where they wanted it.
        viewport.swapDrawing();
        refresh(true);
        if (viewChangeHandler != null) {
            viewChangeHandler.run();
        }
    }

    // Only the graph needs the changes, and only for colour, so they are asked for when it is first
    // shown rather than with every pathway opened. A page of them is large enough that the pathway
    // list was unopenable while it read them.
    // Asked for a moment after the reader stops moving, not as they move. Running down a list of
    // pathways with the arrow keys would otherwise ask for the whole history of every one passed
    // over, and every answer but the last would be thrown away.
    private void fetchHistory() {
        historyRequest.cancel();
        if (renderer.usesHistory() && docRef != null) {
            historyRequest.schedule(HISTORY_DELAY_MILLIS);
        }
    }

    private void fetchHistoryNow() {
        final String name = NullSafe.get(pathway, Pathway::getName);
        if (!renderer.usesHistory() || docRef == null || name == null || name.equals(historyFor)) {
            return;
        }

        historyFor = name;
        final FindPathwayMutationCriteria criteria = new FindPathwayMutationCriteria(
                new PageRequest(0, MAX_HISTORY),
                List.of(new CriteriaFieldSort(PathwayMutation.FIELD_TIME, false, false)),
                docRef,
                name);
        restFactory
                .create(PATHWAYS_RESOURCE)
                .method(res -> res.findMutations(criteria))
                .onSuccess(result -> {
                    // Another pathway may have been picked while this was in flight, and colouring one
                    // model by another model's changes would be worse than not colouring it at all.
                    if (name.equals(NullSafe.get(pathway, Pathway::getName))) {
                        // A page of them rather than all of them says nothing dependable about how
                        // much a node has changed or when it last did. Better to colour nothing than
                        // to colour it from part of the story with no way of saying so.
                        final long total = NullSafe.getOrElse(
                                result.getPageResponse(), PageResponse::getTotal, 0L);
                        setHistory(result.getValues().size() >= total
                                ? result.getValues()
                                : Collections.emptyList());
                        refresh(true);
                    }
                })
                .onFailure(new DefaultErrorHandler(this, () -> {
                    // Asked and not answered. Drawn with nothing known rather than left blank, which
                    // would look like a model with no nodes in it.
                    setHistory(Collections.emptyList());
                    refresh(true);
                }))
                .taskMonitorFactory(this)
                .exec();
    }

    // @return whether the click was on one of the drawing's own controls rather than on the drawing.
    private boolean onControl(final String id) {
        return renderer.onControl(id, new PathwayControls() {
            @Override
            public void zoomIn() {
                viewport.zoomIn();
            }

            @Override
            public void zoomOut() {
                viewport.zoomOut();
            }

            @Override
            public void zoomToExtent() {
                viewport.zoomToExtent();
            }

            @Override
            public void toggleLegend() {
                // Drawn again rather than opened in place: the drawing owns its own markup, and it is
                // rebuilt on every step through the history anyway.
                showKey = !showKey;
                refresh(true);
            }
        });
    }

    /**
     * Puts the drawing back in the middle, for a panel that has changed size around it. Only for a
     * drawing that opens in the middle: a tree is read from its top left and would jump.
     */
    public void centre() {
        if (renderer.opensCentred()) {
            viewport.recentre(this::rootElement);
        }
    }

    /**
     * Whether the model is being shown as a graph rather than as a tree.
     */
    public boolean isGraphShown() {
        return renderer == graphRenderer;
    }

    /**
     * Whether the model is being stepped through rather than looked at a change at a time. What
     * changed is marked once and left, instead of going on drawing the eye to a node that is about
     * to be replaced.
     */
    public void setStepping(final boolean stepping) {
        this.stepping = stepping;
    }

    /**
     * Told whenever the drawing is swapped for the other one, so a view around this one can offer
     * whatever only makes sense against one of them.
     */
    public void setNodeMenuHandler(final BiConsumer<PathNode, PopupPosition> nodeMenuHandler) {
        this.nodeMenuHandler = nodeMenuHandler;
    }

    public void setViewChangeHandler(final Runnable viewChangeHandler) {
        this.viewChangeHandler = viewChangeHandler;
    }

    public void read(final Pathway pathway) {
        // Reading the same pathway again is the model being wound back, and the reader is looking at
        // the same tree, so it stays where they left it. A different pathway starts at the top.
        final boolean samePathway = this.pathway != null
                                    && pathway != null
                                    && Objects.equals(this.pathway.getName(), pathway.getName());

        if (!samePathway) {
            // A different model, which may be a different size altogether. Keeping the zoom set for
            // the last one would show this one at whatever suited that, so it opens showing all of
            // itself instead.
            viewport.fitOnNextDraw();
        }

        // Whether there is a node to go back to. A reader stepping through the model's changes has
        // one and keeps it; one who has just been handed this model has none, whether or not it is
        // the same model they were shown last time.
        final boolean nothingSelected = selectedNode == null && wantedSelection == null;

        this.pathway = pathway;
        // Held changes belong to whichever pathway they were fetched for. Where that is not this one,
        // a drawing that needs them waits: either the fetch below answers, or whoever opened this
        // hands them over.
        historyPending = !Objects.equals(NullSafe.get(pathway, Pathway::getName), historyFor);
        selectionModel.clear();
        refresh(samePathway);
        if (!samePathway || nothingSelected) {
            // The root is the one node every model has, and what the whole pathway is named after, so
            // a model just opened has it picked out rather than nothing.
            selectRoot();
        }
        // A different pathway was picked, so the changes behind the one on show are not the ones held.
        fetchHistory();
    }

    /**
     * The nodes to draw attention to when the model is next read — the ones a change being looked at
     * touched. They are picked out for a moment rather than marked, because it is what just happened
     * that is worth seeing, not a state the node is in.
     *
     * <p>All at once, because a change moved all of them at the same moment. A path did not, and has
     * {@link #setHighlightedPath(List, Map)} instead.
     */
    public void setHighlighted(final List<List<String>> paths) {
        highlighted = new HashMap<>();
        highlightedPath = false;
        runStarts = Collections.emptyMap();
        holds = Collections.emptyList();
        // All at one moment, so one place each and all of them the same.
        NullSafe.list(paths).forEach(path -> highlighted.putIfAbsent(key(path), Collections.singletonList(0)));
    }

    /**
     * The nodes a path ran, in the sequence it ran them. Picked out one after another rather than
     * together, so the walk can be followed rather than only seen.
     */
    public void setHighlightedPath(final List<List<List<String>>> moments,
                                    final Map<String, List<int[]>> runStarts,
                                    final List<Integer> holds) {
        highlighted = new HashMap<>();
        highlightedPath = true;
        this.runStarts = NullSafe.map(runStarts);
        this.holds = NullSafe.list(holds);
        // Which moments of the walk each node lights at. A node the walk comes back to is held off
        // twice and lights at both; everything sharing a moment lights at once, which is how work that
        // ran at the same time is shown to have done so rather than reading as a sequence.
        final List<List<List<String>>> list = NullSafe.list(moments);
        for (int i = 0; i < list.size(); i++) {
            final int moment = i;
            NullSafe.list(list.get(i)).forEach(path ->
                    highlighted.computeIfAbsent(key(path), k -> new ArrayList<>()).add(moment));
        }
    }

    /**
     * Which pathways document the model on show belongs to, so the changes behind it can be asked for
     * where nothing hands them over.
     */
    public void setDocRef(final DocRef docRef) {
        this.docRef = docRef;
    }

    /**
     * How often the model has been taught something about one node, and when it last was, as at the
     * point the drawing is showing. Taken from here rather than worked out again so that anything
     * else saying it cannot disagree with the picture.
     */
    public NodeChange getChange(final List<String> nodePath) {
        return nodePath == null
                ? null
                : counts.node(nodePath);
    }

    /**
     * Every change made to this pathway, which is where the graph gets the colour of a node from.
     * Handing them over stops them being asked for. Takes effect on the next read.
     */
    public void setHistory(final List<PathwayMutation> history) {
        historyFor = NullSafe.get(pathway, Pathway::getName);
        historyPending = false;
        this.history = NullSafe.list(history);
        counts = MutationCounts.of(this.history, upTo);

        // What the sizes are measured against, taken over the whole history rather than the part
        // being shown. Measured against the part being shown, the largest node of the moment would
        // always be drawn at full size, so winding back would rescale the picture rather than shrink
        // it. Worked out once here, because the history is what it depends on and that does not
        // change as the reader moves through it.
        changeCeiling = MutationCounts.of(this.history, 0).getMostChangedNode();
    }

    /**
     * Readings of how much each node had been used, one per trace that changed the model. Given
     * rather than fetched, like the changes themselves.
     */
    public void setUsage(final List<PathwayUsage> usage) {
        this.usage = NullSafe.list(usage);
    }

    // The reading taken for the trace the change being looked at belongs to, which is the first at or
    // after it: a reading is written once the trace is done, numbered with the last change it made.
    // Taking the one before instead would report the model as it stood a whole trace earlier, so a
    // node could read as changed more often than it had been used.
    private Map<String, NodeUsage> usageAsAt() {
        final Map<String, NodeUsage> byUuid = new HashMap<>();
        if (asAt <= 0 || upTo <= 0) {
            return byUuid;
        }

        PathwayUsage reading = null;
        for (final PathwayUsage candidate : usage) {
            if (candidate.getSequence() >= upTo
                && (reading == null || candidate.getSequence() < reading.getSequence())) {
                reading = candidate;
            }
        }
        if (reading != null) {
            NullSafe.list(reading.getNodes()).forEach(node -> byUuid.put(node.getNodeUuid(), node));
        }
        return byUuid;
    }

    /**
     * The model to work the positions out from, which is the model as it stands now rather than the
     * one being shown. Null where they are the same.
     */
    public void setLayout(final PathNode layout) {
        this.layout = layout;
    }

    /**
     * The moment the model on show stood at, or null where it is the model as it stands now. How long
     * ago a node changed is measured from here, so winding back an hour does not age every node by an
     * hour.
     */
    public void setAsAt(final NanoTime asAt, final Long upTo) {
        this.asAt = asAt == null
                ? 0L
                : asAt.toEpochMillis();
        this.upTo = upTo == null
                ? 0L
                : upTo;
        counts = MutationCounts.of(history, this.upTo);
    }

    private static String key(final List<String> path) {
        return String.join("\u0000", NullSafe.list(path));
    }

    private void selectRoot() {
        final PathNode root = NullSafe.get(pathway, Pathway::getRoot);
        if (root != null) {
            if (waiting()) {
                // Nothing is drawn, so nothing is picked out. Remembered, and done once there is.
                wantedSelection = root.getUuid();
            } else {
                reselect(root.getUuid());
            }
        }
    }

    // Whether the drawing on show is made from the model's changes and has not been given them.
    private boolean waiting() {
        return renderer.usesHistory() && historyPending;
    }

    private void reselect(final String uuid) {
        if (uuid != null) {
            final PathNode node = nodeMap.get(uuid);
            if (node != null) {
                select(node, ElementUtil.findChild(html.getElement(),
                        element -> uuid.equals(element.getAttribute("uuid"))));
            }
        }
    }

    private static String uuid(final PathNode pathNode) {
        return pathNode == null
                ? null
                : pathNode.getUuid();
    }

    // Draws the whole thing. Only a new pathway needs this; selecting a node does not.
    private void refresh(final boolean keepScroll) {
        // What was selected, so it can be picked out again once the drawing has been rebuilt. Kept
        // here rather than at each place that redraws: a node keeps its uuid when the model is wound
        // back, so the same node is still the same node, and a redraw that forgot to put the
        // selection back would leave the node marked in one place and not in the other.
        final String was = uuid(selectedNode);
        selectedNode = null;
        nodeMap.clear();
        selectedElement = null;

        if (pathway != null && pathway.getRoot() != null) {
            addNode(pathway.getRoot());
        }

        // A drawing that says how much each node has changed cannot be made before that is known.
        // Drawn anyway, every node would appear at its smallest and in the colour of never having
        // changed, and then jump as the answer arrived. Nothing is better than something wrong, so
        // nothing is drawn until the changes arrive, and whichever node is to be picked out waits
        // with it.
        if (waiting()) {
            html.setHTML(SafeHtmlUtils.EMPTY_SAFE_HTML);
            blanked = true;
            // Whatever was on show, or else whatever was already waiting to be picked out. A second
            // drawing made before the first has anything to draw has nothing on show to remember, and
            // taking that as the answer would forget the node the first one asked for.
            if (was != null) {
                wantedSelection = was;
            }
            selectionModel.clear();
            return;
        }

        // Whatever the caller says, a drawing that follows an empty panel is the first one made of
        // this model, so it opens where a first drawing opens rather than where the last one of some
        // other model was left.
        final boolean keep = keepScroll && !blanked;
        blanked = false;

        // Either what was on show a moment ago, or what was asked for while there was nothing to
        // show it on.
        final String restore = was != null
                ? was
                : wantedSelection;
        wantedSelection = null;

        // Worked out before the drawing is built as well as after it: the drawing is made faint
        // around the nodes a path ran, and then they are marked on it.
        final Map<String, List<Integer>> wanted = wanted();

        final RenderRequest request = new RenderRequest(pathway, layout, byUuid(), usageAsAt(),
                asAt > 0
                        ? asAt
                        : System.currentTimeMillis(),
                changeCeiling,
                mostUsed(layout == null
                        ? NullSafe.get(pathway, Pathway::getRoot)
                        : layout),
                showKey,
                // Only a path says which nodes did not run. A change touched the nodes it touched
                // and says nothing about the rest, so nothing is faded for one.
                highlightedPath
                        ? wanted.keySet()
                        : Collections.emptySet(),
                viewport.scale());

        // Changed in place where the drawing on the screen is of the same model laid out the same
        // way, which is what stepping through the history is: only how large each node is, what
        // colour it is and whether it is there yet moves between one step and the next. Rebuilding it
        // every step throws away the element the reader scrolled, so the scroll and the scale have to
        // be taken off and put back, and every animation starts again — which is what makes a replay
        // hard to watch. The renderer says whether it managed it; anything else falls through to
        // building the lot, which is always right.
        if (html.getOffsetWidth() <= 0 || html.getOffsetHeight() <= 0) {
            // Fitted and placed against a panel with no size, which is what a panel on a tab nobody
            // is looking at measures. Noted so it can be made again once there is something to
            // measure.
            drewWhileHidden = true;
        }

        final boolean changedInPlace = keep && renderer.update(html.getElement(), request);
        if (!changedInPlace) {
            // The element that scrolls is the one being rebuilt below, so where it had got to has to
            // be taken off it first and put back on the one that replaces it.
            viewport.beforeDraw();
            html.setHTML(renderer.render(request));
            viewport.afterDraw(keep, renderer.opensCentred(), renderer.isZoomable(),
                    this::rootElement);
        }
        reselect(restore);
        if (!wanted.isEmpty()) {
            if (changedInPlace) {
                // Asked for and thrown away. Changing a node in place takes its marking off and the
                // marking below puts it straight back, and a run that was never settled in between is
                // a run the browser carries on rather than starts again — so the one thing a replay
                // has to show, which node moved this time, would never be seen after the first step.
                html.getElement().getOffsetWidth();
            }
            mark(html.getElement(), wanted, runStarts, holds, highlightClass(), pulse(), stagger());
        }
    }

    // The most any one node has been used, over the model as it stands rather than the part on show,
    // so that winding back thins the lines rather than rescaling them.
    private static long mostUsed(final PathNode node) {
        if (node == null) {
            return 0L;
        }
        long most = node.getTimesUsed();
        for (final PathNode child : NullSafe.list(node.getChildren())) {
            most = Math.max(most, mostUsed(child));
        }
        return most;
    }

    /**
     * Puts the middle of the drawing in the middle of the view.
     *
     * <p>Deferred, because the panel has no width in the turn the drawing is put on the page, and
     * centring against a width of nothing puts the middle of the drawing against the left edge.
     *
     * <p>Held as something wanted rather than done to one element, because opening a pathway draws it
     * three times — once for the model, again when its changes arrive, and again when the readings do
     * — and the drawing is rebuilt each time. Pinned to the element it was asked for, it would fire
     * against one already thrown away, which is why the middle was only sometimes found.
     */
    private Element rootElement() {
        final PathNode root = layout == null
                ? NullSafe.get(pathway, Pathway::getRoot)
                : layout;
        if (root == null) {
            return null;
        }
        return ElementUtil.findChild(html.getElement(), element ->
                root.getUuid().equals(element.getAttribute("uuid")));
    }

    // The changes against the nodes the model actually holds, worked out once the model has been read
    // and its nodes are known.
    private Map<String, NodeChange> byUuid() {
        final Map<String, NodeChange> byUuid = new HashMap<>();
        nodeMap.values().forEach(node -> {
            final NodeChange change = counts.node(node.getNodePath());
            if (change != null) {
                byUuid.put(node.getUuid(), change);
            }
        });
        return byUuid;
    }

    // The nodes being picked out, by uuid and where each comes in the order. Looked up this way round
    // rather than a search of the drawing for each of them in turn: picking a trace marks most of the
    // model at once, and a search that starts again at the top for every node walks the whole drawing
    // as many times as there are nodes in it.
    private Map<String, List<Integer>> wanted() {
        if (highlighted.isEmpty()) {
            return Collections.emptyMap();
        }
        final Map<String, List<Integer>> wanted = new HashMap<>();
        nodeMap.values().forEach(node -> {
            final List<Integer> places = highlighted.get(key(node.getNodePath()));
            if (places != null) {
                wanted.put(node.getUuid(), places);
            }
        });
        return wanted;
    }

    // How far apart to start the nodes of the path on show. Nothing for a change, whose nodes all
    // moved at once.
    private int stagger() {
        return highlightedPath
                ? PATH_STAGGER_MS
                : 0;
    }

    // What to run on a node the walk reaches, or nothing where the stylesheet already says how — which
    // is a change, whose nodes all move at one moment and need no list.
    private String pulse() {
        if (!highlightedPath) {
            return null;
        }
        return renderer == graphRenderer
                ? GRAPH_PULSE
                : TREE_PULSE;
    }

    // One run of the marking for every time the walk reaches this node. A single hold off can only say
    // one moment, and a node its parent ran, came away from and ran again is reached at two.
    private static void pulse(final Element element,
                              final String animation,
                              final List<Integer> places,
                              final int stagger) {
        final StringBuilder names = new StringBuilder();
        final StringBuilder durations = new StringBuilder();
        final StringBuilder delays = new StringBuilder();
        for (final Integer place : places) {
            if (names.length() > 0) {
                names.append(", ");
                durations.append(", ");
                delays.append(", ");
            }
            names.append(animation);
            durations.append(PULSE_MS).append("ms");
            delays.append(place * stagger).append("ms");
        }

        final Style style = element.getStyle();
        style.setProperty("animationName", names.toString());
        style.setProperty("animationDuration", durations.toString());
        style.setProperty("animationDelay", delays.toString());
        // One value stands for the whole list.
        style.setProperty("animationTimingFunction", "ease-out");
        style.setProperty("animationIterationCount", "1");
    }

    private String highlightClass() {
        if (highlightedPath) {
            // Walked once: the nodes are held off one after another to show the walk, and running that
            // over and over would replay it rather than show it.
            return WALK_CLASS;
        }
        return stepping
                ? HIGHLIGHT_ONCE_CLASS
                : HIGHLIGHT_CLASS;
    }

    private static void mark(final Element element,
                             final Map<String, List<Integer>> wanted,
                             final Map<String, List<int[]>> runStarts,
                             final List<Integer> holds,
                             final String highlightClass,
                             final String pulse,
                             final int stagger) {
        final List<Integer> places = wanted.get(element.getAttribute("uuid"));
        if (places != null) {
            if (pulse == null) {
                // A change moved every node it moved at one moment, so the stylesheet says how the
                // marking runs and this only says when. Left to go with the drawing: the next read
                // builds the markup again, so nothing has to take it back off.
                element.getStyle().setProperty("animationDelay", (places.get(0) * stagger) + "ms");
            } else {
                pulse(element, pulse, places, stagger);
            }
            element.addClassName(highlightClass);
            // The veil over a node the walk has not reached and the name written beside it, both of
            // which change as the walk arrives. Told on their own because they are children of the
            // node, and a hold off put on a parent is not a hold off on what is inside it. The first
            // arrival is what changes them; coming back does not make the node any more reached.
            holdOff(child(element, PathwayGraphRenderer.VEIL_CLASS), places.get(0) * stagger);
            holdOff(child(element, PathwayGraphRenderer.LABEL_CLASS), places.get(0) * stagger);
        }

        // The line reaching a node, drawn over the gap before that node lights so the path traces
        // itself along rather than blinking from one node to the next. A line is long and thin and can
        // still be seen where a ring round a dot cannot.
        // Drawn again for every arrival, because one line joins these two nodes however often the walk
        // runs along it, and a walk that lit the node the second time without running the line would
        // say it got there without going.
        // The line itself, not the badge that sits on it. Both are found by the node at the far end,
        // so both carry the same mark, and only the class tells them apart.
        final List<Integer> arrivals = wanted.get(element.getAttribute("edge"));
        if (arrivals != null
            && stagger > 0
            && has(element.getAttribute("class"), PathwayGraphRenderer.WALK_EDGE_CLASS)) {
            drawWalkEdge(element, arrivals, holds, stagger);
        }

        // The badges on the line into a node, one for each run of work happening at the same time that
        // begins there. The drawing puts one empty badge on every line, because which runs there are
        // is not known until a path is picked; the number and when it shows are written on here.
        //
        // A node that opens two runs — two of them beginning by doing the same thing — needs a badge
        // for each, taken by copying the one already drawn rather than building svg by hand. A badge
        // already written on is left alone, which is what stops a copy being copied again as the walk
        // goes over the children it has just been added to.
        if (stagger > 0
            && has(element.getAttribute("class"), PathwayGraphRenderer.WALK_BADGE_CLASS)
            && element.getAttribute("run").isEmpty()) {
            final List<int[]> starts = runStarts.get(element.getAttribute("edge"));
            if (!NullSafe.isEmptyCollection(starts)) {
                badge(element, starts.get(0), holds, stagger);
                for (int i = 1; i < starts.size(); i++) {
                    final Element copy = Element.as(element.cloneNode(true));
                    element.getParentElement().appendChild(copy);
                    badge(copy, starts.get(i), holds, stagger);
                }
            }
        }

        final NodeList<Node> children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            final Node node = children.getItem(i);
            if (Element.is(node)) {
                mark(Element.as(node), wanted, runStarts, holds, highlightClass, pulse, stagger);
            }
        }
    }

    // Written straight onto the element rather than put in a class, because a line is drawn in svg and
    // an svg element does not take a class name the way a div does. Nothing here touches how long the
    // line is or where it is held off from — the drawing set both as it placed the line, and this only
    // says when it runs.
    private static void drawWalkEdge(final Element element,
                                     final List<Integer> arrivals,
                                     final List<Integer> holds,
                                     final int durationMs) {
        // Two runs for every arrival: the line draws itself over the gap before the node it reaches,
        // and then clears again while that node is pinged. What is left is the drawing as it was, so a
        // magenta stretch travels along the path rather than the whole of it staying painted over.
        // Later runs are put after earlier ones because where two of them would say different things
        // about the line, the last one said wins, and the last one said is the one whose turn it is.
        final StringBuilder names = new StringBuilder();
        final StringBuilder durations = new StringBuilder();
        final StringBuilder delays = new StringBuilder();
        for (final Integer arrival : arrivals) {
            // A node reached at the very start of a walk has nothing before it to come from.
            if (arrival <= 0) {
                continue;
            }
            if (names.length() > 0) {
                names.append(", ");
                durations.append(", ");
                delays.append(", ");
            }
            final int delayMs = (arrival - 1) * durationMs;
            // Normally the line clears as soon as the node it reaches has lit. Inside a block of work
            // that happened at the same time it is held drawn until the last strand of that block is
            // done, so the block fills in line by line and then clears in one — which is the only
            // thing on the drawing saying where the block began and where it ended.
            final int goesOutAt = arrival < holds.size() && holds.get(arrival) > arrival
                    ? holds.get(arrival)
                    : arrival;
            names.append(WALK_EDGE_DRAWN).append(", ").append(WALK_EDGE_CLEARED);
            durations.append(durationMs).append("ms, ").append(durationMs).append("ms");
            delays.append(delayMs).append("ms, ").append(goesOutAt * durationMs).append("ms");
        }
        if (names.length() == 0) {
            return;
        }

        final Style style = element.getStyle();
        style.setProperty("animationName", names.toString());
        style.setProperty("animationDuration", durations.toString());
        style.setProperty("animationDelay", delays.toString());
        // One value stands for the whole list, and every run of a drawing is followed by a run of a
        // clearing, so these two alternate however many arrivals there are.
        style.setProperty("animationTimingFunction", "linear, ease-in");
        // Both ends held. Before its turn the line is off the end of itself by the offset it was given
        // as it was placed, and afterwards it is left cleared.
        style.setProperty("animationFillMode", "forwards");
    }

    // Shown while the line it sits on is being drawn, which is the gap before the node that run starts
    // at lights, so the number and the line arrive together, and left up for as long as that run's
    // lines are, so it goes out with them. The run it says is written onto the badge as well as into
    // it, which is how a badge already dealt with is told from one still empty.
    private static void badge(final Element element,
                              final int[] start,
                              final List<Integer> holds,
                              final int durationMs) {
        final int run = start[0];
        final int moment = start[1];
        final int of = start[2];
        element.setAttribute("run", String.valueOf(run));
        final Element number = child(element, PathwayGraphRenderer.WALK_BADGE_TEXT_CLASS);
        if (number != null) {
            // Which strand of how many, so a reader knows both where they are in the block and that
            // there is more of it to come. A bare number says the first but not the second.
            number.setInnerText(run + "/" + of);
        }

        // Up, then down, the same way the line under it is drawn and cleared. Two runs rather than one
        // long one because a fade said as a share of the whole would stretch with it, and the number
        // would drift up and down instead of being there and then gone.
        final int shownAt = Math.max(0, moment - 1) * durationMs;
        final int goneAt = moment < holds.size() && holds.get(moment) > moment
                ? holds.get(moment) * durationMs
                : shownAt + durationMs;
        final Style style = element.getStyle();
        style.setProperty("animationName", WALK_BADGE_SHOWN + ", " + WALK_BADGE_GONE);
        style.setProperty("animationDuration", durationMs + "ms, " + durationMs + "ms");
        style.setProperty("animationDelay", shownAt + "ms, " + Math.max(goneAt, shownAt + durationMs) + "ms");
        style.setProperty("animationTimingFunction", "ease-out");
        style.setProperty("animationIterationCount", "1, 1");
        // Both ends held, so the number stays up between going on and coming off.
        style.setProperty("animationFillMode", "forwards");
    }

    private static void holdOff(final Element element, final int delayMs) {
        if (element != null) {
            element.getStyle().setProperty("animationDelay", delayMs + "ms");
        }
    }

    // Where a walk leaves things: the nodes lit and still, the veils over them gone, the names up, and
    // the lines it traced cleared again. Said outright rather than left as an animation that has
    // finished, because an animation that has finished starts again the moment it is put back on the
    // screen.
    private static void settle(final Element element) {
        // Read and written as an attribute rather than through the class name methods, which go at the
        // className property — on an svg element that is not a string but an object, so asking it
        // whether it holds a name throws, and a throw in here is a throw in the middle of the tab
        // being taken off the screen. Lines are drawn in svg, so this walks over them.
        final String classes = element.getAttribute("class");
        if (has(classes, WALK_CLASS)) {
            element.setAttribute("class", without(classes, WALK_CLASS));
            // What the node runs is written onto the node, so taking the class off it is not enough to
            // stop it — unlike a change, which the stylesheet runs off its class alone.
            stop(element);
            hold(child(element, PathwayGraphRenderer.VEIL_CLASS), "0");
            hold(child(element, PathwayGraphRenderer.LABEL_CLASS), "1");
        } else if (has(classes, HIGHLIGHT_CLASS) || has(classes, HIGHLIGHT_ONCE_CLASS)) {
            // A change is picked out by the same sort of run, and has nothing to leave behind but
            // itself.
            element.setAttribute("class", without(without(classes, HIGHLIGHT_CLASS), HIGHLIGHT_ONCE_CLASS));
        } else if (has(classes, PathwayGraphRenderer.WALK_EDGE_CLASS)
                   || has(classes, PathwayGraphRenderer.WALK_BADGE_CLASS)) {
            // The lines a walk traced and the numbers that sat on them both go off and stay off. A
            // badge is told the same way as a line and for the same reason: its run is written onto it
            // rather than held in the stylesheet, so taking a class away would not stop it, and an
            // animation that has finished starts again the moment it is put back on the screen.
            hold(element, "0");
        }

        final NodeList<Node> children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            final Node node = children.getItem(i);
            if (Element.is(node)) {
                settle(Element.as(node));
            }
        }
    }

    private static boolean has(final String classes, final String name) {
        return (" " + classes + " ").contains(" " + name + " ");
    }

    private static String without(final String classes, final String name) {
        return (" " + classes + " ").replace(" " + name + " ", " ").trim();
    }

    private static void hold(final Element element, final String opacity) {
        if (element != null) {
            stop(element);
            element.getStyle().setProperty("opacity", opacity);
        }
    }

    private static void stop(final Element element) {
        // The whole of it, so that a run written on as a list of names goes with one word.
        element.getStyle().setProperty("animation", "none");
    }

    private static Element child(final Element element, final String className) {
        final NodeList<Node> children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            final Node node = children.getItem(i);
            if (Element.is(node) && has(Element.as(node).getAttribute("class"), className)) {
                return Element.as(node);
            }
        }
        return null;
    }

    private void addNode(final PathNode node) {
        nodeMap.put(node.getUuid(), node);
        NullSafe.list(node.getChildren()).forEach(this::addNode);
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
