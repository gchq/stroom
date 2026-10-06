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
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.rest.StartupFixtures;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.receive.rules.client.presenter.RuleSetPresenter;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;
import java.util.Map;

/// Stories matching `App/Main/DataReceiptRulesScreen` in the React Storybook, showing Stroom's
/// real [RuleSetPresenter] (the 'Data Receipt Rules' tab, as `ReceiveDataRuleSetPlugin` opens it:
/// the Rules, Fields and Documentation sub-tabs) with fake REST replies.
///
/// | React | Stroom |
/// |---|---|
/// | `fetch` | `GET /ruleset/v2` |
/// | `update` | `PUT /ruleset/v2` (echoes the rules) |
/// | `uiConfig.obfuscatedFields` | the extended UI config's `obfuscatedFields` |
///
/// The recorder's checks become checks on the request spy.
public final class DataReceiptRulesScreenStories {

    private static final String RULES_PATH = "/ruleset/v2";

    private static final String RULES = """
            {
              "type": "ReceiveDataRuleSet", "uuid": "rules-1", "name": "Rules", "version": "v1",
              "description": "Receipt policy.",
              "fields": [
                {"fldName": "Feed", "fldType": "TEXT", "conditionSet": "RECEIPT_POLICY_CONDITIONS"},
                {"fldName": "System", "fldType": "TEXT", "conditionSet": "RECEIPT_POLICY_CONDITIONS"}
              ],
              "rules": [
                {"ruleNumber": 1, "name": "Reject test feeds", "enabled": true, "action": "REJECT",
                  "creationTime": 1700000000000,
                  "expression": {"type": "operator", "op": "AND", "children": [
                    {"type": "term", "field": "Feed", "condition": "CONTAINS", "value": "TEST"}]}}
              ]
            }""";

    private static final RestFixtures FIXTURES = RestFixtures.builder()
            .get(RULES_PATH, RestReply.json(RULES))
            .put(RULES_PATH, request -> RestReply.json(request.getBody()))
            .build();

    // The extended UI config (as StartupFixtures builds it) with Feed obfuscated
    private static final String OBFUSCATING_UI_CONFIG = "{\"uiConfig\":" + StartupFixtures.DEFAULT_UI_CONFIG
            + ",\"externalIdentityProvider\":false,\"dependencyWarningsEnabled\":false"
            + ",\"maxApiKeyExpiryAgeMs\":31536000000,\"obfuscatedFields\":[\"Feed\"]"
            + ",\"receiptCheckMode\":\"FEED_STATUS\",\"lastAnnotationChangeTime\":0}";

    private static final String SAVE = "Save all rules";
    // Differs from React: the expression panel has no class of its own; it is the expression tree's
    // view (ExpressionTreeViewImpl)
    private static final String EXPRESSION_PANEL = ".ExpressionTreeViewImpl-layoutPanel";

    private DataReceiptRulesScreenStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/DataReceiptRulesScreen", DataReceiptRulesScreenStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Saving a rule using an obfuscated field with a condition that can't be obfuscated warns
                .story("ObfuscationWarning", context -> render(context, true))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.findByText("Reject test feeds"));
                    play.click(play.getByTitle("Edit selected rule"));
                    final Play dialog = dialog(screen, "Edit Rule");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(screen.getByText(
                            TextMatch.containing("not compatible with obfuscated fields"))).toBeInTheDocument());
                    expectNoProblems(play);
                })
                // The selected rule's expression shows read only under the grid
                .story("ExpressionPanel", context -> render(context, false))
                .withPlay(play -> {
                    play.findByText("Reject test feeds");
                    final Play panel = play.within(play.querySelector(EXPRESSION_PANEL));
                    // Nothing selected -> no term is shown
                    play.expect(panel.queryByText(TextMatch.containingIgnoreCase("Feed"))).toBeNull();
                    // Select the rule -> its expression shows, read only
                    play.click(play.getByText("Reject test feeds"));
                    play.waitFor(() -> play.expect(panel.getByText(TextMatch.regex("Feed\\s+contains\\s+TEST", "i")))
                            .toHaveClass("expressionItemBox-label"));
                    play.expect(panel.queryByDisplayValue("TEST")).toBeNull();
                    expectNoProblems(play);
                })
                // The rule set loads; adding a rule dirties and saves the document
                .story("Rules", context -> render(context, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Reject test feeds");
                    play.expect(play.getByText("Reject")).toBeInTheDocument();
                    // GWT gives the headings tool tips (RuleSetListPresenter)
                    play.expect(play.getByTitle(
                            "The number of the rule. Rules are evaluated in ascending order of rule number."))
                            .toBeInTheDocument();
                    play.expect(play.getByTitle(TextMatch.startingWith(
                            "Whether this rule is enabled or disabled. A disabled rule will be ignored")))
                            .toBeInTheDocument();
                    // ...and no heading's tool tip just repeats its label
                    final Query headerRow = play.querySelector("thead");
                    play.expect("a heading whose tool tip is its label", () -> headingEchoingItself(headerRow))
                            .toBe(false);
                    // Add a rule -> "Edit Rule" dialog (GWT's caption for an add too) -> name -> OK
                    play.click(play.getByTitle("Add new rule"));
                    final Play dialog = dialog(screen, "Edit Rule");
                    // Differs from React: the field's id is its FormGroup's identity
                    play.type(dialog.querySelector("#ruleRuleName"), "Drop everything else");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.findByText("Drop everything else");
                    // Save -> PUT the whole document with the new rule, inserted at the top as rule 1
                    final Query save = play.getByTitle(SAVE);
                    play.waitFor(() -> play.expect(save).not().toHaveClass("disabled"));
                    play.click(save);
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put(RULES_PATH)
                                    .withBody("'Drop everything else' as rule 1",
                                            DataReceiptRulesScreenStories::hasNewFirstRule)
                                    .toSpyMatcher()));
                    expectNoProblems(play);
                })
                // The Fields tab: Add Field -> dialog (Name + Type) -> the field is added and saved
                .story("AddField", context -> render(context, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Reject test feeds");
                    // Differs from React: GWT's tabs are link tabs with no 'tab' role
                    play.click(play.getByText("Fields", StroomDom.LINK_TAB_LABEL));
                    play.findByText("System");
                    // The field columns are sortable, sorted by name ascending to start with.
                    // Differs from React: GWT has no aria-sort; the sorted header shows a sort icon
                    final Query nameHeader = play.getByText("Name", "th *").closest("th");
                    final Query grid = nameHeader.closest(".dataGridWidget");
                    play.expect(play.within(nameHeader).querySelector(".column-sortIcon")).not().toBeNull();
                    play.expect("the field order", () -> fieldOrder(grid.element().get()))
                            .toBe("Feed,System");
                    play.click(nameHeader);
                    play.waitFor(() -> play.expect("the field order", () -> fieldOrder(grid.element().get()))
                            .toBe("System,Feed"));
                    // Differs from React: the button is titled 'New Field', the dialog 'New Field'
                    play.click(play.getByTitle("New Field"));
                    final Play dialog = dialog(screen, "New Field");
                    play.type(dialog.querySelector("#fieldEditName"), "Environment");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.findByText("Environment");
                    final Query save = play.getByTitle(SAVE);
                    play.waitFor(() -> play.expect(save).not().toHaveClass("disabled"));
                    play.click(save);
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put(RULES_PATH)
                                    .withBody("a field 'Environment'", DataReceiptRulesScreenStories::hasEnvironment)
                                    .toSpyMatcher()));
                    expectNoProblems(play);
                });
    }

    private static Play dialog(final Play screen, final String caption) {
        return screen.within(screen.findByText(caption, StroomDom.DIALOG_TITLE).closest(StroomDom.DIALOG));
    }

    // The names of the fields, in the order the grid shows them
    private static String fieldOrder(final Element container) {
        if (container == null) {
            return null;
        }
        final String text = container.getInnerText();
        final int feed = text.indexOf("Feed");
        final int system = text.indexOf("System");
        if (feed < 0 || system < 0) {
            return null;
        }
        return feed < system
                ? "Feed,System"
                : "System,Feed";
    }

    private static boolean headingEchoingItself(final Query headerRow) {
        final Element thead = headerRow.element().get();
        if (thead == null) {
            return false;
        }
        final NodeList<Element> titled = thead.getElementsByTagName("*");
        for (int i = 0; i < titled.getLength(); i++) {
            final Element element = titled.getItem(i);
            final String title = element.getTitle();
            if (title != null && !title.isEmpty() && title.equals(element.getInnerText().trim())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasNewFirstRule(final String body) {
        final List<?> rules = list(body, "rules");
        if (rules.isEmpty()) {
            return false;
        }
        for (final Object rule : rules) {
            final Map<?, ?> map = (Map<?, ?>) rule;
            if ("Drop everything else".equals(map.get("name"))) {
                return JsonValues.parse("1").equals(map.get("ruleNumber"))
                       || "1".equals(String.valueOf(map.get("ruleNumber")));
            }
        }
        return false;
    }

    private static boolean hasEnvironment(final String body) {
        for (final Object field : list(body, "fields")) {
            if ("Environment".equals(((Map<?, ?>) field).get("fldName"))) {
                return true;
            }
        }
        return false;
    }

    private static List<?> list(final String body, final String name) {
        if (body == null) {
            return List.of();
        }
        final Object value = ((Map<?, ?>) JsonValues.parse(body)).get(name);
        return value instanceof List
                ? (List<?>) value
                : List.of();
    }

    private static void expectNoProblems(final Play play) {
        play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
        play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
    }

    private static Widget render(final StoryContext context, final boolean obfuscateFeed) {
        final ProcessingScreenGinjector injector = GWT.create(ProcessingScreenGinjector.class);
        final ScreenHarness.Builder builder = ScreenHarness.builder(context, FIXTURES)
                .injector(injector);
        if (obfuscateFeed) {
            // React's nested AppContextProvider with uiConfig.obfuscatedFields; the confirmation is
            // Stroom's real dialog
            builder.startup(startup -> startup.extendedUiConfig(OBFUSCATING_UI_CONFIG)).realAlerts();
        }
        final ScreenHarness harness = builder.build();
        harness.afterStartUp(() -> {
            final RuleSetPresenter presenter = injector.getRuleSetPresenter();
            harness.addContent(presenter);
        });
        return harness.asWidget();
    }
}
