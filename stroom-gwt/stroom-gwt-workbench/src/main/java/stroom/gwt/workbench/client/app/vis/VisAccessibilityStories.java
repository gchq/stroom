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
import stroom.gwt.workbench.framework.client.story.StoryRenderer;

/// The `App/Visualisations/Accessibility` stories: the content pack's real charts used without a
/// mouse, and their tooltips, which Stroom shows on its page rather than in the chart's frame.
///
/// Both come from the content pack's `Common.js` (`keyboardPoints` and `hostTip`): every element a
/// chart gives hover handlers is a data point, one tab stop for the chart (a roving tabindex) with
/// the arrow keys moving between them. A point with the keyboard focus shows its tooltip, is named
/// by it for screen readers, and is selected by Enter as Ctrl+Click selects it. A tooltip is
/// asked of Stroom (`stroom.showTooltip` in `vis.js`), which draws it on the page (`VisTooltip`).
///
/// The charts and their data are those of [ChartVisStories], so the points' names are known: a
/// bar chart of the requests to each host by status, a scatter of each host's responses and a
/// doughnut of one host's requests by status.
public final class VisAccessibilityStories {

    private static final String TITLE = VisStories.TITLE + "Accessibility";

    // The bar chart's bars, in the order the keyboard visits them: each status is a group, its bars
    // in host order
    private static final String DB_01_OK = "200 OK, Host: db-01, Requests: 1,380";
    private static final String WEB_01_OK = "200 OK, Host: web-01, Requests: 1,200";
    private static final String WEB_02_OK = "200 OK, Host: web-02, Requests: 1,290";
    private static final String DB_01_MOVED = "301 Moved, Host: db-01, Requests: 515";
    private static final String WEB_01_MOVED = "301 Moved, Host: web-01, Requests: 425";
    private static final String WEB_02_MOVED = "301 Moved, Host: web-02, Requests: 470";

    // The asset-based bar chart's bars
    private static final String BAR = "rect.vis-bar-chart__bar";
    // How faded the bars that aren't highlighted or selected are
    private static final String FADED = "0.2";

