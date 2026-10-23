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

import stroom.config.global.client.presenter.ListDataProvider;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.HasContextMenusCell;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.docref.DocRef;
import stroom.pathways.shared.otel.trace.NanoTime;
import stroom.pathways.shared.pathway.PathNode;
import stroom.pathways.shared.pathway.PathStep;
import stroom.pathways.shared.pathway.PathUse;
import stroom.pathways.shared.pathway.Paths;
import stroom.pathways.shared.pathway.Pathway;
import stroom.preferences.client.DateTimeFormatter;
import stroom.svg.shared.SvgImage;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.InlineSvgToggleButton;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.ColumnSortList;
import com.google.gwt.user.cellview.client.ColumnSortList.ColumnSortInfo;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * The distinct paths traces have taken through one pathway, busiest first.
 *
 * <p>A path is the whole walk — every node the trace reached, in sequence, and which children each
 * one ran. The model shows what the pathway can do; this shows what it actually does, and how often.
 *
 * <p>Busiest first, and the trace count is the last column: what a reader follows across a row is
 * the path itself, and the count is what they land on. A path carrying one or two traces against
 * another's hundreds is usually a first-run lookup or a connection-pool event rather than something
 * the job does, which matters because locking down everything observed would bless them all for ever.
 */
public class PathwayPathListPresenter extends MyPresenterWidget<PagerView> {

    private static final int COUNT_COL = 90;
    private static final int PATH_COL = 700;
    // A trace id is 16 bytes written as hex, so this holds one rather than being measured.
    private static final int TRACE_ID_COL = 270;
    // What the button says when there is a filter to take off. It says nothing else, because it is
    // only there when there is something to undo — the filtering itself is asked for on the drawing,
    // over the node it is about.
    private static final String FILTER_ON_TITLE = "Show every path";
    private static final String FILTER_OFF_TITLE = "Right click a node on the drawing to filter";

    /**
     * Which paths the table is showing: all of them, only those that ran the node picked out on the
     * drawing, or only those that did not. The last is how a reader asks what else a pathway does —
     * the paths that avoid a node are the ones its presence hides.
     */
    public enum Filter {
        OFF,
        RUNNING_NODE,
        AVOIDING_NODE
    }

    // What separates one step from the next on show. A reading choice rather than anything the stored
    // path depends on, which holds its steps as a tree.
    private static final String STEP_SEPARATOR = " > ";
    // What separates one run from the next where several happened at the same time. Different from the
    // step separator because what it joins did not follow on from what came before it.
    private static final String RUN_SEPARATOR = " | ";
    // What separates turns of one node from each other. Different from the step separator because
    // they did not follow on from one another: turns of a node are read in an order settled by what
    // they are rather than by when they happened, whether they were taken by one thread or shared
    // between several.
    private static final String TURN_SEPARATOR = ", ";

    private final DateTimeFormatter dateTimeFormatter;
    private final TraceNavigation traceNavigation;
    private final InlineSvgToggleButton filterButton;
    private final MyDataGrid<PathUse> dataGrid;
    private final MultiSelectionModelImpl<PathUse> selectionModel;
    private final ListDataProvider<PathUse> dataProvider;

    /**
     * The nodes paths name, by the position they name them at, and the path of each. Rebuilt with
     * the data because a path is positions and nothing else; without the model beside it a row
     * cannot say which node it visited.
     */
    private final Map<Integer, PathNode> nodesByPosition = new HashMap<>();
    // What each node is called in the Path column. The schema a database span carries, and the class
    // a code span carries, are the same down most of a path, so dropping them fits far more of the
    // path in the column. A name is only shortened where nothing else in the pathway shortens to the
    // same thing, so two nodes can never come to read alike.
    private final Map<Integer, String> shortNames = new HashMap<>();

    /**
     * Where a path's walk starts. Held with the nodes above for the same reason: a path says which
     * steps were taken, and only the model says what taking them reaches.
     */
    private PathNode root;

