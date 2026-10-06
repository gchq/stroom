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

import stroom.gwt.workbench.client.app.gin.processing.ProcessingScreenGinjector;
import stroom.gwt.workbench.client.app.rest.JsonValues;
import stroom.gwt.workbench.client.app.rest.RecordedRequest;
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
import stroom.receive.rules.client.presenter.DataRetentionPresenter;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/// Stories matching `App/Main/DataRetentionScreen` in the React Storybook, showing Stroom's real
/// [DataRetentionPresenter] (the 'Data Retention' tab, as `DataRetentionPlugin` opens it: the Rules
/// and Impact Summary sub-tabs) with fake REST replies.
///
/// | React | Stroom |
/// |---|---|
/// | `fetch` | `GET /dataRetentionRules/v1` |
/// | `update` | `PUT /dataRetentionRules/v1` (echoes the rules) |
/// | `impactSummary` | `POST /dataRetentionRules/v1/impactSummary` (echoes the request's `queryId`) |
/// | `stopImpactSummary` | `DELETE /dataRetentionRules/v1/impactSummary/{queryId}` |
///
/// The recorder's checks become checks on the request spy.
public final class DataRetentionScreenStories {

    private static final String RULES_PATH = "/dataRetentionRules/v1";
    private static final String IMPACT_PATH = "/dataRetentionRules/v1/impactSummary";

    private static final String RULES = """
            {
              "type": "DataRetentionRules", "uuid": "ret-1", "name": "Retention", "version": "v1",
              "rules": [
                {"ruleNumber": 1, "name": "Keep 30 days", "enabled": true, "forever": false, "age": 30,
                  "timeUnit": "DAYS", "creationTime": 1700000000000,
                  "expression": {"type": "operator", "op": "AND", "children": [
                    {"type": "term", "field": "Feed", "condition": "CONTAINS", "value": "TEST"}]}},
                {"ruleNumber": 2, "name": "Keep 5 years", "enabled": true, "forever": false, "age": 5,
                  "timeUnit": "YEARS", "creationTime": 1700000000001,
                  "expression": {"type": "operator", "op": "AND", "children": [
                    {"type": "term", "field": "Feed", "condition": "CONTAINS", "value": "LONG"}]}}
              ]
            }""";

    private static final String SUMMARIES = """
            [
              {"ruleNumber": 1, "ruleName": "Keep 30 days", "feed": "TEST_FEED", "type": "Raw Events", "count": 42},
              {"ruleNumber": 1, "ruleName": "Keep 30 days", "feed": "OTHER_FEED", "type": "Raw Events", "count": 8}
            ]""";

    private static final String SORTING_SUMMARIES = """
            [
              {"ruleNumber": 2, "ruleName": "Keep 5 years", "feed": "LONG_FEED", "type": "Raw Events", "count": 1},
              {"ruleNumber": 1, "ruleName": "Keep 30 days", "feed": "TEST_FEED", "type": "Raw Events", "count": 42}
            ]""";

    private static final RestFixtures FIXTURES = fixtures(SUMMARIES, 0);
    private static final RestFixtures SORTING_FIXTURES = fixtures(SORTING_SUMMARIES, 0);
    // The query is held open so the running state can be seen and aborted
    private static final RestFixtures ABORT_FIXTURES = fixtures(
            "[{\"ruleNumber\": 1, \"ruleName\": \"Keep 30 days\", \"feed\": \"LATE\", \"type\": \"Raw Events\", "
                    + "\"count\": 99}]",
            1500);

    // The Impact Summary's toolbar buttons
    private static final String RUN_QUERY = "Run Query";
    private static final String ABORT_QUERY = "Abort Query";
    private static final String SET_FILTER = "Set Query Filter";

    // Differs from React: the expression panel has no class of its own; it is the expression tree's
    // view (ExpressionTreeViewImpl)
    private static final String EXPRESSION_PANEL = ".ExpressionTreeViewImpl-layoutPanel";

