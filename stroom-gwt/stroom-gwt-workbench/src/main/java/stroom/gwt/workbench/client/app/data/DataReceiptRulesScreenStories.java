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

package stroom.gwt.workbench.client.app.data;

import stroom.core.client.HasSave;
import stroom.docref.DocRef;
import stroom.document.client.DocumentTabData;
import stroom.document.client.event.SaveDocumentEvent;
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
import stroom.receive.rules.client.component.RuleSetTabController;
import stroom.svg.shared.SvgImage;
import stroom.widget.component.client.ContextValue;
import stroom.widget.component.client.Contexts;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;
import java.util.Map;

/// Stories of `App/Main/DataReceiptRulesScreen`, showing Stroom's real [RuleSetTabController] (the
/// 'Data Receipt Rules' tab, as `ReceiveDataRuleSetPlugin` opens it: the Rules, Fields and
/// Documentation sub-tabs) with fake REST replies.
///
/// | Stroom endpoint | Used for |
/// |---|---|
/// | `GET /ruleset/v2` | the rules |
/// | `PUT /ruleset/v2` (echoes the rules) | saving the rules |
/// | the extended UI config's `obfuscatedFields` | the fields to obfuscate |
///
/// The requests made are checked on the request spy.
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

    private static final String SAVE = "Save Rules";
    // The expression panel is the expression tree (ExpressionTree)
    private static final String EXPRESSION_PANEL = ".expression-tree";

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
                    play.click(play.getByTitle("Edit Selected Rule"));
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
                            .toHaveClass("expression-item__label"));
                    play.expect(panel.queryByDisplayValue("TEST")).toBeNull();
                    expectNoProblems(play);
                })
                // The rule set loads; adding a rule dirties and saves the document
                .story("Rules", context -> render(context, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Reject test feeds");
                    play.expect(play.getByText("Reject")).toBeInTheDocument();
                    // GWT gives the headings tool tips (RuleSetList)
                    play.expect(play.getByTitle(
                            "The number of the rule. Rules are evaluated in ascending order of rule number."))
                            .toBeInTheDocument();
                    play.expect(play.getByTitle(TextMatch.startingWith(
                            "Whether this rule is enabled or disabled. A disabled rule will be ignored")))
                            .toBeInTheDocument();
                    // ...and no heading's tool tip just repeats its label
                    final Query headerRow = play.querySelector("thead, .data-grid__head");
                    play.expect("a heading whose tool tip is its label", () -> headingEchoingItself(headerRow))
                            .toBe(false);
                    // Add a rule -> "Edit Rule" dialog (GWT's caption for an add too) -> name -> OK
                    play.click(play.getByTitle("New Rule"));
                    final Play dialog = dialog(screen, "Edit Rule");
                    // The field's id is its FormGroup's identity
                    play.type(dialog.querySelector("#ruleRuleName"), "Drop everything else");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.findByText("Drop everything else");
                    // Save -> PUT the whole document with the new rule, inserted at the top as rule 1
                    final Query save = play.getByRole("button", SAVE);
                    play.waitFor(() -> play.expect(save).not().toHaveAttribute("aria-disabled"));
                    play.click(save);
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put(RULES_PATH)
                                    .withBody("'Drop everything else' as rule 1",
                                            DataReceiptRulesScreenStories::hasNewFirstRule)
                                    .toSpyMatcher()));
                    expectNoProblems(play);
                })
                // Saving another document (with changes) leaves the rules alone: they were once saved
                // whenever any dirty document was
                .story("SavingAnotherDocument", context -> render(context, false))
                .withPlay(play -> {
                    play.findByText("Reject test feeds");
                    play.run("save another document, with changes", () -> SaveDocumentEvent.fire(
                            ScreenHarness.<RuleSetTabController>shownComponent(), new OtherDocument()));
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.put(RULES_PATH).toSpyMatcher());
                    expectNoProblems(play);
                })
                // The Fields tab: Add Field -> dialog (Name + Type) -> the field is added and saved
                .story("AddField", context -> render(context, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Reject test feeds");
                    // GWT's tabs are link tabs with no 'tab' role
                    play.click(play.getByText("Fields", StroomDom.LINK_TAB_LABEL));
                    play.findByText("System");
                    // The field columns are sortable, sorted by name ascending to start with.
                    // GWT has no aria-sort; the sorted header shows a sort icon
                    final Query nameHeader = play.getByText("Name", StroomDom.GRID_HEADER_TEXT)
                            .closest(StroomDom.GRID_HEADER_CELL);
                    final Query grid = nameHeader.closest(".data-grid");
                    play.expect(play.within(nameHeader).querySelector(".sort-icon")).not().toBeNull();
                    play.expect("the field order", () -> fieldOrder(grid.element().get()))
                            .toBe("Feed,System");
                    play.click(nameHeader);
                    play.waitFor(() -> play.expect("the field order", () -> fieldOrder(grid.element().get()))
                            .toBe("System,Feed"));
                    // The button is titled 'New Field', the dialog 'New Field'
                    play.click(play.getByTitle("New Field"));
                    final Play dialog = dialog(screen, "New Field");
                    play.type(dialog.querySelector("#fieldEditName"), "Environment");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.findByText("Environment");
                    final Query save = play.getByRole("button", SAVE);
                    play.waitFor(() -> play.expect(save).not().toHaveAttribute("aria-disabled"));
                    play.click(save);
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put(RULES_PATH)
                                    .withBody("a field 'Environment'", DataReceiptRulesScreenStories::hasEnvironment)
                                    .toSpyMatcher()));
                    expectNoProblems(play);
                })
                // The field dialog (a component) checks the name on OK and stays open if it is
                // missing or another field's (ignoring case); Cancel closes it with nothing changed
                .story("AddFieldChecksTheName", context -> render(context, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Reject test feeds");
                    play.click(play.getByText("Fields", StroomDom.LINK_TAB_LABEL));
                    play.findByText("System");
                    play.click(play.getByTitle("New Field"));
                    final Play dialog = dialog(screen, "New Field");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.ALERT_SPY))
                            .toHaveBeenCalledWith("WARN: A field must have a name"));
                    play.expect(screen.getByText("New Field", StroomDom.DIALOG_TITLE)).toBeInTheDocument();
                    play.type(dialog.querySelector("#fieldEditName"), "feed");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.ALERT_SPY)).toHaveBeenCalledWith(
                            "WARN: A field with this name already exists. Field names are case insensitive."));
                    play.expect(screen.getByText("New Field", StroomDom.DIALOG_TITLE)).toBeInTheDocument();
                    play.click(dialog.getByRole("button", StroomDom.button("Cancel")));
                    play.waitFor(() -> play.expect(screen.queryByText("New Field", StroomDom.DIALOG_TITLE))
                            .toBeNull());
                    play.expect(play.getByRole("button", SAVE)).toHaveAttribute("aria-disabled", "true");
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                })
                // Cancel on the rule dialog (a component) keeps the rule as it was
                .story("EditRuleCancel", context -> render(context, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.findByText("Reject test feeds"));
                    play.click(play.getByTitle("Edit Selected Rule"));
                    final Play dialog = dialog(screen, "Edit Rule");
                    play.type(dialog.querySelector("#ruleRuleName"), " (changed)");
                    play.click(dialog.getByRole("button", StroomDom.button("Cancel")));
                    play.waitFor(() -> play.expect(screen.queryByText("Edit Rule", StroomDom.DIALOG_TITLE))
                            .toBeNull());
                    play.expect(play.getByText("Reject test feeds")).toBeInTheDocument();
                    play.expect(play.queryByText(TextMatch.containing("(changed)"))).toBeNull();
                    play.expect(play.getByRole("button", SAVE)).toHaveAttribute("aria-disabled", "true");
                    expectNoProblems(play);
                })
                // Duplicating a rule is a change, so it can be saved (it wasn't, before the Rules
                // tab was a component)
                .story("DuplicateRule", context -> render(context, false))
                .withPlay(play -> {
                    play.click(play.findByText("Reject test feeds"));
                    play.click(play.getByTitle("Duplicate Selected Rule"));
                    play.waitFor(() -> play.expect(play.getAllByText("Reject test feeds")).toHaveLength(2));
                    play.waitFor(() -> play.expect(play.getByRole("button", SAVE))
                            .not().toHaveAttribute("aria-disabled"));
                    expectNoProblems(play);
                })
                // Deleting the one rule selected asks about 'this rule' (it asked about 'the selected
                // rules'), and leaves nothing selected to edit
                .story("DeleteRule", context -> render(context, false, true, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.findByText("Reject test feeds"));
                    play.click(play.getByTitle("Delete Selected Rules"));
                    play.click(screen.within(screen.findByText("Are you sure you want to delete this rule?")
                            .closest(StroomDom.DIALOG)).getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.queryByText("Reject test feeds")).toBeNull());
                    play.expect(play.getByTitle("Edit Selected Rule")).toHaveAttribute("aria-disabled", "true");
                    play.expect(play.getByTitle("Delete Selected Rules")).toHaveAttribute("aria-disabled", "true");
                    play.waitFor(() -> play.expect(play.getByRole("button", SAVE))
                            .not().toHaveAttribute("aria-disabled"));
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                })
                // Read only from what shows it (here the story, through context): no rule can be
                // added, edited or deleted, and double-clicking a rule doesn't open it
                .story("ReadOnly", context -> render(context, false, false, true))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Query rule = play.findByText("Reject test feeds");
                    play.expect(play.getByTitle("New Rule")).toHaveAttribute("aria-disabled", "true");
                    play.click(rule);
                    play.expect(play.getByTitle("Edit Selected Rule")).toHaveAttribute("aria-disabled", "true");
                    play.expect(play.getByTitle("Delete Selected Rules")).toHaveAttribute("aria-disabled", "true");
                    play.dblClick(rule);
                    play.sleep(100);
                    play.expect(screen.queryByText("Edit Rule", StroomDom.DIALOG_TITLE)).toBeNull();
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
        // A UI config with obfuscated fields; the confirmation is Stroom's real dialog
        return render(context, obfuscateFeed, obfuscateFeed, false);
    }

    private static Widget render(final StoryContext context,
                                 final boolean obfuscateFeed,
                                 final boolean realAlerts,
                                 final boolean readOnly) {
        final ProcessingScreenGinjector injector = GWT.create(ProcessingScreenGinjector.class);
        final ScreenHarness.Builder builder = ScreenHarness.builder(context, FIXTURES)
                .injector(injector);
        if (obfuscateFeed) {
            builder.startup(startup -> startup.extendedUiConfig(OBFUSCATING_UI_CONFIG));
        }
        if (realAlerts) {
            builder.realAlerts();
        }
        final ScreenHarness harness = builder.build();
        harness.afterStartUp(() -> {
            if (readOnly) {
                // Narrows what the screen provides, as a read-only document would. Here, not
                // before, so the root is made (and disposed) with each opening, as the leak check
                // opens and closes the screen
                harness.getComponentRoot().provide(Contexts.READ_ONLY, ContextValue.of(true));
            }
            final RuleSetTabController screen = injector.getRuleSetTabController();
            harness.show(screen);
        });
        return harness.asWidget();
    }

    // Another open document, with changes, as far as a save is concerned
    private static final class OtherDocument implements DocumentTabData, HasSave {

        @Override
        public DocRef getDocRef() {
            return new DocRef("XSLT", "other-1", "Other");
        }

        @Override
        public SvgImage getIcon() {
            return SvgImage.DOCUMENT_XSLT;
        }

        @Override
        public String getLabel() {
            return "Other";
        }

        @Override
        public boolean isCloseable() {
            return true;
        }

        @Override
        public String getType() {
            return "XSLT";
        }

        @Override
        public void save() {
            // Saved elsewhere
        }

        @Override
        public boolean isDirty() {
            return true;
        }
    }
}