    /**
     * Every shape the paths are built from, by the position they name it at. Held with the nodes
     * above and for the same reason: a path is positions, and only the pathway beside it says what
     * they reach.
     */
    private List<PathStep> shapes = Collections.emptyList();

    // Every path of the pathway on show, busiest first, which is not always what the table is given.
    private List<PathUse> rows = Collections.emptyList();
    // The node picked out on the drawing, which only matters while the filter is on.
    // The node picked out on the drawing, which is the node the filter is about. One notion of "the
    // node in question" rather than two: what is being filtered by is what is selected, and the
    // drawing already shows that plainly.
    private String selectedNode;
    private Filter filter = Filter.OFF;
    // Where the traces that taught this model are kept, or null where nothing keeps them.
    private List<String> sources = List.of();
    // How to order the rows for each column that offers it, and the one ordered on where nothing has
    // been chosen.
    private final Map<Column<?, ?>, Comparator<PathUse>> orders = new HashMap<>();
    private Column<PathUse, String> tracesColumn;

    /**
     * What each path comes to when it is walked, worked out the first time it is wanted and kept.
     * A walk is a walk of the whole model, and the same answers are asked for over and over — the
     * grid asks for a path's text on every draw of every visible row, and ordering on it asks twice
     * for each pair it puts in order. Emptied with the data, which is the only thing that changes it.
     */
    private final Map<PathUse, String> texts = new HashMap<>();
    private final Map<PathUse, List<List<PathNode>>> walks = new HashMap<>();
    // Which run of work happening at the same time each moment belongs to, numbered as the runs are
    // reached throughout the path, and zero for a moment that was not part of one. Held beside the
    // walk rather than in it because only the drawing wants it.
    private final Map<PathUse, List<Integer>> walkRuns = new HashMap<>();
    // Where each run of work that happened at the same time begins: the moment it starts, against the
    // run it is. Held per path, and only for paths whose walk has been worked out.
    private final Map<PathUse, List<int[]>> walkRunStarts = new HashMap<>();
    private final Map<PathUse, List<Integer>> walkHolds = new HashMap<>();
    private final Map<PathUse, Integer> stepCounts = new HashMap<>();

    @Inject
    public PathwayPathListPresenter(final EventBus eventBus,
                                     final PagerView view,
                                     final DateTimeFormatter dateTimeFormatter,
                                     final TraceNavigation traceNavigation) {
        super(eventBus, view);
        this.dateTimeFormatter = dateTimeFormatter;
        this.traceNavigation = traceNavigation;

        // Narrows the table to the paths that ran the node being looked at. On this toolbar rather
        // than on the drawing because it is this table it changes, and the node it works from is
        // picked out on the drawing either way.
        filterButton = new InlineSvgToggleButton();
        filterButton.setSvg(SvgImage.FILTER);
        filterButton.setTitle(FILTER_OFF_TITLE);
        // Off and out of reach until there is a filter on. Narrowing is asked for from the drawing,
        // where the node being narrowed to is the one under the pointer; all this does is undo it,
        // which is nothing to offer while nothing has been done.
        filterButton.setEnabled(false);
        view.addButton(filterButton);

        dataGrid = new MyDataGrid<>(this);
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        view.setDataWidget(dataGrid);

        // Held here rather than fetched a page at a time. The paths arrive on the pathway the view
        // around this one already has, so asking the server again would only risk the two disagreeing.
        dataProvider = new ListDataProvider<>();
        dataProvider.addDataDisplay(dataGrid);

        addColumns();
    }

    @Override
    protected void onBind() {
        super.onBind();
        // Follows the button rather than deciding which clicks count, the same as the drawing's own
        // view button: it turns itself over on any click it accepts.
        registerHandler(dataGrid.addColumnSortHandler(e -> order()));
        registerHandler(filterButton.addClickHandler(e -> {
            // Only reachable while something is filtered, so the only thing it can mean is stop.
            setFilter(Filter.OFF);
        }));
    }

