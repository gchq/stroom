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

package stroom.gwt.workbench.client.app.vis;

import stroom.gwt.workbench.client.StroomThemeDecorator;
import stroom.gwt.workbench.framework.client.play.EventInit;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/// The stories of the content pack's networks drawn on a canvas, `Force 2D` and `Force 3D`, the same
/// stories for each: the emails between teams ([NetworkVisStories#EMAILS]) as a network. The canvas
/// draws the network, which a play can't look into, so the plays work with the clear points over its
/// nodes and lines (which the keyboard and screen readers use), and the mouse is aimed at where they
/// are. See [VisStories].
public final class CanvasNetworkStories {

    private static final Variant FORCE_2D = new Variant("Force 2D", "Force2D", "2D network", "Circle",
            "Shift and the arrow keys move it");
    private static final Variant FORCE_3D = new Variant("Force 3D", "Force3D", "3D network", "Sphere",
            "Shift and the arrow keys turn it");
    // The points over the nodes, in the middle of the lines, and the layer they are in
    private static final String NODE = "circle.vis-network__node";
    private static final String HANDLE = "circle.vis-network__handle";
    private static final String OVERLAY = "svg.vis-network__overlay";
    private static final String TEAMS = " of Emails between From and To: 3 nodes, 3 of them groups (3 collapsed), "
                                        + "3 links";
    private static final String OPS_TO_DEV = "Dev (group) and Ops (group), Ops (group) to Dev (group): 8, Click or "
                                             + "Enter: shows the members it joins";

