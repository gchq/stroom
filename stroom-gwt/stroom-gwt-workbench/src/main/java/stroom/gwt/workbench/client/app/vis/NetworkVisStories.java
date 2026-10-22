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
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import java.util.HashSet;
import java.util.List;
import java.util.Map;


/// The stories of the content pack's visualisations of links and hierarchies: `Force`, `Force 3D`,
/// `ChordLabels`, `Sunburst` and `Tree`. See [VisStories].
public final class NetworkVisStories {

    // Who emailed whom, and how often, with each person's team (Force3DStories draws them too)
    static final VisTable EMAILS = VisTable.builder()
            .text("From")
            .text("To")
            .text("From Team")
            .text("To Team")
            .number("Emails")
            .row("alice", "bob", "Ops", "Ops", 12)
            .row("alice", "carol", "Ops", "Dev", 5)
            .row("bob", "carol", "Ops", "Dev", 3)
            .row("carol", "dave", "Dev", "Dev", 20)
            .row("dave", "erin", "Dev", "Security", 2)
            .row("erin", "alice", "Security", "Ops", 7)
            .row("frank", "erin", "Security", "Security", 9)
            .build();

    // The space each file takes, by its path
    private static final VisTable FILE_SIZES = VisTable.builder()
            .text("Path")
            .number("Size")
            .row("home/alice/reports/2026/summary.txt", 12)
            .row("home/alice/reports/2026/figures.xlsx", 80)
            .row("home/alice/reports/2025/summary.txt", 10)
            .row("home/alice/music/album/track1.mp3", 300)
            .row("home/alice/music/album/track2.mp3", 280)
            .row("home/bob/notes.txt", 4)
            .row("home/bob/photos/beach.jpg", 150)
            .row("var/log/nginx/access.log", 220)
            .row("var/log/nginx/error.log", 30)
            .row("etc/nginx/nginx.conf", 2)
            .build();

    // The space each file takes on each of two servers
    private static final VisTable FILE_SIZES_BY_SERVER = VisTable.builder()
            .text("Server")
            .text("Path")
            .number("Size")
            .row("files-1", "home/alice/reports/summary.txt", 12)
            .row("files-1", "home/alice/reports/figures.xlsx", 80)
            .row("files-1", "home/alice/music/track1.mp3", 300)
            .row("files-1", "var/log/access.log", 220)
            .row("files-2", "home/bob/notes.txt", 4)
            .row("files-2", "home/bob/photos/beach.jpg", 150)
            .row("files-2", "home/bob/photos/hills.jpg", 120)
            .row("files-2", "etc/nginx/nginx.conf", 2)
            .build();

    // The asset-based sunburst's arcs (and its middle)
    private static final String SUNBURST_NODE = "path.vis-sunburst__node";

    // The chord diagram's arcs (a name each), ribbons (a count from one name to another) and names
    private static final String CHORD_ARC = "path.vis-chord__arc";
    private static final String CHORD_RIBBON = "path.vis-chord__ribbon";
    private static final String CHORD_LABEL = "text.vis-chord__label";
    private static final String CAROL = "carol, Emails out: 20, Emails in: 8";
    private static final String CAROL_TO_DAVE = "carol to dave, Emails: 20";

    // The network's nodes and lines, and two of its nodes
    private static final String FORCE_NODE = "path.vis-force__node";
    private static final String FORCE_LINK = "line.vis-force__link";
    static final String OPS = "Ops (group), Members: 2, hidden, Emails out: 8, Emails in: 7, "
                                      + "Emails within: 12, To Dev (group): 8, From Security (group): 7";
    static final String ALICE = "alice, From Team: Ops, Emails out: 17, Emails in: 7, To bob: 12, "
                                        + "From erin: 7, To carol: 5";

    // The tree's boxes (and the root's), and two of its leaves
    private static final String TREE_BOX = "g.vis-tree__node";
    private static final String NOTES = "notes.txt, Path: home / bob / notes.txt, Value: 4";
    private static final String NGINX_CONF = "nginx.conf, Path: etc / nginx / nginx.conf, Value: 2";

    private NetworkVisStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        addForce(registry);

        CanvasNetworkStories.addTo(registry);

        addChordLabels(registry);