    /**
     * The node picked out on the drawing, which is what the filter works from.
     *
     * <p>Moving to another node takes the filter off rather than quietly aiming it somewhere else. A
     * filter that re-aimed itself would change what the table was showing while the reader was reading
     * it, and leave them no way of telling which node it had settled on.
     */
    public void setSelectedNode(final PathNode node) {
        final String uuid = NullSafe.get(node, PathNode::getUuid);
        if (!Objects.equals(uuid, selectedNode)) {
            selectedNode = uuid;
            if (filter != Filter.OFF) {
                setFilter(Filter.OFF);
            }
        }
    }

    /**
     * The path being looked at. Held onto by the view around this one so it can pick the nodes out
     * on the drawing.
     */
    public MultiSelectionModelImpl<PathUse> getSelectionModel() {
        return selectionModel;
    }

    /**
     * Which nodes the selected path ran, each named by its own path down from the root, so they can be
     * picked out on the tree and the graph. Empty where nothing is selected.
     */
    public List<List<List<String>>> getSelectedNodePaths() {
        return nodePathsIn(selectionModel.getSelected());
    }

    /**
     * Which run of work happening at the same time each moment of the selected path belongs to,
     * numbered as the runs are reached throughout the path, and zero where the moment was not part of
     * one. One entry per moment, so it reads alongside {@link #getSelectedNodePaths()}.
     *
     * <p>Runs are walked one after another rather than together, so a reader can follow one before the
     * next begins. This is what says they were not a sequence.
     */
    public List<Integer> getSelectedRuns() {
        final PathUse path = selectionModel.getSelected();
        if (path == null || root == null) {
            return Collections.emptyList();
        }
        momentsIn(path);
        return NullSafe.list(walkRuns.get(path));
    }

    /**
     * When the line lit at each moment of the walk should go out, by moment. A moment inside a block
     * of work that happened at the same time holds until the whole block is done, so the block fills
     * in strand by strand and then clears in one, which is what says where it began and ended. Any
     * other moment says -1, meaning go out as soon as the next one lights.
     */
    public List<Integer> getSelectedHolds() {
        final PathUse path = selectionModel.getSelected();
        if (path == null || root == null) {
            return Collections.emptyList();
        }
        momentsIn(path);
        return NullSafe.list(walkHolds.get(path));
    }

    /**
     * Where each run of work that happened at the same time begins, as the node the walk arrives at
     * first in that run, against which run it is and the moment it starts. By node uuid, because the
     * line into a node is how the drawing finds the place to say it.
     *
     * <p>A node can open more than one run — two runs that began by doing the same thing — so a uuid
     * can carry several, each at its own moment.
     */
    public Map<String, List<int[]>> getSelectedRunStarts() {
        final PathUse path = selectionModel.getSelected();
        if (path == null || root == null) {
            return Collections.emptyMap();
        }
        final List<List<PathNode>> moments = momentsIn(path);
        final Map<String, List<int[]>> starts = new LinkedHashMap<>();
        for (final int[] start : NullSafe.list(walkRunStarts.get(path))) {
            final int moment = start[0];
            final int run = start[1];
            final int of = start[2];
            if (moment < 0 || moment >= moments.size() || moments.get(moment).isEmpty()) {
                continue;
            }
            starts.computeIfAbsent(moments.get(moment).get(0).getUuid(), k -> new ArrayList<>())
                    .add(new int[]{run, moment, of});
        }
        return starts;
    }

    // The nodes the drawing lights, each named by its path down from the root, gathered by the moment
    // they light at. Everything in one entry lights at once, so work that happened at the same time is
    // shown happening at the same time.
    private List<List<List<String>>> nodePathsIn(final PathUse path) {
        final List<List<List<String>>> moments = new ArrayList<>();
        for (final List<PathNode> nodes : momentsIn(path)) {
            final List<List<String>> paths = new ArrayList<>(nodes.size());
            for (final PathNode node : nodes) {
                if (node.getNodePath() != null) {
                    paths.add(node.getNodePath());
                }
            }
            moments.add(paths);
        }
        return moments;
    }