    private CanvasNetworkStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        add(registry, FORCE_2D);
        add(registry, FORCE_3D);
    }

    // The stories of one of the networks
    private static void add(final StoryRegistry registry, final Variant network) {
        registry.component(VisStories.TITLE + network.title(), CanvasNetworkStories.class)
                .layout(StoryLayout.FULLSCREEN)
                .tags(VisStories.TAG)
                // What Stroom is told of a selected node (the Selection column of the design), as
                // Force tells it: Ctrl+Click tells it of the node, and again of nothing
                .story("SelectionPayload", VisStories.story(network.name(), settings(), NetworkVisStories.EMAILS))
                .withPlay(play -> {
                    VisStories.drawn(play, NODE);
                    final Query point = play.withinFrame(VisStories.frame(play)).querySelectorAll(NODE).first();
                    VisStories.expectSelectionOf(play, point,
                            "[{\"key\":\"Group: Ops\",\"name\":\"Ops\",\"group\":\"Ops\"}]");
                    VisStories.expectNoProblems(play);
                })
                // A node for each team, linked by their members' emails (the groups collapsed by
                // default), each with its name, drawn on the canvas
                .story("Teams", VisStories.story(network.name(), settings(), NetworkVisStories.EMAILS))
                .withPlay(play -> {
                    VisStories.drawn(play, NODE, 3);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.expect(inFrame.querySelectorAll(HANDLE)).toHaveLength(3);
                    play.waitFor(() -> play.expect(inFrame.querySelector(OVERLAY))
                            .toHaveAttribute("aria-label", network.kind() + TEAMS));
                    play.expect(inFrame.getByRole("img", NetworkVisStories.OPS)).toBeVisible();
                    play.expect(inFrame.querySelector("canvas.vis-network__canvas")).toBeVisible();
                    play.expect(inFrame.querySelector(".vis-empty"))
                            .not().toHaveTextContent(TextMatch.containing("can't draw"));
                    final Query labels = inFrame.querySelectorAll("text.vis-network__label");
                    play.expect(labels).toHaveLength(3);
                    if (StroomThemeDecorator.isForcedColours()) {
                        // With a high contrast theme, the names are in its text colour
                        final Query vis = inFrame.querySelector(".vis");
                        play.expect("the text's colour and a name's",
                                        () -> List.of(vis.computedStyle("color").get(),
                                                labels.first().computedStyle("fill").get()))
                                .toSatisfy("the same", colours -> new HashSet<>((List<?>) colours).size() == 1);
                    }
                    VisStories.expectNoProblems(play);
                })
                // Clicking a team (where the canvas draws it) shows its members, joined to it, and
                // clicking it again hides them
                .story("ExpandGroup", VisStories.story(network.name(), settings(), NetworkVisStories.EMAILS))
                .withPlay(play -> {
                    final Query nodes = VisStories.drawn(play, NODE, 3);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    final Query overlay = inFrame.querySelector(OVERLAY);
                    play.fireEvent().click(overlay,
                            EventInit.create().atCentreOf(inFrame.getByRole("img", NetworkVisStories.OPS)));
                    play.waitFor(() -> play.expect(nodes).toHaveLength(5));
                    play.waitFor(() -> play.expect(overlay.attribute("aria-label")).toMatch("(2 collapsed)"));
                    play.fireEvent().click(overlay,
                            EventInit.create().atCentreOf(inFrame.querySelector(".vis-network__node--expanded")));
                    play.waitFor(() -> play.expect(nodes).toHaveLength(3));
                    VisStories.expectNoProblems(play);
                })
                // Every team expanded: a node for each person, coloured by team, named beside it
                .story("People", VisStories.story(network.name(),
                        settings("expandGroups", "true"), NetworkVisStories.EMAILS))
                .withPlay(play -> {
                    VisStories.drawn(play, NODE, 9);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.expect(inFrame.querySelectorAll(HANDLE)).toHaveLength(7);
                    play.expect(inFrame.getByRole("img", NetworkVisStories.ALICE)).toBeVisible();
                    play.expect(inFrame.querySelectorAll("text.vis-network__label")).toHaveLength(9);
                    VisStories.expectNoProblems(play);
                })
                // A round Node Shape (Force 3D's Sphere, Force 2D's Circle) draws every node round
                // (rather than by what it does), and an expanded team as a hollow globe
                .story("RoundNodes", VisStories.story(network.name(),
                        settings("nodeShape", network.roundShape()), NetworkVisStories.EMAILS))
                .withPlay(play -> {
                    final Query nodes = VisStories.drawn(play, NODE, 3);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.expect(inFrame.querySelectorAll(NODE + "[data-shape='target']")).toHaveLength(3);
                    play.fireEvent().click(inFrame.querySelector(OVERLAY),
                            EventInit.create().atCentreOf(inFrame.getByRole("img", NetworkVisStories.OPS)));
                    play.waitFor(() -> play.expect(nodes).toHaveLength(5));
                    play.expect(inFrame.querySelector(".vis-network__node--expanded"))
                            .toHaveAttribute("data-shape", "globe");
                    play.expect(inFrame.querySelectorAll(NODE + "[data-shape='target']")).toHaveLength(4);
                    VisStories.expectNoProblems(play);
                })
                // Everything simple: balls of one plain colour, lines all one width without arrowheads
                // or flow, and no names beside the nodes (they are still in the nodes' names)
                .story("Simple", VisStories.story(network.name(),
                        settings("nodeShape", network.roundShape(), "shading", "Flat",
                        "lineWidth", "Same", "showArrows", "false", "showNames", "None", "flow", "false"),
                        NetworkVisStories.EMAILS))
                .withPlay(play -> {
                    VisStories.drawn(play, NODE, 3);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.expect(inFrame.querySelectorAll("text.vis-network__label")).toHaveLength(0);
                    play.expect(inFrame.getByRole("img", NetworkVisStories.OPS)).toBeVisible();
                    play.expect(play.screen().queryByRole("button", "Pause the flow")).toBeNull();
                    VisStories.expectNoProblems(play);
                })
                // With Show Names: Auto, a network of more than 60 nodes has no names beside them (too
                // many to read)...
                .story("ManyNodes", VisStories.story(network.name(), chainSettings(), chain(70)))
                .withPlay(play -> {
                    VisStories.drawn(play, NODE, 70);
                    play.expect(play.withinFrame(VisStories.frame(play)).querySelectorAll("text.vis-network__label"))
                            .toHaveLength(0);
                    VisStories.expectNoProblems(play);
                })
                // A big network, a thousand nodes, drawn whole (the canvas is quick); each node still
                // has its point for the keyboard
                .story("BigNetwork", VisStories.story(network.name(), chainSettings("maxNodes", "1000"), chain(1000)))
                .withPlay(play -> {
                    VisStories.drawn(play, NODE, 1000);
                    VisStories.expectNoProblems(play);
                })
                // ...and with Show Names: All, it has them all
                .story("AllNames", VisStories.story(network.name(), chainSettings("showNames", "All"), chain(70)))
                .withPlay(play -> {
                    VisStories.drawn(play, NODE, 70);
                    play.expect(play.withinFrame(VisStories.frame(play)).querySelectorAll("text.vis-network__label"))
                            .toHaveLength(70);
                    VisStories.expectNoProblems(play);
                })
                // With the mouse over alice (where the canvas draws her), her tooltip shows, and the people
                // not joined to her fade
                .story("Hover", VisStories.story(network.name(),
                        settings("expandGroups", "true"), NetworkVisStories.EMAILS))
                .withPlay(play -> {
                    VisStories.drawn(play, NODE, 9);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.fireEvent().pointerMove(inFrame.querySelector(OVERLAY),
                            EventInit.create().atCentreOf(inFrame.getByRole("img", NetworkVisStories.ALICE)));
                    final Query tooltip = VisStories.tooltip(play);
                    play.waitFor(() -> play.expect(tooltip).toBeVisible());
                    play.expect(tooltip).toHaveTextContent(TextMatch.containing("To bob"));
                    play.expect(inFrame.getByRole("img", TextMatch.startingWith("frank,")))
                            .toHaveClass("vis-network__node--faded");
                    play.expect(inFrame.getByRole("img", TextMatch.startingWith("bob,")))
                            .not().toHaveClass("vis-network__node--faded");
                    VisStories.expectNoProblems(play);
                })
                // The nodes are one tab stop, a team at a time: Down moves to the next team, Enter on a
                // team expands it (the focus staying on it), Right moves to its first member, and
                // Enter on a person selects them
                .story("Keyboard", VisStories.story(network.name(), settings(), NetworkVisStories.EMAILS))
                .withPlay(play -> {
                    final Query nodes = VisStories.drawn(play, NODE, 3);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    VisStories.focusTabStop(play);
                    play.waitFor(() -> play.expect(inFrame.getByRole("img", NetworkVisStories.OPS)).toHaveFocus());
                    VisStories.press(play, "{ArrowDown}");
                    play.waitFor(() -> play.expect(VisStories.focusedPoint(play).attribute("aria-label"))
                            .toMatch("Dev (group), Members: 2, hidden"));
                    VisStories.press(play, "{Enter}");
                    play.waitFor(() -> play.expect(nodes).toHaveLength(5));
                    play.waitFor(() -> play.expect(VisStories.focusedPoint(play).attribute("aria-label"))
                            .toMatch("Dev (group), Members: 2, shown"));
                    VisStories.press(play, "{ArrowRight}");
                    play.waitFor(() -> play.expect(VisStories.focusedPoint(play).attribute("aria-label"))
                            .toMatch("carol, From Team: Dev"));
                    VisStories.press(play, "{Enter}");
                    play.waitFor(() -> play.expect(VisStories.focusedPoint(play))
                            .toHaveClass("vis-network__node--selected"));
                    VisStories.expectNoProblems(play);
                })
                // From the keyboard, Shift and the arrow keys turn the network, + zooms in and 0 goes
                // back to the start, and a screen reader is told so
                .story("ViewKeys", VisStories.story(network.name(), settings(), NetworkVisStories.EMAILS))
                .withPlay(play -> {
                    final Query nodes = VisStories.drawn(play, NODE, 3);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.expect(inFrame.querySelector(OVERLAY).attribute("aria-description"))
                            .toMatch(network.keysWords());
                    final Query ops = inFrame.getByRole("img", NetworkVisStories.OPS);
                    VisStories.focusTabStop(play);
                    final List<double[]> start = new ArrayList<>();
                    final double[] size = new double[1];
                    play.run("note where the nodes are, and how big Ops is", () -> {
                        start.addAll(places(nodes));
                        size[0] = number(ops, "r");
                    });
                    // Three turns of 15 degrees
                    VisStories.press(play, "{Shift>}{ArrowRight}{ArrowRight}{ArrowRight}{/Shift}");
                    play.waitFor(() -> play.expect("how far the nodes moved, at most",
                            () -> furthestMoved(start, nodes)).toBeGreaterThan(5));
                    VisStories.press(play, "0");
                    play.waitFor(() -> play.expect("how far the nodes are from where they started, at most",
                            () -> furthestMoved(start, nodes)).toBeLessThan(0.5));
                    VisStories.press(play, "+");
                    play.waitFor(() -> play.expect("how big Ops is after +, over how big it was",
                            () -> number(ops, "r") / size[0]).toBeGreaterThan(1.15));
                    VisStories.expectNoProblems(play);
                })
                // Dragging turns the network, and the click at the end of the drag does nothing (it
                // doesn't expand the team it ends on)
                .story("Drag", VisStories.story(network.name(), settings(), NetworkVisStories.EMAILS))
                .withPlay(play -> {
                    final Query nodes = VisStories.drawn(play, NODE, 3);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    final Query overlay = inFrame.querySelector(OVERLAY);
                    final Query ops = inFrame.getByRole("img", NetworkVisStories.OPS);
                    final double[] start = new double[1];
                    play.run("note where Ops is", () -> start[0] = number(ops, "cx"));
                    play.fireEvent().pointerDown(overlay, EventInit.create().relativeTo(overlay, 200, 200));
                    play.fireEvent().pointerMove(overlay, EventInit.create().relativeTo(overlay, 260, 200));
                    play.fireEvent().pointerUp(overlay, EventInit.create().relativeTo(overlay, 260, 200));
                    play.waitFor(() -> play.expect("how far Ops moved across",
                            () -> Math.abs(number(ops, "cx") - start[0])).toBeGreaterThan(5));
                    play.fireEvent().click(overlay, EventInit.create().atCentreOf(ops));
                    play.expect(nodes).toHaveLength(3);
                    VisStories.expectNoProblems(play);
                })
                // The Find nodes field, from Stroom's toolbar, as Force's: what is typed rings the nodes
                // it matches (a collapsed team with a matching member) and fades the rest, and Enter
                // moves to the first
                .story("Search", VisStories.story(network.name(), settings(), NetworkVisStories.EMAILS))
                .withPlay(play -> {
                    VisStories.drawn(play, NODE, 3);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.click(play.screen().getByRole("button", "Find nodes"));
                    final Query field = inFrame.getByRole("searchbox", "Find nodes");
                    play.waitFor(() -> play.expect(field).toHaveFocus());
                    play.type(field, "car");
                    play.waitFor(() -> play.expect(inFrame.querySelector(".vis-search__count"))
                            .toHaveTextContent("1 node matches"));
                    final Query dev = inFrame.querySelector(".vis-network__node--match");
                    play.expect(inFrame.querySelectorAll(".vis-network__node--match")).toHaveLength(1);
                    play.expect(inFrame.querySelectorAll(".vis-network__node--faded")).toHaveLength(2);
                    VisStories.press(play, "{Enter}");
                    play.waitFor(() -> play.expect(dev).toHaveFocus());
                    play.expect(dev.attribute("aria-label")).toMatch("Dev (group)");
                    VisStories.expectNoProblems(play);
                })
                // Stroom's toolbar pauses and plays the flow along the lines (WCAG 2.2.2), and zooms in
                // and back to the start
                .story("Toolbar", VisStories.story(network.name(),
                        settings("expandGroups", "true"), NetworkVisStories.EMAILS))
                .withPlay(play -> {
                    VisStories.drawn(play, NODE, 9);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.click(play.screen().getByRole("button", "Pause the flow"));
                    play.waitFor(() -> play.expect(play.screen().getByRole("button", "Play the flow")).toBeVisible());
                    final Query alice = inFrame.getByRole("img", NetworkVisStories.ALICE);
                    final double[] start = new double[1];
                    play.run("note how big alice is", () -> start[0] = number(alice, "r"));
                    play.click(play.screen().getByRole("button", "Zoom in"));
                    play.waitFor(() -> play.expect("how big alice is after zooming in, over how big she was",
                            () -> number(alice, "r") / start[0]).toBeGreaterThan(1.15));
                    play.click(play.screen().getByRole("button", "Reset the view"));
                    play.waitFor(() -> play.expect("how big alice is after the reset, less how big she was",
                            () -> Math.abs(number(alice, "r") - start[0])).toBeLessThan(0.1));
                    VisStories.expectNoProblems(play);
                })
                // Colour By: Activity colours each team by how many emails it sent, got and kept, the
                // legend the key of the colours
                .story("ColourByActivity", VisStories.story(network.name(), settings("colourBy", "Activity"),
                        NetworkVisStories.EMAILS))
                .withPlay(play -> {
                    VisStories.drawn(play, NODE, 3);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.expect(inFrame.querySelectorAll(".vis-legend__item").first())
                            .toHaveTextContent(TextMatch.containing("Emails"));
                    play.expect(inFrame.querySelectorAll(".vis-legend__item").first())
                            .toHaveTextContent(TextMatch.containing(" to "));
                    VisStories.expectNoProblems(play);
                })
                // Each line has a point in its middle, after the nodes for the keyboard, its tooltip the
                // counts each way. Clicking the one between two collapsed teams shows the people it
                // joins, and Enter on theirs goes back
                .story("ExpandLine", VisStories.story(network.name(), settings(), NetworkVisStories.EMAILS))
                .withPlay(play -> {
                    final Query nodes = VisStories.drawn(play, NODE, 3);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.fireEvent().click(inFrame.querySelector(OVERLAY),
                            EventInit.create().atCentreOf(inFrame.getByRole("img", OPS_TO_DEV)));
                    play.waitFor(() -> play.expect(nodes).toHaveLength(6));
                    final Query aliceCarol = inFrame.getByRole("img", "alice and carol, alice to carol: 5, Click or "
                                                                      + "Enter: shows their groups' line again");
                    play.run("focus alice and carol's line", () -> aliceCarol.element().get().focus());
                    VisStories.press(play, "{Enter}");
                    play.waitFor(() -> play.expect(nodes).toHaveLength(3));
                    VisStories.expectNoProblems(play);
                })
                // Rows without a source or target, or with a count that isn't above zero, are left out
                .story("AwkwardData", VisStories.story(network.name(),
                        VisStories.settings("source", "From", "target", "To", "count", "Emails"),
                        VisTable.builder().text("From").text("To").number("Emails")
                                .row("alice", "bob", 1)
                                .row("alice", "carol", 0)
                                .row(null, "dave", 2)
                                .row("erin", "", 3)
                                .build()))
                .withPlay(play -> {
                    VisStories.drawn(play, NODE, 2);
                    play.waitFor(() -> play.expect(play.withinFrame(VisStories.frame(play)).querySelector(OVERLAY))
                            .toHaveAttribute("aria-label",
                                    network.kind() + " of Emails between From and To: 2 nodes, 1 link"));
                    VisStories.expectNoProblems(play);
                })
                // One email: two people and a line between them
                .story("OneValue", VisStories.story(network.name(),
                        VisStories.settings("source", "From", "target", "To", "count", "Emails"),
                        VisTable.builder().text("From").text("To").number("Emails").row("alice", "bob", 1).build()))
                .withPlay(play -> {
                    VisStories.drawn(play, NODE, 2);
                    play.expect(play.withinFrame(VisStories.frame(play)).querySelectorAll(HANDLE)).toHaveLength(1);
                    VisStories.expectNoProblems(play);
                })
                // A search that found nothing
                .story("NoData", VisStories.story(network.name(),
                        VisStories.settings("source", "From", "target", "To", "count", "Emails"),
                        VisTable.builder().text("From").text("To").number("Emails").build()))
                .withPlay(play -> {
                    final Query overlay = VisStories.drawn(play, OVERLAY, 1).first();
                    play.waitFor(() -> play.expect(overlay).toHaveAttribute("aria-label",
                            network.kind() + " of Emails between From and To: no data"));
                    play.expect(play.withinFrame(VisStories.frame(play)).querySelector(".vis-empty"))
                            .toHaveTextContent("No data");
                    VisStories.expectNoProblems(play);
                });
    }

    // Where each point is, as [cx, cy]
    private static List<double[]> places(final Query points) {
        final List<double[]> places = new ArrayList<>();
        for (int index = 0; index < points.count().get(); index++) {
            final Query point = points.nth(index);
            places.add(new double[]{number(point, "cx"), number(point, "cy")});
        }
        return places;
    }

    // How far the point that moved furthest from where it was has moved
    private static double furthestMoved(final List<double[]> from, final Query points) {
        final List<double[]> now = places(points);
        double furthest = 0;
        for (int index = 0; index < Math.min(from.size(), now.size()); index++) {
            furthest = Math.max(furthest, Math.hypot(now.get(index)[0] - from.get(index)[0],
                    now.get(index)[1] - from.get(index)[1]));
        }
        return furthest;
    }

    // A number attribute of a point, e.g. its cx
    private static double number(final Query point, final String attribute) {
        return Double.parseDouble(point.attribute(attribute).get());
    }

    // A chain of hosts, each sending to the next: `count` hosts and a line between each two
    private static VisTable chain(final int count) {
        final VisTable.Builder builder = VisTable.builder().text("From").text("To");
        for (int index = 1; index < count; index++) {
            builder.row("host-" + index, "host-" + (index + 1));
        }
        return builder.build();
    }

    // The Force 3D settings of a chain of hosts, and any more
    private static Map<String, String> chainSettings(final String... more) {
        final Map<String, String> settings = VisStories.settings("source", "From", "target", "To");
        settings.putAll(VisStories.settings(more));
        return settings;
    }

    // The Force 3D settings of the emails between teams, and any more
    private static Map<String, String> settings(final String... more) {
        final Map<String, String> settings = VisStories.settings("source", "From", "target", "To",
                "sourceGroup", "From Team", "targetGroup", "To Team", "count", "Emails");
        settings.putAll(VisStories.settings(more));
        return settings;
    }

    // One of the networks: its name, its stories' title, what its summary calls it, its round Node
    // Shape's name and what a screen reader is told of Shift and the arrow keys
    private record Variant(String name, String title, String kind, String roundShape, String keysWords) {

    }
}