        registry.component(VisStories.TITLE + "Sunburst", NetworkVisStories.class)
                .layout(StoryLayout.FULLSCREEN)
                .tags(VisStories.TAG)
                // What Stroom is told of a selected branch (the Selection column of the design):
                // Ctrl+Click tells it of the branch, and again of nothing (the middle isn't selected)
                .story("SelectionPayload", VisStories.story("Sunburst",
                        VisStories.settings("path", "Path", "value", "Size"), FILE_SIZES))
                .withPlay(play -> {
                    VisStories.drawn(play, SUNBURST_NODE);
                    final Query point = play.withinFrame(VisStories.frame(play))
                            .querySelectorAll(SUNBURST_NODE).nth(1);
                    VisStories.expectSelectionOf(play, point,
                            "[{\"key\":\"/home\",\"value\":836,\"name\":\"home\",\"path\":\"/home\""
                            + "}]");
                    VisStories.expectNoProblems(play);
                })
                // The files' sizes by folder: each top-level folder a colour of its own, the folders
                // in it shades of it
                .story("FileSizes", VisStories.story("Sunburst",
                        VisStories.settings("path", "Path", "value", "Size"), FILE_SIZES))
                .withPlay(play -> {
                    final Query nodes = VisStories.drawn(play, SUNBURST_NODE, 25);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    final Query chart = inFrame.querySelector("svg.vis-sunburst");
                    play.waitFor(() -> play.expect(chart).toHaveAttribute("aria-label",
                            "Sunburst of Size by Path: 10 paths, total 1,088, largest branch home 836 (76.8%)"));
                    final Query home = inFrame.getByRole("img", "home, Size: 836, Share: 76.8%");
                    final Query var = inFrame.getByRole("img", "var, Size: 250, Share: 23.0%");
                    play.expect("the colours of the home and var folders",
                                    () -> List.of(home.computedStyle("fill").get(), var.computedStyle("fill").get()))
                            .toSatisfy("different", colours -> new HashSet<>((List<?>) colours).size() == 2);
                    // With var shown, home fades, and its name is then in the theme's text colour,
                    // which reads on the faded arc
                    play.hover(var);
                    final Query homeLabel = inFrame.getByText("home", "text.vis-sunburst__label");
                    play.waitFor(() -> play.expect(homeLabel).toHaveClass("vis-sunburst__label--faded"));
                    final Query vis = inFrame.querySelector(".vis");
                    play.expect("the text's colour and the faded arc's name's",
                                    () -> List.of(vis.computedStyle("color").get(),
                                            homeLabel.computedStyle("fill").get()))
                            .toSatisfy("the same", colours -> new HashSet<>((List<?>) colours).size() == 1);
                    if (StroomThemeDecorator.isForcedColours()) {
                        // With a high contrast theme, each arc is edged in its text colour
                        play.expect("the text's colour and an arc's edge's",
                                        () -> List.of(vis.computedStyle("color").get(),
                                                nodes.nth(1).computedStyle("stroke").get()))
                                .toSatisfy("the same", colours -> new HashSet<>((List<?>) colours).size() == 1);
                    }
                    VisStories.expectNoProblems(play);
                })
                // Clicking a folder zooms in to it, which is then in the middle, and clicking the
                // middle zooms back out
                .story("ZoomIn", VisStories.story("Sunburst",
                        VisStories.settings("path", "Path", "value", "Size"), FILE_SIZES))
                .withPlay(play -> {
                    final Query nodes = VisStories.drawn(play, SUNBURST_NODE, 25);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.click(inFrame.getByRole("img", "home, Size: 836, Share: 76.8%"));
                    play.waitFor(() -> play.expect(nodes).toHaveLength(16));
                    final Query chart = inFrame.querySelector("svg.vis-sunburst");
                    play.expect(chart.attribute("aria-label")).toMatch(", showing home");
                    play.click(inFrame.querySelector(".vis-sunburst__node--middle"));
                    play.waitFor(() -> play.expect(nodes).toHaveLength(25));
                    VisStories.expectNoProblems(play);
                })
                // The middle is the tab stop. Down moves out a ring and Up back in; Enter on a folder
                // zooms in to it, keeping the focus in the middle, and Enter there zooms back out
                .story("Keyboard", VisStories.story("Sunburst",
                        VisStories.settings("path", "Path", "value", "Size"), FILE_SIZES))
                .withPlay(play -> {
                    final Query nodes = VisStories.drawn(play, SUNBURST_NODE, 25);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    VisStories.focusTabStop(play);
                    play.waitFor(() -> play.expect(inFrame.getByRole("img", "All, Size: 1,088")).toHaveFocus());
                    VisStories.press(play, "{ArrowDown}");
                    play.waitFor(() -> play.expect(inFrame.getByRole("img", "home, Size: 836, Share: 76.8%"))
                            .toHaveFocus());
                    VisStories.press(play, "{Enter}");
                    play.waitFor(() -> play.expect(nodes).toHaveLength(16));
                    play.waitFor(() -> play.expect(inFrame.getByRole("img", "home, Size: 836, Share: 76.8%"))
                            .toHaveFocus());
                    VisStories.press(play, "{Enter}");
                    play.waitFor(() -> play.expect(nodes).toHaveLength(25));
                    play.waitFor(() -> play.expect(inFrame.getByRole("img", "All, Size: 1,088")).toHaveFocus());
                    // Space selects a branch rather than zooming in to it (Enter couldn't select one)
                    VisStories.press(play, "{ArrowDown} ");
                    final Query home = inFrame.getByRole("img",
                            TextMatch.startingWith("home, Size: 836, Share: 76.8%"));
                    play.waitFor(() -> play.expect(home).toHaveClass("vis-sunburst__node--selected"));
                    play.expect(nodes).toHaveLength(25);
                    VisStories.expectNoProblems(play);
                })
                // A Base Colour given by name colours every branch, shaded further out, with the names
                // black or white to read on it (a named colour broke the shading, which needs hex)
                .story("NamedBaseColour", VisStories.story("Sunburst",
                        VisStories.settings("path", "Path", "value", "Size", "baseColor", "red"), FILE_SIZES))
                .withPlay(play -> {
                    VisStories.drawn(play, SUNBURST_NODE, 25);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    final Query home = inFrame.getByRole("img", "home, Size: 836, Share: 76.8%");
                    play.expect(home.computedStyle("fill")).toBe("rgb(255, 0, 0)");
                    final Query alice = inFrame.getByRole("img", "home / alice, Size: 682, Share: 62.7%");
                    play.expect("alice's colour, a shade of red", () -> alice.computedStyle("fill").get())
                            .toSatisfy("redder than it is green or blue, and not the same red", fill -> {
                                final String[] rgb = String.valueOf(fill).replaceAll("[^0-9,]", "").split(",");
                                return rgb.length == 3 && Integer.parseInt(rgb[0]) > Integer.parseInt(rgb[1])
                                       && Integer.parseInt(rgb[0]) > Integer.parseInt(rgb[2])
                                       && !"rgb(255, 0, 0)".equals(fill);
                            });
                    final Query label = inFrame.getByText("home", "text.vis-sunburst__label");
                    play.expect("home's name's colour", () -> label.computedStyle("fill").get())
                            .toSatisfy("black or white", fill -> "rgb(0, 0, 0)".equals(fill)
                                                                 || "rgb(255, 255, 255)".equals(fill));
                    VisStories.expectNoProblems(play);
                })
                // A sunburst for each server, each named by its server
                .story("GridSeries", VisStories.story("Sunburst",
                        VisStories.settings("path", "Path", "value", "Size", "gridSeries", "Server"),
                        FILE_SIZES_BY_SERVER))
                .withPlay(play -> {
                    VisStories.drawn(play, "svg.vis-sunburst", 2);
                    final Query names = play.withinFrame(VisStories.frame(play)).querySelectorAll(".vis-grid__name");
                    play.expect("the sunbursts' names", () -> names.textContents().get())
                            .toEqual(List.of("files-1", "files-2"));
                    VisStories.expectNoProblems(play);
                })
                // One path: the middle and one ring
                .story("OneValue", VisStories.story("Sunburst",
                        VisStories.settings("path", "Path", "value", "Size"),
                        VisTable.builder().text("Path").number("Size").row("home", 5).build()))
                .withPlay(play -> {
                    VisStories.drawn(play, SUNBURST_NODE, 2);
                    VisStories.expectNoProblems(play);
                })
                // A search that found nothing: 'No data' where the rings would be
                .story("NoData", VisStories.story("Sunburst",
                        VisStories.settings("path", "Path", "value", "Size"),
                        VisTable.builder().text("Path").number("Size").build()))
                .withPlay(play -> {
                    final Query chart = VisStories.drawn(play, "svg.vis-sunburst", 1).first();
                    play.waitFor(() -> play.expect(chart).toHaveAttribute("aria-label",
                            "Sunburst of Size by Path: no data"));
                    play.expect(play.withinFrame(VisStories.frame(play)).querySelector(".vis-empty"))
                            .toHaveTextContent("No data");
                    VisStories.expectNoProblems(play);
                });