    // How many moments the drawing steps through, which is what the Steps column says. Not the same as
    // how many nodes ran: work repeated is held as one step however many times it went round, and the
    // runs of a moment are stepped through one after another, so each of them adds its own.
    private int steps(final PathUse path) {
        Integer count = stepCounts.get(path);
        if (count == null) {
            count = momentsIn(path).size();
            stepCounts.put(path, count);
        }
        return count;
    }

    // Whether this path ran the given node at all.
    private boolean runs(final PathUse path, final String uuid) {
        for (final List<PathNode> moment : momentsIn(path)) {
            for (final PathNode node : moment) {
                if (uuid.equals(node.getUuid())) {
                    return true;
                }
            }
        }
        return false;
    }

    // What the path ran, by the moment it ran it.
    private List<List<PathNode>> momentsIn(final PathUse path) {
        if (path == null || root == null) {
            return Collections.emptyList();
        }
        List<List<PathNode>> moments = walks.get(path);
        if (moments == null) {
            moments = new ArrayList<>();
            final List<Integer> runs = new ArrayList<>();
            final List<int[]> starts = new ArrayList<>();
            final List<Integer> holds = new ArrayList<>();
            walk(path.getRoot(), moments, runs, starts, holds, 0, 0);
            walks.put(path, moments);
            walkRuns.put(path, runs);
            walkRunStarts.put(path, starts);
            walkHolds.put(path, holds);
        }
        return moments;
    }

    // Down into each child before moving on to the next, which is the order the work happened in, and
    // says how many moments the shape took so the one after it knows where to start.
    //
    // Runs that happened at the same time are walked one after another rather than together, because
    // several lines lighting at once is hard to follow. Each moment is marked with the run it belongs
    // to so the drawing can say which one is being watched; that mark is the only thing left saying
    // they did not follow on from one another. Runs are numbered as they are reached, throughout the
    // path, so the numbers only ever count up.
    private int walk(final int shape,
                     final List<List<PathNode>> moments,
                     final List<Integer> runs,
                     final List<int[]> starts,
                     final List<Integer> holds,
                     final int at,
                     final int run) {
        final PathStep step = shapeAt(shape);
        if (step == null) {
            return 0;
        }

        int used = 0;
        // Only a node is somewhere the trace reached; a shape holding runs lights nothing of its own.
        if (step.getNode() >= 0) {
            final PathNode node = nodesByPosition.get(step.getNode());
            if (node != null) {
                lightAt(moments, runs, at, node, run);
                used = 1;
            }
        }

        if (step.isConcurrent()) {
            // The strands of the block. Usually a run apiece; where the work came down to turns of one
            // node, each turn instead, because a turn is what one thread did and the run holding them
            // is all that is left of the work being shared out. Gathered before any of them is walked
            // so each can be told how many it is one of.
            final List<Integer> strands = new ArrayList<>();
            for (final Integer child : NullSafe.list(step.getSteps())) {
                final PathStep held = shapeAt(child);
                if (held != null && turnsOfOneNode(held)) {
                    strands.addAll(NullSafe.list(held.getSteps()));
                } else {
                    strands.add(child);
                }
            }

            final int from = at + used;
            int after = 0;
            for (int i = 0; i < strands.size(); i++) {
                // Counted within the block rather than across the path, because what the badge says
                // is which of these it is and how many there are. Said together, a block inside a
                // block is still plain: the count beside the number is what tells the two apart.
                starts.add(new int[]{from + after, i + 1, strands.size()});
                final int took = walk(strands.get(i), moments, runs, starts, holds, from + after, i + 1);
                // The lines of one strand held drawn until that strand is done, so it fills in line by
                // line, stands whole for a beat and then clears as the next one starts. Held a strand
                // at a time rather than over the block, because a block that never cleared would run
                // its strands into one another and there would be no telling where one ended.
                //
                // However many there are. A block that came down to a single strand is still a block,
                // and leaving that one unheld would have its lines and its number go out part way
                // through while every other block held to its end.
                //
                // Set after the strand is walked, so a block inside it has already said where its own
                // strands end and keeps those rather than taking this one's.
                holdTo(holds, from + after, from + after + took);
                after += took;
            }
            return used + after;
        }

        for (final Integer child : NullSafe.list(step.getSteps())) {
            used += walk(child, moments, runs, starts, holds, at + used, run);
        }
        return used;
    }