    private DataRetentionScreenStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/DataRetentionScreen", DataRetentionScreenStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Rules load (incl. the synthetic retain-all row); editing a rule dirties and saves
                .story("Retention", context -> render(context, FIXTURES))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Keep 30 days");
                    play.expect(play.getByText("30 Days")).toBeInTheDocument();
                    // The synthetic backend-default rule is shown at the bottom
                    play.expect(play.getByText("Default Retain All Forever Rule")).toBeInTheDocument();
                    play.expect(play.getByText("Forever")).toBeInTheDocument();
                    // Double click the user rule -> "Edit Rule" -> rename -> OK
                    play.dblClick(play.getByText("Keep 30 days"));
                    final Play dialog = dialog(screen, "Edit Rule");
                    // Differs from React: the field's id is its FormGroup's identity
                    final Query name = dialog.querySelector("#dataRetentionRuleRuleName");
                    play.clear(name);
                    play.type(name, "Keep a month");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.findByText("Keep a month");
                    // Save -> PUT the rules (the retain-all rule excluded) with the renamed rule
                    final Query save = play.getByTitle("Save rules");
                    play.waitFor(() -> play.expect(save).not().toHaveClass("disabled"));
                    play.click(save);
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put(RULES_PATH)
                                    .withBody("two rules, the first 'Keep a month'",
                                            DataRetentionScreenStories::hasRenamedFirstRule)
                                    .toSpyMatcher()));
                    expectNoProblems(play);
                })
                // The selected rule's expression shows read only under the grid
                .story("ExpressionPanel", context -> render(context, FIXTURES))
                .withPlay(play -> {
                    play.findByText("Keep 30 days");
                    // Differs from React: the panel is the ExpressionTreePresenter's view under the
                    // grid (DataRetentionPolicyViewImpl's split panel), with no class of its own
                    final Play panel = play.within(play.querySelector(EXPRESSION_PANEL));
                    // Nothing selected -> no term is shown
                    play.expect(panel.queryByText(TextMatch.containingIgnoreCase("Feed"))).toBeNull();
                    // Select the rule -> its expression shows, read only (labels, not editors)
                    play.click(play.getByText("Keep 30 days"));
                    play.waitFor(() -> play.expect(panel.getByText(TextMatch.regex("Feed\\s+contains\\s+TEST", "i")))
                            .toHaveClass("expressionItemBox-label"));
                    play.expect(panel.queryByDisplayValue("TEST")).toBeNull();
                    // The synthetic retain-all rule has no expression -> the panel clears
                    play.click(play.getByText("Default Retain All Forever Rule"));
                    play.waitFor(() -> play.expect(panel.queryByText(TextMatch.containingIgnoreCase("Feed")))
                            .toBeNull());
                    expectNoProblems(play);
                })
                // The row's action menu acts on its row; the retain-all rule offers only two items
                .story("RowActionMenu", context -> render(context, FIXTURES))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Keep 30 days");
                    play.click(play.within(row(play, "Keep 30 days")).getByTitle(StroomDom.ACTIONS_TITLE));
                    play.waitFor(() -> play.expect(menuItem(screen, "Edit Rule")).toBeVisible());
                    for (final String label : new String[]{
                            "Add new rule above", "Add new rule below", "Edit Rule", "Copy Rule", "Delete Rule"}) {
                        play.expect(menuItem(screen, label)).toBeVisible();
                    }
                    // Row 1 of 2: no Move Up, but Move Down
                    play.expect(screen.queryByText("Move Rule Up", StroomDom.MENU_ITEM_TEXT)).toBeNull();
                    play.expect(menuItem(screen, "Move Rule Down")).toBeVisible();
                    // Copy Rule acts on that row -> a second rule appears
                    play.click(menuItem(screen, "Copy Rule"));
                    play.waitFor(() -> play.expect(play.getAllByText("Keep 30 days").count()).toBe(2));
                    // The default retain-all row: only Add above and Copy
                    play.click(play.within(row(play, "Default Retain All Forever Rule"))
                            .getByTitle(StroomDom.ACTIONS_TITLE));
                    play.waitFor(() -> play.expect(menuItem(screen, "Add new rule above")).toBeVisible());
                    play.expect(menuItem(screen, "Copy Rule")).toBeVisible();
                    play.expect(screen.queryByText("Edit Rule", StroomDom.MENU_ITEM_TEXT)).toBeNull();
                    play.expect(screen.queryByText("Delete Rule", StroomDom.MENU_ITEM_TEXT)).toBeNull();
                    play.expect(screen.queryByText("Add new rule below", StroomDom.MENU_ITEM_TEXT)).toBeNull();
                    expectNoProblems(play);
                })
                // The Impact Summary tab: Run Query fetches the per-rule deletion counts
                .story("ImpactSummary", context -> render(context, FIXTURES))
                .withPlay(play -> {
                    openImpactSummary(play);
                    play.click(play.getByTitle(RUN_QUERY));
                    // Differs from React: GWT's nested view (its default) shows the feeds once
                    // expanded; the rules start expanded down to their types
                    play.click(play.findByTitle("Expand all"));
                    play.findByText("TEST_FEED");
                    play.expect(play.getByText("42")).toBeInTheDocument();
                    expectNoProblems(play);
                })
                // A query in flight disables Run/Filter and enables Abort, which cancels it by queryId
                .story("ImpactAbort", context -> render(context, ABORT_FIXTURES))
                .withPlay(play -> {
                    openImpactSummary(play);
                    // Idle: Run enabled, Abort disabled
                    play.expect(play.getByTitle(ABORT_QUERY)).toHaveClass("disabled");
                    play.expect(play.getByTitle(RUN_QUERY)).not().toHaveClass("disabled");
                    // In flight: the gating inverts (updateButtonStates)
                    play.click(play.getByTitle(RUN_QUERY));
                    play.waitFor(() -> play.expect(play.getByTitle(ABORT_QUERY)).not().toHaveClass("disabled"));
                    play.expect(play.getByTitle(RUN_QUERY)).toHaveClass("disabled");
                    play.expect(play.getByTitle(SET_FILTER)).toHaveClass("disabled");
                    // Abort cancels the same queryId the run was issued with
                    play.click(play.getByTitle(ABORT_QUERY));
                    final Spy requests = play.spy(ScreenHarness.REQUEST_SPY);
                    play.waitFor(() -> play.expect("the query ids run and cancelled",
                                    () -> runAndCancelledIds(requests))
                            .toSatisfy("one run, cancelled by its id", DataRetentionScreenStories::isCancelledRun));
                    play.waitFor(() -> play.expect(play.getByTitle(ABORT_QUERY)).toHaveClass("disabled"));
                    play.expect(play.getByTitle(RUN_QUERY)).not().toHaveClass("disabled");
                    // The aborted run's late response must not fill the cleared table
                    play.sleep(1600);
                    play.expect(play.queryByText("LATE")).toBeNull();
                    expectNoProblems(play);
                })
                // The Impact grid sorts the Rule Age column by duration, not by its text
                .story("ImpactSorting", context -> render(context, SORTING_FIXTURES))
                .withPlay(play -> {
                    openImpactSummary(play);
                    play.click(play.getByTitle(RUN_QUERY));
                    // Differs from React: GWT's default nested view shows the feeds once expanded
                    play.click(play.findByTitle("Expand all"));
                    play.findByText("LONG_FEED");
                    // Ascending by Rule Age -> 30 Days (rule 1) before 5 Years (rule 2)
                    final Query rows = play.querySelector("tbody");
                    play.click(play.getByText("Rule Age"));
                    play.waitFor(() -> play.expect("the first feed", () -> firstFeed(rows)).toBe("TEST"));
                    // Descending flips it
                    play.click(play.getByText("Rule Age"));
                    play.waitFor(() -> play.expect("the first feed", () -> firstFeed(rows)).toBe("LONG"));
                    expectNoProblems(play);
                })
                // The nested view groups by Rule -> Type -> Feed with subtotals
                .story("ImpactNested", context -> render(context, FIXTURES))
                .withPlay(play -> {
                    openImpactSummary(play);
                    play.click(play.getByTitle(RUN_QUERY));
                    // Differs from React: GWT's Impact Summary starts nested (isTableNested = true),
                    // so the toggle isn't needed; the rule and type rows show the subtotal (42 + 8)
                    play.waitFor(() -> play.expect(play.getAllByText("50").count())
                            .toBeGreaterThanOrEqual(1));
                    // The full column set, behind an expander column.
                    // Differs from React: GWT's sortable headers have role="button", so the headers
                    // are the table's <th> cells
                    play.expect(play.querySelectorAll("thead th").textContents()).toEqual(List.of(
                            "", "Rule No.", "Rule Name", "Rule Age", "Type", "Feed", "Stream Delete Count"));
                    // An active rule with no deletes still shows, as a 0 count row.
                    // Differs from React: GWT builds the rows from the fetched rules' active rules,
                    // which don't include the UI only 'Default Retain All Forever Rule', so its 0 count
                    // row is 'Keep 5 years' (an active rule nothing is deleted by)
                    play.expect(play.queryByText("Default Retain All Forever Rule")).toBeNull();
                    play.expect(play.getByText("Keep 5 years")).toBeInTheDocument();
                    // Expand all -> the feed rows show
                    play.click(play.getByTitle("Expand all"));
                    play.findByText("TEST_FEED");
                    play.expect(play.getByText("OTHER_FEED")).toBeInTheDocument();
                    expectNoProblems(play);
                })
                // Applying a filter sets the criteria the next run sends
                .story("ImpactFilter", context -> render(context, FIXTURES))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    openImpactSummary(play);
                    play.click(play.getByTitle(SET_FILTER));
                    play.click(dialog(screen, "Query Filter").getByRole("button", StroomDom.button("OK")));
                    // Differs from React: GWT's filter only sets the criteria (it doesn't re-run the
                    // query, and there is no 'Clear Filter' button); the next Run sends it
                    play.click(play.getByTitle(RUN_QUERY));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(IMPACT_PATH)
                                    .withJsonBodyContaining(
                                            "{\"criteria\": {\"expression\": {\"type\": \"operator\"}}}")
                                    .toSpyMatcher()));
                    play.click(play.findByTitle("Expand all"));
                    play.findByText("TEST_FEED");
                    play.expect(play.queryByTitle("Clear Filter")).toBeNull();
                    expectNoProblems(play);
                });
    }

    private static void openImpactSummary(final Play play) {
        play.findByText("Keep 30 days");
        // Differs from React: GWT's tabs are link tabs with no 'tab' role
        play.click(play.getByText("Impact Summary", StroomDom.LINK_TAB_LABEL));
        play.findByTitle(RUN_QUERY);
    }

    // Differs from React: GWT's grid rows are <tr> elements with no role attribute
    private static Query row(final Play play, final String text) {
        return play.getAllByText(text).nth(0).closest("tr");
    }

    private static Query menuItem(final Play screen, final String text) {
        return screen.getByText(text, StroomDom.MENU_ITEM_TEXT);
    }

    private static Play dialog(final Play screen, final String caption) {
        return screen.within(screen.findByText(caption, StroomDom.DIALOG_TITLE).closest(StroomDom.DIALOG));
    }

    // Which of the two feeds is in the first grid row that names a feed
    private static String firstFeed(final Query rows) {
        final Element body = rows.element().get();
        final String text = body == null
                ? ""
                : body.getInnerText();
        final int test = text.indexOf("TEST_FEED");
        final int longFeed = text.indexOf("LONG_FEED");
        if (test < 0 || longFeed < 0) {
            return null;
        }
        return test < longFeed
                ? "TEST"
                : "LONG";
    }

    // The query id of each impact summary run, followed by the id of each cancel
    private static List<String> runAndCancelledIds(final Spy requests) {
        final List<String> ids = new ArrayList<>();
        for (final List<Object> call : requests.getCalls()) {
            final RecordedRequest request = RecordedRequest.parse(String.valueOf(call.get(0)));
            if ("POST".equals(request.getMethod()) && IMPACT_PATH.equals(request.getPath())) {
                ids.add(String.valueOf(((Map<?, ?>) JsonValues.parse(request.getBody())).get("queryId")));
            } else if ("DELETE".equals(request.getMethod()) && request.getPath().startsWith(IMPACT_PATH + "/")) {
                ids.add(request.getPath().substring(IMPACT_PATH.length() + 1));
            }
        }
        return ids;
    }

    // One run, cancelled with the run's query id
    private static boolean isCancelledRun(final Object ids) {
        final List<?> list = (List<?>) ids;
        return list.size() == 2 && list.get(0) != null && list.get(0).equals(list.get(1));
    }

    private static boolean hasRenamedFirstRule(final String body) {
        if (body == null) {
            return false;
        }
        final Object rules = ((Map<?, ?>) JsonValues.parse(body)).get("rules");
        return rules instanceof List
               && ((List<?>) rules).size() == 2
               && "Keep a month".equals(((Map<?, ?>) ((List<?>) rules).get(0)).get("name"));
    }

    private static void expectNoProblems(final Play play) {
        play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
        play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
    }

    // The summary reply echoes the request's (client minted) query id
    private static RestFixtures fixtures(final String summaries, final int delayMillis) {
        return RestFixtures.builder()
                .get(RULES_PATH, RestReply.json(RULES))
                .put(RULES_PATH, request -> RestReply.json(request.getBody()))
                .post(IMPACT_PATH, request -> {
                    final Object queryId = ((Map<?, ?>) JsonValues.parse(request.getBody())).get("queryId");
                    return RestReply.json("{\"queryId\": \"" + queryId + "\", \"values\": " + summaries + "}")
                            .delayed(delayMillis);
                })
                .delete(IMPACT_PATH + "/*", RestReply.json("true"))
                .build();
    }

    private static Widget render(final StoryContext context, final RestFixtures fixtures) {
        final ProcessingScreenGinjector injector = GWT.create(ProcessingScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures)
                .injector(injector)
                .build();
        harness.afterStartUp(() -> harness.addContent(injector.getDataRetentionPresenter()));
        return harness.asWidget();
    }
}