        addTree(registry);
    }

    private static void addTree(final StoryRegistry registry) {
        registry.component(VisStories.TITLE + "Tree", NetworkVisStories.class)
                .layout(StoryLayout.FULLSCREEN)
                .tags(VisStories.TAG)
                // What Stroom is told of a selected branch (the Selection column of the design):
                // Ctrl+Click tells it of the branch, and again of nothing (the root isn't selected)
                .story("SelectionPayload", VisStories.story("Tree", treeSettings(), FILE_SIZES))
                .withPlay(play -> {
                    VisStories.drawn(play, TREE_BOX);
                    final Query point = play.withinFrame(VisStories.frame(play))
                            .querySelectorAll(TREE_BOX).nth(1);
                    VisStories.expectSelectionOf(play, point,
                            "[{\"key\":\"/home\",\"path\":\"/home\",\"name\":\"home\"}]");
                    VisStories.expectNoProblems(play);
                })
                // The files by folder, three levels shown at first (13 boxes and the root), each box
                // in its top-level folder's colour with its words black or white to read on it
                .story("FileSizes", VisStories.story("Tree", treeSettings(), FILE_SIZES))
                .withPlay(play -> {
                    final Query boxes = VisStories.drawn(play, TREE_BOX, 14);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.waitFor(() -> play.expect(inFrame.querySelector("svg.vis-tree")).toHaveAttribute(
                            "aria-label", "Tree of Path: 24 paths, 5 levels deep, 13 shown"));
                    play.expect(inFrame.getByRole("img", NOTES)).toBeVisible();
                    final Query label = inFrame.getByText("home", "text.vis-tree__label");
                    play.expect(label.computedStyle("fill")).toMatch("rgb(");
                    play.expect("a label's colour", () -> label.computedStyle("fill").get())
                            .toSatisfy("black or white", fill -> "rgb(0, 0, 0)".equals(fill)
                                                                 || "rgb(255, 255, 255)".equals(fill));
                    if (StroomThemeDecorator.isForcedColours()) {
                        // With a high contrast theme, each box is edged in its text colour
                        final Query vis = inFrame.querySelector(".vis");
                        play.expect("the text's colour and a box's edge",
                                        () -> List.of(vis.computedStyle("color").get(),
                                                inFrame.querySelectorAll("rect.vis-tree__box").nth(1)
                                                        .computedStyle("stroke").get()))
                                .toSatisfy("the same", colours -> new HashSet<>((List<?>) colours).size() == 1);
                    }
                    VisStories.expectNoProblems(play);
                })
                // Clicking a hidden branch (reports) shows its children, and clicking it again hides
                // them, the tree fitted to its cell each time
                .story("ShowAndHide", VisStories.story("Tree", treeSettings(), FILE_SIZES))
                .withPlay(play -> {
                    final Query boxes = VisStories.drawn(play, TREE_BOX, 14);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    final Query reports = inFrame.getByRole("img", "reports, Path: home / alice / reports, "
                                                                   + "Children: 2, hidden");
                    play.click(reports);
                    play.waitFor(() -> play.expect(boxes).toHaveLength(16));
                    play.waitFor(() -> play.expect(inFrame.querySelector("svg.vis-tree")).toHaveAttribute(
                            "aria-label", "Tree of Path: 24 paths, 5 levels deep, 15 shown"));
                    play.click(inFrame.getByRole("img", "reports, Path: home / alice / reports, Children: 2, shown"));
                    play.waitFor(() -> play.expect(boxes).toHaveLength(14));
                    VisStories.expectNoProblems(play);
                })
                // The root is the tab stop; Down moves to the first top-level folder, and Enter on it
                // hides its children, the focus staying on it
                .story("Keyboard", VisStories.story("Tree", treeSettings(), FILE_SIZES))
                .withPlay(play -> {
                    final Query boxes = VisStories.drawn(play, TREE_BOX, 14);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    VisStories.focusTabStop(play);
                    play.waitFor(() -> play.expect(inFrame.getByRole("img", "All, Path: All, Children: 3, shown"))
                            .toHaveFocus());
                    VisStories.press(play, "{ArrowDown}");
                    final Query home = inFrame.getByRole("img", "home, Path: home, Children: 2, shown");
                    play.waitFor(() -> play.expect(home).toHaveFocus());
                    VisStories.press(play, "{Enter}");
                    play.waitFor(() -> play.expect(boxes).toHaveLength(8));
                    play.waitFor(() -> play.expect(inFrame.getByRole("img", "home, Path: home, Children: 2, hidden"))
                            .toHaveFocus());
                    VisStories.press(play, "{ArrowRight}{ArrowDown}{ArrowDown}");
                    play.waitFor(() -> play.expect(inFrame.getByRole("img", NGINX_CONF)).toHaveFocus());
                    VisStories.press(play, "{Enter}");
                    play.waitFor(() -> play.expect(inFrame.getByRole("img", TextMatch.startingWith(NGINX_CONF))
                                    .closest("g"))
                            .toHaveClass("vis-tree__node--selected"));
                    VisStories.expectNoProblems(play);
                })
                // Growing right from the root, on the left. The arrow keys follow it: Right moves out a
                // level and Down along one (they were Down and Right whichever way the tree grew), and
                // Space selects a branch without hiding its children
                .story("East", VisStories.story("Tree", treeSettings("orientation", "east"), FILE_SIZES))
                .withPlay(play -> {
                    final Query boxes = VisStories.drawn(play, TREE_BOX, 14);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    final Query root = inFrame.querySelector(".vis-tree__node--root rect");
                    final Query home = inFrame.getByRole("img", "home, Path: home, Children: 2, shown");
                    play.waitFor(() -> play.expect("the root's right less home's left",
                            () -> root.right().get() - home.left().get()).toBeLessThanOrEqual(0));
                    VisStories.focusTabStop(play);
                    VisStories.press(play, "{ArrowRight}");
                    play.waitFor(() -> play.expect(home).toHaveFocus());
                    VisStories.press(play, "{ArrowDown}");
                    final Query var = inFrame.getByRole("img",
                            TextMatch.startingWith("var, Path: var, Children: 1, shown"));
                    play.waitFor(() -> play.expect(var).toHaveFocus());
                    VisStories.press(play, " ");
                    play.waitFor(() -> play.expect(var.closest("g")).toHaveClass("vis-tree__node--selected"));
                    play.expect(boxes).toHaveLength(14);
                    VisStories.expectNoProblems(play);
                })
                // Radial (as the legacy RadialTree drew it): the root in the middle, each level a ring
                // further out, each node a dot with its words beside it, outwards, in the theme's text
                // colour; showing and hiding a branch works as it does for boxes
                .story("Radial", VisStories.story("Tree", treeSettings("orientation", "radial"), FILE_SIZES))
                .withPlay(play -> {
                    final Query nodes = VisStories.drawn(play, TREE_BOX, 14);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    final Query dots = inFrame.querySelectorAll("circle.vis-tree__dot");
                    play.expect(dots.first().computedStyle("display")).toBe("inline");
                    play.expect(inFrame.querySelector("rect.vis-tree__box").computedStyle("display")).toBe("none");
                    // The root is in the middle of the others
                    final Query root = inFrame.querySelector(".vis-tree__node--root circle");
                    final Query home = inFrame.getByRole("img", "home, Path: home, Children: 2, shown");
                    final Query etc = inFrame.getByRole("img", "etc, Path: etc, Children: 1, shown");
                    play.expect("the root's middle, between home and etc",
                            () -> (root.left().get() + root.right().get()) / 2).toBeGreaterThan(0);
                    play.expect("home and etc either side of the root", () -> {
                        final double middle = (root.left().get() + root.right().get()) / 2;
                        final double homeMiddle = (home.left().get() + home.right().get()) / 2;
                        final double etcMiddle = (etc.left().get() + etc.right().get()) / 2;
                        return (homeMiddle - middle) * (etcMiddle - middle) < 0;
                    }).toBe(true);
                    final Query vis = inFrame.querySelector(".vis");
                    final Query label = inFrame.getByText("home", "text.vis-tree__label");
                    play.expect("the text's colour and a name's",
                                    () -> List.of(vis.computedStyle("color").get(), label.computedStyle("fill").get()))
                            .toSatisfy("the same", colours -> new HashSet<>((List<?>) colours).size() == 1);
                    if (StroomThemeDecorator.isForcedColours()) {
                        play.expect("the text's colour and a dot's edge",
                                        () -> List.of(vis.computedStyle("color").get(),
                                                dots.nth(1).computedStyle("stroke").get()))
                                .toSatisfy("the same", colours -> new HashSet<>((List<?>) colours).size() == 1);
                    }
                    play.click(inFrame.getByRole("img", "reports, Path: home / alice / reports, Children: 2, hidden"));
                    play.waitFor(() -> play.expect(nodes).toHaveLength(16));
                    VisStories.expectNoProblems(play);
                })
                // + and − zoom and 0 fits it again, from the keyboard as the wheel and double click do
                .story("KeyboardZoom", VisStories.story("Tree", treeSettings(), FILE_SIZES))
                .withPlay(play -> {
                    VisStories.drawn(play, TREE_BOX, 14);
                    VisStories.expectKeyboardZoom(play, "svg.vis-tree", "svg.vis-tree > g");
                    VisStories.expectNoProblems(play);
                })
                // A tree for each server
                .story("GridSeries", VisStories.story("Tree", treeSettings("gridSeries", "Server"),
                        FILE_SIZES_BY_SERVER))
                .withPlay(play -> {
                    VisStories.drawn(play, "svg.vis-tree", 2);
                    final Query names = play.withinFrame(VisStories.frame(play)).querySelectorAll(".vis-grid__name");
                    play.expect("the trees' names", () -> names.textContents().get())
                            .toEqual(List.of("files-1", "files-2"));
                    VisStories.expectNoProblems(play);
                })
                // A big radial tree in each of two small grid cells: it is never fitted so small that its
                // words are below 10px (12px scaled by 5/6), the rest reached by panning, and a ring
                // with many nodes is pushed out until they are 16px apart, so their words don't overlap
                .story("BigTreeReadable", VisStories.story("Tree",
                        treeSettings("orientation", "radial", "gridSeries", "Server"), bigTreeByServer()))
                .withPlay(play -> {
                    VisStories.drawn(play, "svg.vis-tree", 2);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    final Query zoomed = inFrame.querySelectorAll("svg.vis-tree > g");
                    play.waitFor(() -> play.expect(inFrame.querySelectorAll(TREE_BOX)).toHaveLength(2 * 94));
                    play.expect("the smallest a tree is scaled", () -> Math.min(
                                    VisStories.scaleOf(zoomed.nth(0).attribute("transform").get()),
                                    VisStories.scaleOf(zoomed.nth(1).attribute("transform").get())))
                            .toBeGreaterThan(10.0 / 12 - 0.001);
                    final Query dots = inFrame.within(inFrame.querySelectorAll("svg.vis-tree").first())
                            .querySelectorAll("circle.vis-tree__dot");
                    play.expect("the least distance between two dots of the first tree on screen", () -> {
                        final int count = dots.count().get();
                        final double[] xs = new double[count];
                        final double[] ys = new double[count];
                        for (int i = 0; i < count; i++) {
                            xs[i] = (dots.nth(i).left().get() + dots.nth(i).right().get()) / 2;
                            ys[i] = (dots.nth(i).top().get() + dots.nth(i).bottom().get()) / 2;
                        }
                        double least = Double.MAX_VALUE;
                        for (int i = 0; i < count; i++) {
                            for (int j = i + 1; j < count; j++) {
                                least = Math.min(least, Math.hypot(xs[i] - xs[j], ys[i] - ys[j]));
                            }
                        }
                        return least;
                    }).toBeGreaterThan(16 * 10.0 / 12 - 0.5);
                    VisStories.expectNoProblems(play);
                })
                // One path: the root and one box
                .story("OneValue", VisStories.story("Tree", treeSettings(),
                        VisTable.builder().text("Path").number("Size").row("home", 5).build()))
                .withPlay(play -> {
                    VisStories.drawn(play, TREE_BOX, 2);
                    VisStories.expectNoProblems(play);
                })
                // Rows without a path are left out, empty parts skipped, and a path that also starts
                // another keeps its own value (the legacy tree lost it when the longer path came first)
                .story("AwkwardData", VisStories.story("Tree", treeSettings(),
                        VisTable.builder().text("Path").text("Size")
                                .row("/a//b", "1")
                                .row("a", "own")
                                .row(null, "2")
                                .row("", "3")
                                .build()))
                .withPlay(play -> {
                    VisStories.drawn(play, TREE_BOX, 3);
                    play.expect(play.withinFrame(VisStories.frame(play)).getByText("a: own", "text.vis-tree__label"))
                            .toBeVisible();
                    VisStories.expectNoProblems(play);
                })
                // A search that found nothing: 'No data' where the tree would be
                .story("NoData", VisStories.story("Tree", treeSettings(),
                        VisTable.builder().text("Path").number("Size").build()))
                .withPlay(play -> {
                    final Query chart = VisStories.drawn(play, "svg.vis-tree", 1).first();
                    play.waitFor(() -> play.expect(chart).toHaveAttribute("aria-label", "Tree of Path: no data"));
                    play.expect(play.withinFrame(VisStories.frame(play)).querySelector(".vis-empty"))
                            .toHaveTextContent("No data");
                    VisStories.expectNoProblems(play);
                });
    }

    // The Tree settings of the files' sizes, and any more
    // Two servers' files: three folders of 30 files each on each, 94 nodes a tree with the root
    private static VisTable bigTreeByServer() {
        final VisTable.Builder builder = VisTable.builder().text("Server").text("Path").number("Size");
        for (final String server : List.of("files-1", "files-2")) {
            for (final String folder : List.of("docs", "logs", "media")) {
                for (int file = 0; file < 30; file++) {
                    builder.row(server, folder + "/file-" + file + ".txt", 10 + file);
                }
            }
        }
        return builder.build();
    }

    private static Map<String, String> treeSettings(final String... more) {
        final Map<String, String> settings = VisStories.settings("path", "Path", "value", "Size");
        settings.putAll(VisStories.settings(more));
        return settings;
    }

    private static void addForce(final StoryRegistry registry) {
        registry.component(VisStories.TITLE + "Force", NetworkVisStories.class)
                .layout(StoryLayout.FULLSCREEN)
                .tags(VisStories.TAG)
                // What Stroom is told of a selected node (the Selection column of the design): Ctrl+Click
                // tells it of the node, and again of nothing
                .story("SelectionPayload", VisStories.story("Force", forceSettings(), EMAILS))
                .withPlay(play -> {
                    VisStories.drawn(play, FORCE_NODE);
                    final Query point = play.withinFrame(VisStories.frame(play))
                            .querySelectorAll(FORCE_NODE).first();
                    VisStories.expectSelectionOf(play, point,
                            "[{\"key\":\"Group: Ops\",\"name\":\"Ops\",\"group\":\"Ops\"}]");
                    VisStories.expectNoProblems(play);
                })
                // A node for each team, linked by their members' emails (the groups are collapsed by
                // default), each team's emails to its own members counted within it
                .story("Teams", VisStories.story("Force", forceSettings(), EMAILS))
                .withPlay(play -> {
                    final Query nodes = VisStories.drawn(play, FORCE_NODE, 3);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.expect(inFrame.querySelectorAll(FORCE_LINK)).toHaveLength(3);
                    play.waitFor(() -> play.expect(inFrame.querySelector("svg.vis-force")).toHaveAttribute(
                            "aria-label", "Network of Emails between From and To: 3 nodes, 3 of them groups "
                                          + "(3 collapsed), 3 links"));
                    play.expect(inFrame.getByRole("img", OPS)).toBeVisible();
                    if (StroomThemeDecorator.isForcedColours()) {
                        // With a high contrast theme, each node is edged in its text colour
                        final Query vis = inFrame.querySelector(".vis");
                        play.expect("the text's colour and a node's edge",
                                        () -> List.of(vis.computedStyle("color").get(),
                                                nodes.first().computedStyle("stroke").get()))
                                .toSatisfy("the same", colours -> new HashSet<>((List<?>) colours).size() == 1);
                    }
                    VisStories.expectNoProblems(play);
                })
                // Clicking a team shows its members, joined to it, and clicking it again hides them
                .story("ExpandGroup", VisStories.story("Force", forceSettings(), EMAILS))
                .withPlay(play -> {
                    final Query nodes = VisStories.drawn(play, FORCE_NODE, 3);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.click(inFrame.getByRole("img", OPS));
                    play.waitFor(() -> play.expect(nodes).toHaveLength(5));
                    play.waitFor(() -> play.expect(inFrame.querySelector("svg.vis-force").attribute("aria-label"))
                            .toMatch("(2 collapsed)"));
                    play.expect(inFrame.querySelectorAll(".vis-force__link--membership")).toHaveLength(2);
                    play.click(inFrame.querySelector(".vis-force__node--expanded"));
                    play.waitFor(() -> play.expect(nodes).toHaveLength(3));
                    VisStories.expectNoProblems(play);
                })
                // Every team expanded: a node for each person, coloured by team, named beside it
                .story("People", VisStories.story("Force", forceSettings("expandGroups", "true"), EMAILS))
                .withPlay(play -> {
                    VisStories.drawn(play, FORCE_NODE, 9);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.expect(inFrame.querySelectorAll(FORCE_LINK + ":not(.vis-force__link--membership)"))
                            .toHaveLength(7);
                    play.expect(inFrame.getByRole("img", ALICE)).toBeVisible();
                    play.expect(inFrame.querySelectorAll("text.vis-force__label")).toHaveLength(9);
                    VisStories.expectNoProblems(play);
                })
                // With the mouse over alice, the people and lines not joined to her fade, but no names
                .story("Hover", VisStories.story("Force", forceSettings("expandGroups", "true"), EMAILS))
                .withPlay(play -> {
                    VisStories.drawn(play, FORCE_NODE, 9);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.hover(inFrame.getByRole("img", ALICE));
                    final Query frank = inFrame.getByRole("img", "frank, From Team: Security, Emails out: 9, "
                                                                 + "To erin: 9");
                    play.waitFor(() -> play.expect(frank).toHaveClass("vis-force__node--faded"));
                    play.expect(inFrame.getByRole("img", "bob, From Team: Ops, Emails out: 3, Emails in: 12, "
                                                         + "From alice: 12, To carol: 3"))
                            .not().toHaveClass("vis-force__node--faded");
                    play.expect(inFrame.querySelectorAll(".vis-force__label--faded")).toHaveLength(0);
                    final Query tooltip = VisStories.tooltip(play);
                    play.waitFor(() -> play.expect(tooltip).toBeVisible());
                    play.expect(tooltip).toHaveTextContent(TextMatch.containing("To bob"));
                    VisStories.expectNoProblems(play);
                })
                // The nodes are one tab stop, a team at a time: Down moves to the next team, Enter on a
                // team expands it (the focus staying on it), Right moves to its first member, and
                // Enter on a person selects them
                .story("Keyboard", VisStories.story("Force", forceSettings(), EMAILS))
                .withPlay(play -> {
                    final Query nodes = VisStories.drawn(play, FORCE_NODE, 3);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    VisStories.focusTabStop(play);
                    play.waitFor(() -> play.expect(inFrame.getByRole("img", OPS)).toHaveFocus());
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
                            .toHaveClass("vis-force__node--selected"));
                    VisStories.expectNoProblems(play);
                })
                // The search field, from Stroom's toolbar: what is typed highlights the nodes it matches
                // (a collapsed team with a matching member), a screen reader hears how many, Enter
                // moves to the first and Escape clears and closes it (the legacy search took every key
                // on the page)
                .story("Search", VisStories.story("Force", forceSettings(), EMAILS))
                .withPlay(play -> {
                    VisStories.drawn(play, FORCE_NODE, 3);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.click(play.screen().getByRole("button", "Find nodes"));
                    final Query field = inFrame.getByRole("searchbox", "Find nodes");
                    play.waitFor(() -> play.expect(field).toHaveFocus());
                    play.type(field, "car");
                    final Query count = inFrame.querySelector(".vis-search__count");
                    play.waitFor(() -> play.expect(count).toHaveTextContent("1 node matches"));
                    play.expect(count).toHaveAttribute("aria-live", "polite");
                    final Query dev = inFrame.querySelector(".vis-force__node--match");
                    play.expect(inFrame.querySelectorAll(".vis-force__node--match")).toHaveLength(1);
                    play.expect(inFrame.querySelectorAll(".vis-force__node--faded")).toHaveLength(2);
                    VisStories.press(play, "{Enter}");
                    play.waitFor(() -> play.expect(dev).toHaveFocus());
                    play.expect(dev.attribute("aria-label")).toMatch("Dev (group)");
                    play.click(field);
                    VisStories.press(play, "{Escape}");
                    play.waitFor(() -> play.expect(inFrame.querySelector(".vis-search")).not().toBeVisible());
                    play.expect(inFrame.querySelectorAll(".vis-force__node--faded")).toHaveLength(0);
                    VisStories.expectNoProblems(play);
                })
                // With Animate on, a dot runs along each row's line in turn, its two ends lit, and the
                // Pause button in Stroom's toolbar stops it (WCAG 2.2.2)
                .story("Animate", VisStories.story("Force", forceSettings("expandGroups", "true", "animate", "true"),
                        EMAILS))
                .withPlay(play -> {
                    VisStories.drawn(play, FORCE_NODE, 9);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    final Query zap = inFrame.querySelector(".vis-force__zap");
                    play.waitFor(() -> play.expect(zap).toBeVisible());
                    play.expect(inFrame.querySelectorAll(".vis-force__node--lit")).toHaveLength(2);
                    play.click(play.screen().getByRole("button", "Pause the animation"));
                    play.waitFor(() -> play.expect(zap).not().toBeVisible());
                    play.expect(inFrame.querySelectorAll(".vis-force__node--lit")).toHaveLength(0);
                    play.expect(play.screen().getByRole("button", "Play the animation")).toBeVisible();
                    VisStories.expectNoProblems(play);
                })
                // Each line has a target in its middle, after the nodes for the keyboard, its tooltip the
                // counts each way. Clicking the one between two collapsed teams shows the people it
                // joins (as the legacy network did), and Enter on theirs goes back
                .story("ExpandLine", VisStories.story("Force", forceSettings(), EMAILS))
                .withPlay(play -> {
                    final Query nodes = VisStories.drawn(play, FORCE_NODE, 3);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    final Query opsDev = inFrame.getByRole("img", "Dev (group) and Ops (group), Ops (group) to "
                                                                  + "Dev (group): 8, Click or Enter: shows the members "
                                                                  + "it joins");
                    play.click(opsDev);
                    play.waitFor(() -> play.expect(nodes).toHaveLength(6));
                    final Query aliceCarol = inFrame.getByRole("img", "alice and carol, alice to carol: 5, Click or "
                                                                      + "Enter: shows their groups' line again");
                    play.run("focus alice and carol's line", () -> aliceCarol.element().get().focus());
                    VisStories.press(play, "{Enter}");
                    play.waitFor(() -> play.expect(nodes).toHaveLength(3));
                    VisStories.expectNoProblems(play);
                })
                // + and − zoom and 0 fits it again, from the keyboard as the wheel and double click do
                .story("KeyboardZoom", VisStories.story("Force", forceSettings("expandGroups", "true"), EMAILS))
                .withPlay(play -> {
                    VisStories.drawn(play, FORCE_NODE, 9);
                    VisStories.expectKeyboardZoom(play, "svg.vis-force", "svg.vis-force > g");
                    VisStories.expectNoProblems(play);
                })
                // Rows without a source or target, or with a count that isn't above zero, are left out
                // (the legacy network counted a count of 0 as 1)
                .story("AwkwardData", VisStories.story("Force",
                        VisStories.settings("source", "From", "target", "To", "count", "Emails"),
                        VisTable.builder().text("From").text("To").number("Emails")
                                .row("alice", "bob", 1)
                                .row("alice", "carol", 0)
                                .row(null, "dave", 2)
                                .row("erin", "", 3)
                                .build()))
                .withPlay(play -> {
                    VisStories.drawn(play, FORCE_NODE, 2);
                    final Query network = play.withinFrame(VisStories.frame(play)).querySelector("svg.vis-force");
                    play.waitFor(() -> play.expect(network)
                            .toHaveAttribute("aria-label",
                                    "Network of Emails between From and To: 2 nodes, 1 link"));
                    VisStories.expectNoProblems(play);
                })
                // One email: two people and a line between them
                .story("OneValue", VisStories.story("Force",
                        VisStories.settings("source", "From", "target", "To", "count", "Emails"),
                        VisTable.builder().text("From").text("To").number("Emails").row("alice", "bob", 1).build()))
                .withPlay(play -> {
                    VisStories.drawn(play, FORCE_NODE, 2);
                    play.expect(play.withinFrame(VisStories.frame(play)).querySelectorAll(FORCE_LINK)).toHaveLength(1);
                    VisStories.expectNoProblems(play);
                })
                // A search that found nothing
                .story("NoData", VisStories.story("Force",
                        VisStories.settings("source", "From", "target", "To", "count", "Emails"),
                        VisTable.builder().text("From").text("To").number("Emails").build()))
                .withPlay(play -> {
                    final Query chart = VisStories.drawn(play, "svg.vis-force", 1).first();
                    play.waitFor(() -> play.expect(chart).toHaveAttribute("aria-label",
                            "Network of Emails between From and To: no data"));
                    play.expect(play.withinFrame(VisStories.frame(play)).querySelector(".vis-empty"))
                            .toHaveTextContent("No data");
                    VisStories.expectNoProblems(play);
                });
    }

    // The Force settings of the emails between teams, and any more
    private static Map<String, String> forceSettings(final String... more) {
        final Map<String, String> settings = VisStories.settings("source", "From", "target", "To",
                "sourceGroup", "From Team", "targetGroup", "To Team", "count", "Emails");
        settings.putAll(VisStories.settings(more));
        return settings;
    }

    private static void addChordLabels(final StoryRegistry registry) {
        registry.component(VisStories.TITLE + "ChordLabels", NetworkVisStories.class)
                .layout(StoryLayout.FULLSCREEN)
                .tags(VisStories.TAG)
                // What Stroom is told of a selected name's arc (the Selection column of the design): Ctrl+Click
                // tells it of the name's arc, and again of nothing
                .story("SelectionPayload", VisStories.story("ChordLabels", chordSettings(), EMAILS))
                .withPlay(play -> {
                    VisStories.drawn(play, CHORD_ARC);
                    final Query point = play.withinFrame(VisStories.frame(play))
                            .querySelectorAll(CHORD_ARC).first();
                    VisStories.expectSelectionOf(play, point,
                            "[{\"key\":\"carol\",\"name\":\"carol\"}]");
                    VisStories.expectNoProblems(play);
                })
                // Who emailed whom: an arc for each person, as long as their emails out and in, and
                // an arrow of a ribbon for each sender and recipient, in the sender's colour
                .story("Emails", VisStories.story("ChordLabels", chordSettings(), EMAILS))
                .withPlay(play -> {
                    final Query arcs = VisStories.drawn(play, CHORD_ARC, 6);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.expect(inFrame.querySelectorAll(CHORD_RIBBON)).toHaveLength(7);
                    play.waitFor(() -> play.expect(inFrame.querySelector("svg.vis-chord")).toHaveAttribute(
                            "aria-label", "Chord diagram of Emails between From and To: 6 names, 7 flows, "
                                          + "total 58, largest carol to dave 20"));
                    play.expect(inFrame.getByRole("img", CAROL)).toBeVisible();
                    play.expect(inFrame.getByRole("img", CAROL_TO_DAVE)).toBeVisible();
                    // Each person's name is beside their arc, in the theme's text colour
                    final Query labels = inFrame.querySelectorAll(CHORD_LABEL);
                    play.expect(labels).toHaveLength(6);
                    final Query vis = inFrame.querySelector(".vis");
                    play.expect("the text's colour and a name's",
                                    () -> List.of(vis.computedStyle("color").get(),
                                            labels.first().computedStyle("fill").get()))
                            .toSatisfy("the same", colours -> new HashSet<>((List<?>) colours).size() == 1);
                    if (StroomThemeDecorator.isForcedColours()) {
                        // With a high contrast theme, each arc is edged in its text colour
                        play.expect("the text's colour and an arc's edge's",
                                        () -> List.of(vis.computedStyle("color").get(),
                                                arcs.first().computedStyle("stroke").get()))
                                .toSatisfy("the same", colours -> new HashSet<>((List<?>) colours).size() == 1);
                    }
                    VisStories.expectNoProblems(play);
                })
                // Someone who is only ever emailed (bob) has an arc too, as long as their emails in
                // (the legacy diagram's arcs were only as long as the emails out, so bob had none)
                .story("OnlyATarget", VisStories.story("ChordLabels", chordSettings(),
                        VisTable.builder().text("From").text("To").number("Emails")
                                .row("alice", "bob", 5)
                                .row("carol", "bob", 3)
                                .build()))
                .withPlay(play -> {
                    VisStories.drawn(play, CHORD_ARC, 3);
                    final Query bob = play.withinFrame(VisStories.frame(play))
                            .getByRole("img", "bob, Emails out: 0, Emails in: 8");
                    play.expect("the width of bob's arc", () -> bob.width().get()).toBeGreaterThan(10);
                    VisStories.expectNoProblems(play);
                })
                // Two rows from and to the same people are added up (the legacy diagram kept the last)
                .story("SameTwice", VisStories.story("ChordLabels", chordSettings(),
                        VisTable.builder().text("From").text("To").number("Emails")
                                .row("alice", "bob", 12)
                                .row("alice", "bob", 8)
                                .row("bob", "alice", 3)
                                .build()))
                .withPlay(play -> {
                    VisStories.drawn(play, CHORD_RIBBON, 2);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.expect(inFrame.getByRole("img", "alice to bob, Emails: 20")).toBeVisible();
                    play.expect(inFrame.getByRole("img", "bob to alice, Emails: 3")).toBeVisible();
                    VisStories.expectNoProblems(play);
                })
                // With the mouse over carol, the ribbons and people not joined to her fade, but the
                // names never do
                .story("Hover", VisStories.story("ChordLabels", chordSettings(), EMAILS))
                .withPlay(play -> {
                    VisStories.drawn(play, CHORD_ARC, 6);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.hover(inFrame.getByRole("img", CAROL));
                    play.waitFor(() -> play.expect(inFrame.getByRole("img", "frank to erin, Emails: 9"))
                            .toHaveClass("vis-chord__ribbon--faded"));
                    play.expect(inFrame.querySelectorAll(".vis-chord__ribbon--faded")).toHaveLength(4);
                    play.expect(inFrame.getByRole("img", "frank, Emails out: 9, Emails in: 0"))
                            .toHaveClass("vis-chord__arc--faded");
                    play.expect(inFrame.querySelectorAll(".vis-chord__arc--faded")).toHaveLength(2);
                    play.expect(inFrame.querySelectorAll(".vis-chord__label--faded")).toHaveLength(0);
                    final Query tooltip = VisStories.tooltip(play);
                    play.waitFor(() -> play.expect(tooltip).toBeVisible());
                    play.expect(tooltip).toHaveTextContent(TextMatch.containing("Emails in"));
                    VisStories.expectNoProblems(play);
                })
                // The arcs, round the ring from the biggest, are the tab stop; Down crosses to the
                // ribbons, the widest first, and Enter selects one, fading what isn't joined to it
                .story("Keyboard", VisStories.story("ChordLabels", chordSettings(), EMAILS))
                .withPlay(play -> {
                    VisStories.drawn(play, CHORD_ARC, 6);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    VisStories.focusTabStop(play);
                    play.waitFor(() -> play.expect(inFrame.getByRole("img", CAROL)).toHaveFocus());
                    VisStories.press(play, "{ArrowRight}");
                    play.waitFor(() -> play.expect(inFrame.getByRole("img", "alice, Emails out: 17, Emails in: 7"))
                            .toHaveFocus());
                    VisStories.press(play, "{ArrowLeft}{ArrowDown}");
                    final Query carolToDave = inFrame.getByRole("img", TextMatch.startingWith(CAROL_TO_DAVE));
                    play.waitFor(() -> play.expect(carolToDave).toHaveFocus());
                    VisStories.press(play, "{Enter}");
                    play.waitFor(() -> play.expect(carolToDave).toHaveClass("vis-chord__ribbon--selected"));
                    play.expect(carolToDave).toHaveAttribute("aria-label", CAROL_TO_DAVE + ", selected");
                    // The selection keeps its highlight when the focus leaves
                    play.run("move the focus away", () -> carolToDave.element().get().blur());
                    play.waitFor(() -> play.expect(inFrame.querySelectorAll(".vis-chord__ribbon--faded"))
                            .toHaveLength(6));
                    play.expect(inFrame.querySelectorAll(".vis-chord__arc--faded")).toHaveLength(4);
                    // Escape selects nothing
                    play.run("focus the ribbon again", () -> carolToDave.element().get().focus());
                    VisStories.press(play, "{Escape}");
                    play.waitFor(() -> play.expect(inFrame.querySelectorAll(".vis-chord__ribbon--selected"))
                            .toHaveLength(0));
                    play.expect(carolToDave).toHaveAttribute("aria-label", CAROL_TO_DAVE);
                    VisStories.expectNoProblems(play);
                })
                // + and − zoom and 0 puts it back (the legacy diagram zoomed and panned with the mouse
                // only, and the first migration dropped that)
                .story("KeyboardZoom", VisStories.story("ChordLabels", chordSettings(), EMAILS))
                .withPlay(play -> {
                    VisStories.drawn(play, CHORD_ARC, 6);
                    VisStories.expectKeyboardZoom(play, "svg.vis-chord", "svg.vis-chord > g");
                    VisStories.expectNoProblems(play);
                })
                // A diagram for each sender's team, each named by its team
                .story("GridSeries", VisStories.story("ChordLabels", chordSettings("gridSeries", "From Team"), EMAILS))
                .withPlay(play -> {
                    VisStories.drawn(play, "svg.vis-chord", 3);
                    final Query names = play.withinFrame(VisStories.frame(play)).querySelectorAll(".vis-grid__name");
                    play.expect("the diagrams' names", () -> names.textContents().get())
                            .toEqual(List.of("Dev", "Ops", "Security"));
                    VisStories.expectNoProblems(play);
                })
                // One email: two arcs and a ribbon between them
                .story("OneValue", VisStories.story("ChordLabels", chordSettings(),
                        VisTable.builder().text("From").text("To").number("Emails").row("alice", "bob", 1).build()))
                .withPlay(play -> {
                    VisStories.drawn(play, CHORD_ARC, 2);
                    play.expect(play.withinFrame(VisStories.frame(play)).querySelectorAll(CHORD_RIBBON))
                            .toHaveLength(1);
                    VisStories.expectNoProblems(play);
                })
                // Rows without a sender, recipient or count above zero are left out, and a name too
                // long for the room beside the ring is shortened, though its tooltip has all of it
                .story("AwkwardData", VisStories.story("ChordLabels", chordSettings(),
                        VisTable.builder().text("From").text("To").number("Emails")
                                .row("backup-service.storage.cluster-07.internal.example.org", "alice", 4)
                                .row(null, "carol", 3)
                                .row("dave", null, 3)
                                .row("erin", "frank", 0)
                                .row("gina", "harry", null)
                                .build()))
                .withPlay(play -> {
                    VisStories.drawn(play, CHORD_ARC, 2);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.expect(inFrame.getByRole("img", "backup-service.storage.cluster-07.internal.example.org, "
                                                         + "Emails out: 4, Emails in: 0")).toBeVisible();
                    final Query labels = inFrame.querySelectorAll(CHORD_LABEL);
                    play.expect("the names beside the ring", () -> labels.textContents().get())
                            .toSatisfy("one shortened with an ellipsis, and alice",
                                    texts -> ((List<?>) texts).contains("alice")
                                             && ((List<?>) texts).stream()
                                                     .anyMatch(text -> String.valueOf(text).endsWith("…")));
                    VisStories.expectNoProblems(play);
                })
                // A search that found nothing: 'No data' where the ring would be
                .story("NoData", VisStories.story("ChordLabels", chordSettings(),
                        VisTable.builder().text("From").text("To").number("Emails").build()))
                .withPlay(play -> {
                    final Query chart = VisStories.drawn(play, "svg.vis-chord", 1).first();
                    play.waitFor(() -> play.expect(chart).toHaveAttribute("aria-label",
                            "Chord diagram of Emails between From and To: no data"));
                    play.expect(play.withinFrame(VisStories.frame(play)).querySelector(".vis-empty"))
                            .toHaveTextContent("No data");
                    VisStories.expectNoProblems(play);
                });
    }

    // The chord diagram's settings for the emails, and any more
    private static Map<String, String> chordSettings(final String... more) {
        final Map<String, String> settings = VisStories.settings(
                "source", "From", "target", "To", "count", "Emails");
        settings.putAll(VisStories.settings(more));
        return settings;
    }
}