    // Which moment the lines lit over a stretch of the walk should go out at. Left alone where one is
    // already set, which is how a block inside a block keeps its own ending.
    private static void holdTo(final List<Integer> holds, final int from, final int to) {
        while (holds.size() < to) {
            holds.add(-1);
        }
        for (int moment = Math.max(0, from); moment < to; moment++) {
            if (holds.get(moment) < 0) {
                holds.set(moment, to);
            }
        }
    }

    private static void lightAt(final List<List<PathNode>> moments,
                                final List<Integer> runs,
                                final int at,
                                final PathNode node,
                                final int run) {
        while (moments.size() <= at) {
            moments.add(new ArrayList<>());
            runs.add(0);
        }
        // A moment is reached by one run only, because the runs are walked one after another, so the
        // first to say which it is says it for the moment.
        if (runs.get(at) == 0) {
            runs.set(at, run);
        }
        moments.get(at).add(node);
    }

    // A shape is written after the shapes it is made of, so its steps are always earlier in the list
    // than it is and following them cannot come back round. A step naming a shape that is not there
    // belongs to a pathway written by another build, and is left rather than guessed at.
    private PathStep shapeAt(final int shape) {
        return shape >= 0 && shape < shapes.size()
                ? shapes.get(shape)
                : null;
    }

    /**
     * Shows the paths of one pathway. Given rather than fetched, for the reason the data provider
     * gives above.
     */
    public void setData(final Pathway pathway) {
        // Whatever was selected belonged to the pathway being replaced, and the view around this one
        // asks what is selected to decide what to pick out.
        selectionModel.clear();
        nodesByPosition.clear();
        shortNames.clear();
        // What was worked out about the paths of the pathway being replaced says nothing about this
        // one's, and the walk they came from starts at a root that is about to change.
        texts.clear();
        walks.clear();
        walkRuns.clear();
        walkRunStarts.clear();
        stepCounts.clear();
        root = NullSafe.get(pathway, Pathway::getRoot);
        shapes = Collections.emptyList();
        // Whatever was picked out belonged to the model being replaced, so the filter starts with
        // nothing to work from rather than with a node this pathway may not have.
        selectedNode = null;
        setFilter(Filter.OFF);

        final Paths paths = NullSafe.get(pathway, Pathway::getPaths);
        if (paths == null) {
            rows = Collections.emptyList();
            show();
            return;
        }

        indexNodes(NullSafe.get(pathway, Pathway::getRoot), paths.getNodes());
        shapes = NullSafe.list(paths.getSteps());

        rows = new ArrayList<>(NullSafe.list(paths.getPaths()));
        order();
    }

    /**
     * The trace stores this pathway has learnt from, by position. A row names its store by position
     * in this list rather than carrying a uuid of its own, and a row learnt from a store that has
     * since been pointed elsewhere still names the one it came from.
     */
    public void setSources(final List<String> sources) {
        this.sources = NullSafe.list(sources);
    }

    /**
     * Narrows the table, or stops narrowing it. Asked for from the drawing as well as from the button
     * beside the table, so the button is brought into line with whatever was asked.
     */
    public void setFilter(final Filter filter) {
        this.filter = filter;
        final boolean on = filter != Filter.OFF && selectedNode != null;
        filterButton.setState(on);
        filterButton.setEnabled(on);
        filterButton.setTitle(on
                ? FILTER_ON_TITLE
                : FILTER_OFF_TITLE);
        show();
    }

    public Filter getFilter() {
        return filter;
    }

