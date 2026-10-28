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

package stroom.gwt.workbench.client.app.main;

import stroom.gwt.workbench.client.app.gin.AppScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.security.shared.AppPermission;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;

/// Stories of `App/Main/QuickFilterHistory`: a quick filter's recent filters and its Advanced
/// Query dialog, on Stroom's real Users screen ([stroom.security.client.component.UsersController]),
/// whose filter has fields, so it offers both. The screen's own stories are in
/// [UsersScreenStories].
///
/// The fixtures answer Stroom's `QuickFilterHistoryResource` (`POST /quickFilterHistory/v1/fetch`
/// and `/record`), the parse and format of `ExpressionResource` (`POST
/// /expression/v1/parseQuickFilter` and `/formatQuickFilter`) and `UserResource.find`. The
/// requests are checked on the request spy.
public final class QuickFilterHistoryStories {

    private static final String HISTORY_PATH = "/quickFilterHistory/v1";
    private static final String FETCH_PATH = HISTORY_PATH + "/fetch";
    private static final String RECORD_PATH = HISTORY_PATH + "/record";
    private static final String PARSE_PATH = "/expression/v1/parseQuickFilter";
    private static final String FORMAT_PATH = "/expression/v1/formatQuickFilter";
    private static final String FIND_PATH = "/users/v1/find";

    private static final String RECENT_FILTER = "display:alice";
    private static final String OLDER_FILTER = "display:\\\"Bob Brown\\\"";
    // Far wider than any screen, so its menu item is cut short
    private static final String LONG_FILTER = longFilter();
    // The users list rejects a filter whose bracket doesn't close
    private static final String REJECTED_FILTER = "(alice";
    private static final String REJECTION = "Expected ')' to close the group opened at 1:1";

    private static final String USERS = """
            {"values": [{"id": 1, "uuid": "u-alice", "subjectId": "alice", "displayName": "Alice Anderson",
              "fullName": "Alice J. Anderson", "group": false, "enabled": true}],
              "pageResponse": {"offset": 0, "length": 1, "total": 1, "exact": true}}""";

    // UserResource.find() for a rejected filter: an empty page that says why
    private static final String REJECTED = """
            {"values": [], "pageResponse": {"offset": 0, "length": 0, "total": 0, "exact": true},
              "filterError": {"text": "REJECTION", "severity": "ERROR"}}"""
            .replace("REJECTION", REJECTION);

    // ExpressionResource.parseQuickFilter() for RECENT_FILTER
    private static final String PARSED = """
            {"expression": {"type": "operator", "op": "AND", "children": [
              {"type": "term", "field": "display", "condition": "CONTAINS", "value": "alice"}]}}""";