    private VisAccessibilityStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component(TITLE, VisAccessibilityStories.class)
                .layout(StoryLayout.FULLSCREEN)
                .tags(VisStories.TAG)
                // A bar the mouse is over has its tooltip, drawn by Stroom on the page, and it goes
                // when the mouse leaves
                .story("HoverShowsTooltip", barChart())
                .withPlay(play -> {
                    final Query bars = VisStories.drawn(play, BAR, 12);
                    final Query tooltip = VisStories.tooltip(play);
                    play.hover(bars.first());
                    play.waitFor(() -> play.expect(tooltip).toBeVisible());
                    play.expect(tooltip).toHaveTextContent(TextMatch.containing("db-01"));
                    play.expect(tooltip).toHaveTextContent(TextMatch.containing("1,380"));
                    // Visual only: screen readers hear the point's name instead
                    play.expect(tooltip).toHaveAttribute("aria-hidden", "true");
                    play.unhover(bars.first());
                    play.waitFor(() -> play.expect(tooltip).not().toBeVisible());
                    VisStories.expectNoProblems(play);
                })
                // The chart is one tab stop. Its first bar, given the focus, is named by its
                // tooltip, which shows, and has a focus ring. Left and Right move along a status's
                // bars, on to the next or previous status at the ends; Up and Down move to the
                // same host's bar of another status; Home and End to a status's first and last
                // bars. The tab stop moves with the focus
                .story("ArrowKeysMoveBetweenBars", barChart())
                .withPlay(play -> {
                    VisStories.drawn(play, BAR, 12);
                    final Query tooltip = VisStories.tooltip(play);
                    VisStories.focusTabStop(play);
                    expectFocusOn(play, DB_01_OK);
                    play.waitFor(() -> play.expect(tooltip).toBeVisible());
                    play.expect(tooltip).toHaveTextContent(TextMatch.containing("db-01"));
                    // The keyboard focus ring vis.css gives a point
                    play.expect(VisStories.focusedPoint(play).computedStyle("outline-style")).toBe("solid");

                    VisStories.press(play, "{ArrowRight}");
                    expectFocusOn(play, WEB_01_OK);
                    play.waitFor(() -> play.expect(tooltip).toHaveTextContent(TextMatch.containing("1,200")));
                    // Still one tab stop, now the focused bar
                    play.expect(VisStories.tabStop(play)).toHaveFocus();

                    VisStories.press(play, "{ArrowDown}");
                    expectFocusOn(play, WEB_01_MOVED);
                    VisStories.press(play, "{End}");
                    expectFocusOn(play, WEB_02_MOVED);
                    VisStories.press(play, "{Home}");
                    expectFocusOn(play, DB_01_MOVED);
                    // Before the first 301 is the last 200
                    VisStories.press(play, "{ArrowLeft}");
                    expectFocusOn(play, WEB_02_OK);
                    VisStories.press(play, "{ArrowUp}");
                    // Already in the first status, so it stays
                    expectFocusOn(play, WEB_02_OK);
                    VisStories.expectNoProblems(play);
                })
                // Escape hides the tooltip and the highlight, leaving the focus where it is, and
                // Tab leaves the chart, hiding the tooltip (WCAG 1.4.13, Content on Hover or
                // Focus)
                .story("EscapeAndTabHideTooltip", barChart())
                .withPlay(play -> {
                    final Query bars = VisStories.drawn(play, BAR, 12);
                    final Query tooltip = VisStories.tooltip(play);
                    VisStories.focusTabStop(play);
                    play.waitFor(() -> play.expect(tooltip).toBeVisible());
                    // The other bars fade while one is highlighted
                    play.waitFor(() -> play.expect(bars.nth(1).computedStyle(fades())).toBe(FADED));

                    VisStories.press(play, "{Escape}");
                    play.waitFor(() -> play.expect(tooltip).not().toBeVisible());
                    play.waitFor(() -> play.expect(bars.nth(1).computedStyle(fades())).toBe("1"));
                    expectFocusOn(play, DB_01_OK);

                    VisStories.press(play, "{ArrowRight}");
                    play.waitFor(() -> play.expect(tooltip).toBeVisible());
                    VisStories.press(play, "{Tab}");
                    play.expect(VisStories.focusedPoint(play)).not().toBeInTheDocument();
                    play.waitFor(() -> play.expect(tooltip).not().toBeVisible());
                    VisStories.expectNoProblems(play);
                })
                // Enter selects the focused bar, as Ctrl+Click does: once the focus has left, the
                // selected bar stays bright and the others faded, and its name says it is selected.
                // Enter again deselects it, and Escape selects nothing
                .story("EnterSelectsBar", barChart())
                .withPlay(play -> {
                    final Query bars = VisStories.drawn(play, BAR, 12);
                    VisStories.focusTabStop(play);
                    VisStories.press(play, "{Enter}");
                    VisStories.press(play, "{Tab}");
                    play.waitFor(() -> play.expect(bars.nth(1).computedStyle(fades())).toBe(FADED));
                    play.expect(bars.first().computedStyle(fades())).toBe("1");
                    play.expect(bars.first().attribute("aria-label")).toMatch(", selected");
                    VisStories.focusTabStop(play);
                    VisStories.press(play, "{Enter}");
                    VisStories.press(play, "{Tab}");
                    play.waitFor(() -> play.expect(bars.nth(1).computedStyle(fades())).toBe("1"));
                    play.expect(bars.first().attribute("aria-label")).not().toMatch(", selected");
                    VisStories.focusTabStop(play);
                    VisStories.press(play, "{Enter}");
                    VisStories.press(play, "{Escape}");
                    VisStories.press(play, "{Tab}");
                    play.waitFor(() -> play.expect(bars.nth(1).computedStyle(fades())).toBe("1"));
                    play.expect(bars.first().attribute("aria-label")).not().toMatch(", selected");
                    VisStories.expectNoProblems(play);
                })
                // Each host's responses are a group: Down and Up move between the hosts
                .story("ScatterSeries", VisStories.story("Scatter",
                        VisStories.settings("x", "Duration", "y", "Bytes", "pointSeries", "Host"),
                        ChartVisStories.responseSizes()))
                .withPlay(play -> {
                    VisStories.drawn(play, "circle.vis-scatter__point", 30);
                    VisStories.focusTabStop(play);
                    expectFocusOn(play, "db-01, Duration: 74, Bytes: 8,000");
                    VisStories.press(play, "{ArrowDown}");
                    expectFocusOn(play, "web-01, Duration: 25, Bytes: 2,000");
                    VisStories.press(play, "{ArrowDown}");
                    expectFocusOn(play, "web-02, Duration: 49, Bytes: 5,000");
                    VisStories.press(play, "{ArrowUp}");
                    expectFocusOn(play, "web-01, Duration: 25, Bytes: 2,000");
                    VisStories.expectNoProblems(play);
                })
                // A doughnut's slices are named by their tooltips, share and all
                .story("DoughnutSlices", VisStories.story("Doughnut",
                        VisStories.settings("names", "Status", "values", "Requests"),
                        ChartVisStories.requestsForHost("web-01")))
                .withPlay(play -> {
                    VisStories.drawn(play, "path.vis-doughnut__slice", 4);
                    final Query tooltip = VisStories.tooltip(play);
                    VisStories.focusTabStop(play);
                    expectFocusOn(play, "200 OK, Requests: 1,200, Share: 65.6%");
                    VisStories.press(play, "{ArrowRight}");
                    expectFocusOn(play, "301 Moved, Requests: 425, Share: 23.2%");
                    play.waitFor(() -> play.expect(tooltip).toHaveTextContent(TextMatch.containing("23.2%")));
                    VisStories.expectNoProblems(play);
                });
    }

    // The bar chart of the requests to each host by status, the hosts along the bottom
    private static StoryRenderer barChart() {
        return VisStories.story("BarChart",
                VisStories.settings("x", "Host", "y", "Requests", "series", "Status"),
                ChartVisStories.REQUESTS_BY_STATUS);
    }

    // Expects the point with a name to have the focus
    private static void expectFocusOn(final Play play, final String name) {
        expectFocusOn(play, TextMatch.exact(name));
    }

    private static void expectFocusOn(final Play play, final TextMatch name) {
        final Query point = play.withinFrame(VisStories.frame(play)).getByRole("img", name);
        play.waitFor(() -> play.expect(point).toHaveFocus());
    }

    // What fades: the whole bar, or with a high contrast theme (forced colours) only its fill, so
    // that its outline still shows. Asked as a play runs, as it asks the browser: the JVM tests
    // load this class (to list the stories) without one
    private static String fades() {
        return StroomThemeDecorator.isForcedColours()
                ? "fill-opacity"
                : "opacity";
    }
}