    // What the table is given: every path, only those that ran the node picked out on the drawing, or
    // only those that did not. With a filter on and nothing picked out there is nothing to narrow by,
    // so everything is shown rather than nothing.
    private void show() {
        if (filter == Filter.OFF || selectedNode == null) {
            dataProvider.setCompleteList(rows);
            return;
        }

        final boolean wanted = filter == Filter.RUNNING_NODE;
        final List<PathUse> kept = new ArrayList<>();
        for (final PathUse path : rows) {
            if (runs(path, selectedNode) == wanted) {
                kept.add(path);
            }
        }

        // A path that has just been taken off the table should not go on picking nodes out on the
        // drawing, where the reader has no row left to click to stop it.
        final PathUse selected = selectionModel.getSelected();
        if (selected != null && !kept.contains(selected)) {
            selectionModel.clear();
        }
        dataProvider.setCompleteList(kept);
    }

    // Walks the model once, keeping the nodes the paths name. A node a path references that is not
    // in the model has been removed since, and is left out rather than guessed at.
    private void indexNodes(final PathNode root, final List<String> uuids) {
        if (root == null || NullSafe.isEmptyCollection(uuids)) {
            return;
        }
        final Map<String, PathNode> byUuid = new HashMap<>();
        collect(root, byUuid);
        for (int i = 0; i < uuids.size(); i++) {
            final PathNode node = byUuid.get(uuids.get(i));
            if (node != null) {
                nodesByPosition.put(i, node);
            }
        }
        nameNodes();
    }

    // Shortens every node name that stays its own after shortening, and leaves the rest in full. One
    // name reaching the model twice is still one name, so what disqualifies a shortening is two
    // different names coming to the same thing, not the same name arriving again.
    private void nameNodes() {
        shortNames.clear();
        final Map<String, String> firstSeen = new HashMap<>();
        final Set<String> ambiguous = new HashSet<>();
        for (final PathNode node : nodesByPosition.values()) {
            final String shortened = shorten(node.getName());
            final String seen = firstSeen.putIfAbsent(shortened, node.getName());
            if (seen != null && !seen.equals(node.getName())) {
                ambiguous.add(shortened);
            }
        }
        for (final Map.Entry<Integer, PathNode> entry : nodesByPosition.entrySet()) {
            final String shortened = shorten(entry.getValue().getName());
            shortNames.put(entry.getKey(), ambiguous.contains(shortened)
                    ? entry.getValue().getName()
                    : shortened);
        }
    }