    private QuickFilterHistoryStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/QuickFilterHistory", QuickFilterHistoryStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // The arrow lists the user's recent filters for this quick filter, most recent
                // first; picking one applies it and remembers it again
                .story("RecentFilters", context -> render(context, fixtures(RestReply.json(
                        "[\"" + RECENT_FILTER + "\", \"" + OLDER_FILTER + "\", \"" + LONG_FILTER + "\"]"))))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Alice Anderson");
                    play.click(play.getByTitle("Show Recent Filters"));
                    play.expect(screen.findByText("display:\"Bob Brown\"")).toBeInTheDocument();
                    // The menu is as wide as the filter, as a combo box's list is, and lined up
                    // with it, however long its filters
                    final Query box = play.querySelector(".quick-filter");
                    final Query menu = screen.querySelector(".menu").closest(".popup");
                    play.expect("menu's left edge", () -> Math.abs(menu.left().get() - box.left().get()))
                            .toBeLessThanOrEqual(1);
                    play.expect("menu's width", () -> Math.abs(menu.width().get() - box.width().get()))
                            .toBeLessThanOrEqual(1);
                    // So a long filter is cut short
                    final Query longItem = screen.getByText(LONG_FILTER);
                    play.expect("long filter cut short", () ->
                                    ((Number) longItem.property("scrollWidth").get()).doubleValue()
                                    > ((Number) longItem.property("clientWidth").get()).doubleValue())
                            .toBe(true);
                    // The history is fetched for the users' context, not the screen
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(FETCH_PATH)
                                    .withJsonBodyContaining("{\"context\": \"users\"}")
                                    .toSpyMatcher());
                    play.click(screen.getByText(RECENT_FILTER));
                    play.expect(play.getByPlaceholderText(StroomDom.QUICK_FILTER_PLACEHOLDER))
                            .toHaveValue(RECENT_FILTER);
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(FIND_PATH)
                                    .withJsonBodyContaining("{\"quickFilter\": \"" + RECENT_FILTER + "\"}")
                                    .toSpyMatcher()));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(RECORD_PATH)
                                    .withJsonBodyContaining("{\"key\": {\"context\": \"users\"}, "
                                                            + "\"filterText\": \"" + RECENT_FILTER + "\"}")
                                    .toSpyMatcher()));
                    expectNoProblems(play);
                })
                // A user with no history for this quick filter is told so; Advanced Query is still
                // offered, as this filter has fields
                .story("NoRecentFilters", context -> render(context, fixtures(RestReply.json("[]"))))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Alice Anderson");
                    play.click(play.getByTitle("Show Recent Filters"));
                    play.expect(screen.findByText("No recent filters")).toBeInTheDocument();
                    play.expect(screen.getByText("Advanced Query...")).toBeInTheDocument();
                    expectNoProblems(play);
                })
                // History that fails to load is not worth an alert: the menu says so instead
                .story("RecentFiltersFailToLoad", context -> render(context, fixtures(
                        RestReply.error(500, "Unable to fetch the history"))))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Alice Anderson");
                    play.click(play.getByTitle("Show Recent Filters"));
                    play.expect(screen.findByText("Could not load recent filters")).toBeInTheDocument();
                    expectNoProblems(play);
                })
                // A filter the server rejects comes back as an empty page, like one that matched
                // nothing, so the box says why; it isn't remembered
                .story("FilterRejected", context -> render(context, fixtures(RestReply.json("[]"))))
                .withPlay(play -> {
                    final Query filter = play.getByPlaceholderText(StroomDom.QUICK_FILTER_PLACEHOLDER);
                    play.findByText("Alice Anderson");
                    play.type(filter, REJECTED_FILTER + "{Enter}");
                    play.waitFor(() -> play.expect(play.queryByText("Alice Anderson")).toBeNull());
                    play.expect(filter).toHaveAttribute("aria-invalid", "true");
                    play.expect(filter).toHaveAttribute("title", REJECTION);
                    play.click(play.getByTitle("Quick Filter Syntax Help"));
                    play.expect(play.screen().findByText("This filter was not applied")).toBeInTheDocument();
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.post(RECORD_PATH).toSpyMatcher());
                    expectNoProblems(play);
                })
                // Advanced Query opens the filter as an expression tree. OK without an edit leaves
                // the text as it was, though the tree is formatted again to tell
                .story("AdvancedQueryUnchanged", context -> render(context, fixtures(RestReply.json("[]"))))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Query filter = play.getByPlaceholderText(StroomDom.QUICK_FILTER_PLACEHOLDER);
                    play.findByText("Alice Anderson");
                    play.type(filter, RECENT_FILTER + "{Enter}");
                    play.click(play.getByTitle("Show Recent Filters"));
                    play.click(screen.findByText("Advanced Query..."));
                    final Play dialog = screen.within(
                            screen.findByText("Advanced Query", StroomDom.DIALOG_TITLE).closest(StroomDom.DIALOG));
                    // The term, as the expression tree shows it
                    play.expect(dialog.findByText(TextMatch.containing("alice"))).toBeInTheDocument();
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(screen.queryByText("Advanced Query", StroomDom.DIALOG_TITLE))
                            .toBeNull());
                    play.expect(filter).toHaveValue(RECENT_FILTER);
                    // Once when it opened, and once on OK
                    final Spy requests = play.spy(ScreenHarness.REQUEST_SPY);
                    play.expect("formats", () -> formatCount(requests)).toBe(2);
                    play.expect(screen.queryByText("Replace the filter text?")).toBeNull();
                    expectNoProblems(play);
                })
                // A filter the tree can't show opens an empty tree, saying so (and that OK will
                // replace the text). OK then replaces it, with no confirmation
                .story("AdvancedQueryUnconvertible", context -> render(context, fixtures(RestReply.json("[]"))))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Query filter = play.getByPlaceholderText(StroomDom.QUICK_FILTER_PLACEHOLDER);
                    play.findByText("Alice Anderson");
                    play.type(filter, REJECTED_FILTER + "{Enter}");
                    play.click(play.getByTitle("Show Recent Filters"));
                    play.click(screen.findByText("Advanced Query..."));
                    final Play dialog = screen.within(
                            screen.findByText("Advanced Query", StroomDom.DIALOG_TITLE).closest(StroomDom.DIALOG));
                    play.expect(dialog.findByText(TextMatch.startingWith("The current filter could not be converted")))
                            .toBeInTheDocument();
                    play.expect(dialog.getByText(TextMatch.containing(REJECTION))).toBeInTheDocument();
                    play.expect(dialog.getByText(TextMatch.containing("OK will replace the filter text")))
                            .toBeInTheDocument();
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(screen.queryByText("Advanced Query", StroomDom.DIALOG_TITLE))
                            .toBeNull());
                    play.expect(screen.queryByText("Replace the filter text?")).toBeNull();
                    play.expect(filter).toHaveValue("");
                    play.findByText("Alice Anderson");
                    expectNoProblems(play);
                });
    }

    // A filter of 20 terms, about 300 characters
    private static String longFilter() {
        final StringBuilder sb = new StringBuilder("display:user01");
        for (int i = 2; i <= 20; i++) {
            sb.append(" or display:user").append(i < 10
                    ? "0"
                    : "").append(i);
        }
        return sb.toString();
    }

    // The users list, the history (answered with the given reply), and the expression parse and
    // format for RECENT_FILTER and REJECTED_FILTER
    private static RestFixtures fixtures(final RestReply history) {
        return RestFixtures.builder()
                .route(RequestMatcher.post(FIND_PATH)
                                .withJsonBodyContaining("{\"quickFilter\": \"" + REJECTED_FILTER + "\"}"),
                        RestReply.json(REJECTED))
                .post(FIND_PATH, RestReply.json(USERS))
                .post(FETCH_PATH, history)
                .post(RECORD_PATH, RestReply.noContent())
                .route(RequestMatcher.post(PARSE_PATH)
                                .withJsonBodyContaining("{\"text\": \"" + RECENT_FILTER + "\"}"),
                        RestReply.json(PARSED))
                .post(PARSE_PATH, RestReply.json(
                        "{\"error\": {\"text\": \"" + REJECTION + "\", \"severity\": \"ERROR\"}}"))
                .route(RequestMatcher.post(FORMAT_PATH).withBodyContaining("\"alice\""),
                        RestReply.json("{\"text\": \"" + RECENT_FILTER + "\"}"))
                .post(FORMAT_PATH, RestReply.json("{\"text\": \"\"}"))
                .build();
    }

    private static Widget render(final StoryContext context, final RestFixtures fixtures) {
        final AppScreenGinjector injector = GWT.create(AppScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures)
                .injector(injector)
                .appPermissions(AppPermission.MANAGE_USERS_PERMISSION)
                // The Advanced Query dialog's confirmation is shown in Stroom's real dialog
                .realAlerts()
                .build();
        // As UsersPlugin.open opens it, refreshed
        harness.show(injector.getUsersController()).refresh();
        return harness.asWidget();
    }

    // The number of formatQuickFilter requests the spy recorded
    private static int formatCount(final Spy requests) {
        int count = 0;
        for (final List<Object> call : requests.getCalls()) {
            if (String.valueOf(call.get(0)).startsWith("POST " + FORMAT_PATH)) {
                count++;
            }
        }
        return count;
    }

    private static void expectNoProblems(final Play play) {
        play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
        play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
    }
}
