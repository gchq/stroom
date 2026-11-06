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

package stroom.gwt.workbench.client.app.dashboard;

import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Value;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.dom.client.Element;

/// Stories of the splitters' overlays (`SplitterOverlay`): each splitter's line and grab area are
/// drawn on the page's root, above the frames of visualisations, which are on the root over the
/// dashboard. The overlays must stay over their splitters as the dashboard scrolls, changes and
/// goes, clipped to the dashboard's scrolled area.
///
/// Each story puts the dashboard in a stacking context of its own, as Stroom's content pane does
/// ([DashboardSupport#isolated]), and shows the workbench's test visualisation, which paints its
/// frame ([VisToolbarStories]).
///
/// Every check of the overlays is [#overlaysMismatch]: one overlay for each splitter on the page,
/// each inside the dashboard's scrolled area, and the grab areas seen of them those of the splitters
/// (as much of each as the scrolled area shows).
public final class DashboardSplitterOverlayStories {

    // A Query and Table, the visualisation, then a second Table, 800px each in a dashboard 2400px
    // wide, about twice the story's page, so it scrolls across
    private static final String WIDE = DashboardDocs.doc("dash-ov-wide", "Wide", null,
            "\"designMode\": false, \"layoutConstraints\": {\"fitWidth\": false, \"fitHeight\": true}, "
            + "\"preferredSize\": {\"width\": 2400, \"height\": 600}",
            DashboardDocs.split(0,
                    DashboardDocs.sized(DashboardDocs.tabs(1, "q1", "t1"), 800, 100),
                    DashboardDocs.sized(DashboardDocs.tabs(0, "v1"), 800, 100),
                    DashboardDocs.sized(DashboardDocs.tabs(0, "t2"), 800, 100)),
            DashboardDocs.query("q1", "Q", ", \"automate\": {\"open\": true}"),
            DashboardDocs.table("t1", "T", "q1", DashboardDocs.NAME_FIELD, ""),
            VisToolbarStories.visComponent("v1", "Chart"),
            DashboardDocs.table("t2", "T2", "q1", DashboardDocs.NAME_FIELD, ""));

    // A Query and Table, and the visualisation right of them
    private static final String SIDE_BY_SIDE = DashboardDocs.doc("dash-ov-side", "Side", null,
            "\"designMode\": false",
            DashboardDocs.split(0,
                    DashboardDocs.sized(DashboardDocs.tabs(1, "q1", "t1"), 300, 100),
                    DashboardDocs.sized(DashboardDocs.tabs(0, "v1"), 500, 100)),
            DashboardDocs.query("q1", "Q", ", \"automate\": {\"open\": true}"),
            DashboardDocs.table("t1", "T", "q1", DashboardDocs.NAME_FIELD, ""),
            VisToolbarStories.visComponent("v1", "Chart"));

    private DashboardSplitterOverlayStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component(DashboardSupport.TITLE, DashboardSplitterOverlayStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Scrolled across, the overlays follow their splitters and are clipped to the
                // dashboard's scrolled area: the one scrolled out of sight shows nothing, and the
                // other, now beside the visualisation, is on top of it
                .story("SplitterOverlaysScrolled", context -> DashboardSupport.isolated(
                        DashboardSupport.story(WIDE, VisToolbarStories::routes).render(context)))
                .withPlay(play -> {
                    VisToolbarStories.visFrame(play);
                    final Query first = play.getByRole("separator", "Resize T and Chart");
                    play.waitFor(() -> play.expect("how the overlays differ from the splitters",
                            DashboardSplitterOverlayStories::overlaysMismatch).toBe("none"));

                    // Scrolled so the first splitter (left of the visualisation) is near the left
                    play.run("scroll the first splitter to 200px in", () -> scrollToShow(first.element().get(), 200));
                    play.waitFor(() -> play.expect("how the overlays differ from the splitters, scrolled",
                            DashboardSplitterOverlayStories::overlaysMismatch).toBe("none"));
                    play.expect("what is on top on the first splitter's line",
                            () -> DashboardPaneStories.topmostBeside(first.element().get(), 0)).toBe("splitter");
                    play.expect("what is on top just right of it",
                            () -> DashboardPaneStories.topmostBeside(first.element().get(), 3)).toBe("splitter");

                    // Just past the left edge, only part of its grab area is in sight, and of its overlay
                    play.run("scroll the first splitter 2px past the left edge",
                            () -> scrollToShow(first.element().get(), -2));
                    play.waitFor(() -> play.expect("how the overlays differ from the splitters, at the edge",
                            DashboardSplitterOverlayStories::overlaysMismatch).toBe("none"));

                    // Scrolled to its end, the first splitter is out of sight: its overlay clipped away
                    play.run("scroll the dashboard to its end", () -> scrollToShow(first.element().get(), -10000));
                    play.waitFor(() -> play.expect("how the overlays differ from the splitters, at the end",
                            DashboardSplitterOverlayStories::overlaysMismatch).toBe("none"));
                    play.expect("the first splitter's left, out of sight",
                            () -> first.left().get() < 0).toBe(true);
                    DashboardPlays.expectNoProblems(play);
                })
                // The overlays go when the dashboard does (another of its views is shown, or a panel
                // maximised), and come back over their splitters with it
                .story("SplitterOverlaysFollowTheDashboard", context -> DashboardSupport.isolated(
                        DashboardSupport.story(SIDE_BY_SIDE, VisToolbarStories::routes).render(context)))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    VisToolbarStories.visFrame(play);
                    play.waitFor(() -> play.expect("how the overlays differ from the splitters",
                            DashboardSplitterOverlayStories::overlaysMismatch).toBe("none"));
                    play.expect("the number of overlays", DashboardSplitterOverlayStories::overlayCount).toBe(1);

                    // The Documentation view takes the dashboard's layer off the page
                    play.click(play.getByRole("tab", "Documentation"));
                    play.waitFor(() -> play.expect("the number of overlays with the Documentation shown",
                            DashboardSplitterOverlayStories::overlayCount).toBe(0));
                    play.click(play.getByRole("tab", "Dashboard"));
                    play.waitFor(() -> play.expect("the number of overlays back on the Dashboard",
                            DashboardSplitterOverlayStories::overlayCount).toBe(1));
                    play.waitFor(() -> play.expect("how the overlays differ from the splitters, back",
                            DashboardSplitterOverlayStories::overlaysMismatch).toBe("none"));

                    // Maximised, the panel is alone, with no splitters; restored, they are back
                    DashboardPlays.openTabMenu(play, "Chart");
                    play.click(DashboardPlays.menuItem(screen, "Maximise"));
                    play.waitFor(() -> play.expect(play.querySelectorAll(DashboardPlays.PANEL)).toHaveLength(1));
                    play.waitFor(() -> play.expect("the number of overlays, maximised",
                            DashboardSplitterOverlayStories::overlayCount).toBe(0));
                    DashboardPlays.openTabMenu(play, "Chart");
                    play.click(DashboardPlays.menuItem(screen, "Restore Pane Layout"));
                    play.waitFor(() -> play.expect(play.querySelectorAll(DashboardPlays.PANEL)).toHaveLength(2));
                    play.waitFor(() -> play.expect("how the overlays differ from the splitters, restored",
                            DashboardSplitterOverlayStories::overlaysMismatch).toBe("none"));
                    play.expect("the number of overlays, restored",
                            DashboardSplitterOverlayStories::overlayCount).toBe(1);
                    DashboardPlays.expectNoProblems(play);
                })
                // In design mode the splitter that resizes the whole dashboard has an overlay too,
                // which drags it
                .story("SplitterOverlaysDesignMode", context -> DashboardSupport.isolated(
                        DashboardSupport.story(WIDE, VisToolbarStories::routes).render(context)))
                .withPlay(play -> {
                    VisToolbarStories.visFrame(play);
                    DashboardPlays.designMode(play, true);
                    final Query width = play.getByRole("separator", "Resize the dashboard's width");
                    play.waitFor(() -> play.expect("how the overlays differ from the splitters, designing",
                            DashboardSplitterOverlayStories::overlaysMismatch).toBe("none"));

                    // Scrolled to the dashboard's right edge, its width splitter is in sight
                    play.run("scroll the width splitter into sight", () -> scrollToShow(width.element().get(), 600));
                    play.waitFor(() -> play.expect("how the overlays differ from the splitters, at the end",
                            DashboardSplitterOverlayStories::overlaysMismatch).toBe("none"));
                    final Value<Double> start = play.capture("the width splitter's left", width.left());
                    play.run("drag the width splitter 100px left from just right of its line",
                            () -> DashboardPaneStories.dragFrom(width.element().get(), 3, -100));
                    play.waitFor(() -> play.expect("how far the width splitter moved",
                            () -> (int) Math.round(width.left().get() - start.get())).toBe(-100));
                    play.waitFor(() -> play.expect("how the overlays differ from the splitters, after the drag",
                            DashboardSplitterOverlayStories::overlaysMismatch).toBe("none"));
                    DashboardPlays.expectNoProblems(play);
                });
    }

    // Scrolls the dashboard's scrolled area across to put an element that far in from its left
    // (as far as it scrolls)
    private static native void scrollToShow(Element element, double left) /*-{
        var scroll = $doc.querySelector('.flex-layout__scroll');
        scroll.scrollLeft += element.getBoundingClientRect().left - scroll.getBoundingClientRect().left - left;
    }-*/;

    // How many splitter overlays are on the page
    private static native int overlayCount() /*-{
        return $doc.querySelectorAll('.flex-layout__overlay').length;
    }-*/;

    // 'none', or how the splitters' overlays differ from the splitters: not one each, one outside
    // the dashboard's scrolled area, or the grab areas seen (each splitter's, and each overlay's,
    // within the scrolled area) not the same
    private static native String overlaysMismatch() /*-{
        var scroll = $doc.querySelector('.flex-layout__scroll');
        var splitters = $doc.querySelectorAll('.flex-layout__splitter');
        var overlays = $doc.querySelectorAll('.flex-layout__overlay');
        if (!scroll) {
            return 'no dashboard';
        }
        if (splitters.length !== overlays.length) {
            return overlays.length + ' overlays for ' + splitters.length + ' splitters';
        }
        var s = scroll.getBoundingClientRect();
        var clip = {left: s.left, top: s.top, right: s.left + scroll.clientWidth, bottom: s.top + scroll.clientHeight};
        var cut = function (r, c) {
            var left = Math.max(r.left, c.left);
            var top = Math.max(r.top, c.top);
            var right = Math.min(r.right, c.right);
            var bottom = Math.min(r.bottom, c.bottom);
            return right - left > 0.5 && bottom - top > 0.5
                ? Math.round(left) + ',' + Math.round(top) + ' ' + Math.round(right) + ',' + Math.round(bottom)
                : null;
        };
        var want = [];
        var got = [];
        for (var i = 0; i < splitters.length; i++) {
            var w = cut(splitters[i].firstElementChild.getBoundingClientRect(), clip);
            if (w) {
                want.push(w);
            }
        }
        for (var j = 0; j < overlays.length; j++) {
            var o = overlays[j].getBoundingClientRect();
            if (o.width > 0.5 && o.height > 0.5 && (o.left < clip.left - 0.5 || o.top < clip.top - 0.5
                    || o.right > clip.right + 0.5 || o.bottom > clip.bottom + 0.5)) {
                return 'an overlay outside the dashboard\'s scrolled area';
            }
            var g = cut(overlays[j].firstElementChild.getBoundingClientRect(), o);
            if (g) {
                got.push(g);
            }
        }
        want.sort();
        got.sort();
        return want.join('; ') === got.join('; ')
            ? 'none'
            : 'grab areas ' + got.join('; ') + ' not ' + want.join('; ');
    }-*/;
}