    // A database span is named for the operation and the table, a code span for the class and the
    // method. Either way what comes before the last dot is shared with the names around it and what
    // comes after it is the part that tells them apart. A name holding a quote is left alone: the dot
    // in it belongs to a value rather than to a name, and cutting there would mangle it.
    static String shorten(final String name) {
        final int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1 || name.indexOf('\'') >= 0 || name.indexOf('"') >= 0) {
            return name;
        }
        final int space = name.lastIndexOf(' ', dot);
        return space < 0
                ? name.substring(dot + 1)
                : name.substring(0, space + 1) + name.substring(dot + 1);
    }

    private static void collect(final PathNode node, final Map<String, PathNode> byUuid) {
        byUuid.put(node.getUuid(), node);
        for (final PathNode child : NullSafe.list(node.getChildren())) {
            collect(child, byUuid);
        }
    }

    private void addColumns() {
        addColumn("Created", path -> NullSafe.get(path.getFirstUsedTime(),
                        value -> dateTimeFormatter.format(value.toEpochMillis())),
                ColumnSizeConstants.DATE_COL,
                (a, b) -> compare(a.getFirstUsedTime(), b.getFirstUsedTime()));
        addColumn("Last Used", path -> NullSafe.get(path.getLastUsedTime(),
                        value -> dateTimeFormatter.format(value.toEpochMillis())),
                ColumnSizeConstants.DATE_COL,
                (a, b) -> compare(a.getLastUsedTime(), b.getLastUsedTime()));
        addColumn("Path", this::text, PATH_COL,
                (a, b) -> compare(text(a), text(b)));
        addTraceIdColumn();
        addColumn("Steps", path -> Integer.toString(steps(path)), COUNT_COL,
                (a, b) -> Integer.compare(steps(a), steps(b)));
        tracesColumn = addColumn("Traces", path -> Long.toString(path.getTimesUsed()), COUNT_COL,
                (a, b) -> Long.compare(a.getTimesUsed(), b.getTimesUsed()));
        busiestFirst();
    }

    // The trace that first took this path, which is the one worth opening to see what it actually did.
    // Right clicking it offers to go there — nothing, where no traces store feeds this pathway.
    private void addTraceIdColumn() {
        final Column<PathUse, String> column = new Column<PathUse, String>(
                new HasContextMenusCell<String>((context, traceId) ->
                        traceNavigation.getMenuItems(this, sourceOf(traceId), traceId, whenTaken(traceId))) {
                }) {
            @Override
            public String getValue(final PathUse path) {
                return path.getCreatedByTraceId();
            }
        };
        column.setSortable(true);
        dataGrid.addResizableColumn(column, "Created By", TRACE_ID_COL);
        // Held against the column rather than the name, the same as every other column here.
        orders.put(column, (a, b) -> compare(a.getCreatedByTraceId(), b.getCreatedByTraceId()));
    }

    // When the trace that first took this path actually ran. Not the first used time beside it: that
    // is when the model learnt from the trace, which is however long after the trace ran that it
    // waited in the queue to be applied.
    // Which trace store a row's trace came from, resolved against the list the pathway carries. Null
    // where the row names none, or names one this pathway no longer holds — the menu then offers
    // nothing rather than opening a store that cannot answer.
    private String sourceOf(final String traceId) {
        final int index = indexOfSource(traceId);
        return index < 0 || index >= sources.size()
                ? null
                : sources.get(index);
    }

    private int indexOfSource(final String traceId) {
        for (final PathUse path : rows) {
            if (Objects.equals(traceId, path.getCreatedByTraceId())) {
                return path.getSource();
            }
        }
        return -1;
    }

    private Long whenTaken(final String traceId) {
        for (final PathUse path : rows) {
            if (Objects.equals(traceId, path.getCreatedByTraceId())) {
                return NullSafe.get(path.getTraceTime(), NanoTime::toEpochMillis);
            }
        }
        return null;
    }

    // Busiest first unless the grid has been told otherwise. What a reader wants to know is what
    // normally happens, and after that what hardly ever does.
    private void busiestFirst() {
        final ColumnSortList sortList = dataGrid.getColumnSortList();
        sortList.clear();
        sortList.push(new ColumnSortInfo(tracesColumn, false));
    }

    // Whatever the grid has been told to order on, or the busiest first where it has been told
    // nothing it knows about.
    private void order() {
        Comparator<PathUse> order = orders.get(tracesColumn);
        boolean ascending = false;

        final ColumnSortList sortList = dataGrid.getColumnSortList();
        if (sortList != null && sortList.size() > 0) {
            final ColumnSortInfo info = sortList.get(0);
            final Comparator<PathUse> chosen = orders.get(info.getColumn());
            if (chosen != null) {
                order = chosen;
                ascending = info.isAscending();
            }
        }

        final Comparator<PathUse> chosen = order;
        final boolean up = ascending;
        final List<PathUse> sorted = new ArrayList<>(rows);
        sorted.sort((a, b) -> up
                ? chosen.compare(a, b)
                : chosen.compare(b, a));
        rows = sorted;
        show();
    }

    private static int compare(final NanoTime a, final NanoTime b) {
        if (a == null || b == null) {
            return a == b
                    ? 0
                    : (a == null
                            ? -1
                            : 1);
        }
        return a.compareTo(b);
    }

    private static int compare(final String a, final String b) {
        if (a == null || b == null) {
            return a == b
                    ? 0
                    : (a == null
                            ? -1
                            : 1);
        }
        return a.compareTo(b);
    }

    private String text(final PathUse path) {
        String text = texts.get(path);
        if (text == null) {
            text = pathText(path);
            texts.put(path, text);
        }
        return text;
    }

    // The walk in the sequence it happened, which is the sequence the drawing picks the nodes out in.
    // Starts below the root rather than at it: the root is the pathway, which is named in the dialog
    // this sits in and drawn beside the table, so every row would open with the same words.
    private String pathText(final PathUse path) {
        final PathStep step = shapeAt(path.getRoot());
        final String steps = step == null
                ? ""
                : ran(step);
        // A shape with no steps is a node that ran nothing, so a root with none is a trace where the
        // operation ran and nothing under it did. The root is then the whole of what ran, so it is
        // what the row says — the same as every other row, which says what ran.
        return steps.isEmpty()
                ? NullSafe.getOrElse(root, PathNode::getName, "")
                : steps;
    }

    // One shape as it reads: a name on its own where the node ran nothing, and the name with what it
    // ran in brackets where it ran something. Nested rather than listed node by node, because what a
    // reader follows is the work in the order it happened, and a list by node puts everything one step
    // from the root ahead of anything further out.
    private String stepText(final int shape) {
        final PathStep step = shapeAt(shape);
        if (step == null) {
            return "";
        }
        if (step.isConcurrent()) {
            // Runs that happened at the same time, held in braces and parted by the run separator.
            // Read in an order settled by what they are rather than by which thread got there first,
            // so a reader must not follow them as a sequence.
            final StringBuilder sb = new StringBuilder();
            for (final Integer run : NullSafe.list(step.getSteps())) {
                final String text = stepText(run);
                if (text.isEmpty()) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append(RUN_SEPARATOR);
                }
                sb.append(text);
            }
            return "{" + sb + "}";
        }
        final String inner = ran(step);
        if (step.isRun()) {
            return inner;
        }
        if (step.isUnfinished()) {
            // Work that ran over and over and stopped part way through the last time, held in square
            // brackets. Work that repeated and did finish is simply said once with nothing to mark
            // it, because how much there was to do is the workload rather than the path through the
            // code. Each of the three brackets means one thing and nothing else: round for what a
            // node ran, braces for work at the same time, square for a run that stopped part way.
            return "[" + inner + "]";
        }
        final String name = shortNames.get(step.getNode());
        if (name == null) {
            return "";
        }
        return inner.isEmpty()
                ? name
                : name + " (" + inner + ")";
    }

    // What one shape ran, in the order it ran it — or, where everything it ran is a turn of one
    // node, in no order at all.
    private String ran(final PathStep step) {
        final String separator = turnsOfOneNode(step)
                ? TURN_SEPARATOR
                : STEP_SEPARATOR;
        final StringBuilder sb = new StringBuilder();
        for (final Integer child : NullSafe.list(step.getSteps())) {
            final String text = stepText(child);
            if (text.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(separator);
            }
            sb.append(text);
        }
        return sb.toString();
    }

    // Whether everything this shape ran is a turn of one and the same node. The model reads a run of
    // turns of one node in an order settled by what they are, so what is shown is not the order they
    // happened in and must not be read as one.
    private boolean turnsOfOneNode(final PathStep step) {
        final List<Integer> children = NullSafe.list(step.getSteps());
        if (children.size() < 2) {
            return false;
        }
        int node = -1;
        for (final Integer child : children) {
            final PathStep shape = shapeAt(child);
            if (shape == null || shape.getNode() < 0) {
                return false;
            }
            if (node < 0) {
                node = shape.getNode();
            } else if (node != shape.getNode()) {
                return false;
            }
        }
        return true;
    }

    private Column<PathUse, String> addColumn(final String name,
                                               final Function<PathUse, String> value,
                                               final int width,
                                               final Comparator<PathUse> order) {
        final Column<PathUse, String> column = DataGridUtil
                .textColumnBuilder(value)
                .withSorting(name)
                .build();
        dataGrid.addResizableColumn(column, name, width);
        // Held against the column rather than the name, because what the grid hands back when a header
        // is clicked is the column.
        orders.put(column, order);
        return column;
    }
}
