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

import stroom.docref.DocRef;
import stroom.feed.shared.FeedDoc;
import stroom.gwt.workbench.client.app.gin.processing.ProcessingScreenGinjector;
import stroom.gwt.workbench.client.app.rest.JsonValues;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.client.widgets.tree.ExplorerFixture;
import stroom.gwt.workbench.client.widgets.tree.TreeFixtures;
import stroom.gwt.workbench.framework.client.play.EventInit;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.client.presenter.ProcessorEditPresenter;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorType;
import stroom.processor.shared.QueryData;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

import java.util.Map;

/// Stories matching `App/Main/ProcessorFilterEditDialog` in the React Storybook, showing Stroom's
/// real [ProcessorEditPresenter] (the 'Add Filter'/'Edit Filter' dialog, as `ProcessorPresenter`
/// shows it) with fake REST replies.
///
/// | React | Stroom |
/// |---|---|
/// | `validateExpression` | `POST /expression/v1/validate` |
/// | `api.update` | `PUT /processorFilter/v1/{id}` (echoes the filter) |
/// | `api.create` | `POST /processorFilter/v1` |
/// | `streamTypes` | `GET /meta/v1/getTypes` |
/// | (the feed picker) | the explorer tree's `POST /explorer/v2/fetchExplorerNodes` |
///
/// React's `onSaved` is a spy on the dialog's consumer, and `api.update`'s checks are checks on the
/// request spy. The confirmations are Stroom's real dialogs (`realAlerts()`).
public final class ProcessorFilterEditDialogStories {

    /// The name of the spy recording the filter the dialog returns (its consumer).
    static final String ON_SAVED = "onSaved";

    private static final String VALIDATE_PATH = "/expression/v1/validate";

    // ProcessorEditPresenter's confirmations
    private static final String ALL_FEEDS = "about to process all feeds";
    private static final String UPDATE_EXISTING = "update an existing filter";

    // The fields of the dialog (the inputs inside the widgets with their FormGroup's identity as id)
    private static final String MAX_TASKS = "#processorEditMaxProcessingTasks input";
    private static final String DELAY_ENABLED = "#processorEditMaxTaskCreationDelayEnabled input";

    private static final RestFixtures VALID = fixtures("{\"ok\": true}");
    private static final RestFixtures INVALID = fixtures("{\"ok\": false, \"string\": \"Field 'Bogus' is not valid\"}");

    private ProcessorFilterEditDialogStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/ProcessorFilterEditDialog", ProcessorFilterEditDialogStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Creating a filter with no Feed/Id/Parent Id term warns that it would process all feeds
                .story("AllFeedsConfirm", context -> render(context, VALID, null))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    screen.findByText("Add Filter", StroomDom.DIALOG_TITLE);
                    play.click(okButton(screen, "Add Filter"));
                    screen.findByText(TextMatch.containingIgnoreCase(ALL_FEEDS));
                    expectNoProblems(play);
                })
                // Server side validation runs first on OK: an invalid expression shows the message
                .story("InvalidExpressionBlocks", context -> render(context, INVALID, null))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    screen.findByText("Add Filter", StroomDom.DIALOG_TITLE);
                    play.click(okButton(screen, "Add Filter"));
                    screen.findByText(TextMatch.containing("Field 'Bogus' is not valid"));
                    play.expect(screen.queryByText(TextMatch.containingIgnoreCase(ALL_FEEDS))).toBeNull();
                    // The alert spy records the message as AlertEvent holds it, HTML escaped
                    play.expect(play.spy(ScreenHarness.ALERT_SPY))
                            .toHaveBeenCalledWith("ERROR: Field &#39;Bogus&#39; is not valid");
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                })
                // Editing an existing filter first warns about the tracker position
                .story("UpdateExistingConfirm", context -> render(context, VALID, filter(7, emptyExpression())
                        .build()))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    screen.findByText("Edit Filter", StroomDom.DIALOG_TITLE);
                    play.click(okButton(screen, "Edit Filter"));
                    screen.findByText(TextMatch.containingIgnoreCase(UPDATE_EXISTING));
                    expectNoProblems(play);
                })
                // 0 is the unlimited value for Max Processing Tasks, so the spinner has to reach it
                .story("MaxProcessingTasksCanBeSetToUnlimited", context -> render(context, VALID,
                        filter(7, feedExpression()).maxProcessingTasks(1).build()))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    screen.findByText("Edit Filter", StroomDom.DIALOG_TITLE);
                    // Differs from React: GWT's ValueSpinner is a text box, holding the text "1"
                    final Query input = screen.querySelector(MAX_TASKS);
                    play.expect(input).toHaveValue("1");
                    play.clear(input);
                    play.type(input, "0");
                    play.expect(input).toHaveValue("0");
                    saveExistingFilter(play, screen);
                    // 0 is a real value, not a blank
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put("/processorFilter/v1/7")
                                    .withJsonBodyContaining("{\"maxProcessingTasks\": 0}")
                                    .toSpyMatcher()));
                    expectNoProblems(play);
                })
                // A filter with no value shows the unlimited default rather than a blank spinner
                .story("MaxProcessingTasksDefaultsToUnlimited", context -> render(context, VALID,
                        filter(8, feedExpression()).build()))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    screen.findByText("Edit Filter", StroomDom.DIALOG_TITLE);
                    play.expect(screen.querySelector(MAX_TASKS)).toHaveValue("0");
                    expectNoProblems(play);
                })
                // While the tick box is clear the delay is not sent at all
                .story("MaxTaskCreationDelayNotSentWhenUnticked", context -> render(context, VALID,
                        filter(9, feedExpression()).build()))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    screen.findByText("Edit Filter", StroomDom.DIALOG_TITLE);
                    // A filter with no delay opens unticked
                    play.expect(screen.querySelector(DELAY_ENABLED)).not().toBeChecked();
                    play.expect(screen.getByText("Max Task Creation Delay", "label")).toBeInTheDocument();
                    // A duration is a group of an amount and its unit, which the duration picker names
                    final Play delay = screen.within(screen.getByRole("group", "Max Task Creation Delay"));
                    play.expect(delay.getByRole("textbox", "Amount")).toBeInTheDocument();
                    play.expect(delay.getByRole("textbox", "Unit")).toBeInTheDocument();
                    saveExistingFilter(play, screen);
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put("/processorFilter/v1/9")
                                    .withBody("no maxTaskCreationDelay",
                                            body -> parse(body).get("maxTaskCreationDelay") == null)
                                    .toSpyMatcher()));
                    expectNoProblems(play);
                })
                // Ticking it sends the duration, in the API's upper case time unit
                .story("MaxTaskCreationDelaySentWhenTicked", context -> render(context, VALID,
                        filter(10, feedExpression()).build()))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    screen.findByText("Edit Filter", StroomDom.DIALOG_TITLE);
                    final Query tick = screen.querySelector(DELAY_ENABLED);
                    play.click(tick);
                    play.expect(tick).toBeChecked();
                    saveExistingFilter(play, screen);
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put("/processorFilter/v1/10")
                                    .withJsonBodyContaining(
                                            "{\"maxTaskCreationDelay\": {\"time\": 30, \"timeUnit\": \"SECONDS\"}}")
                                    .toSpyMatcher()));
                    expectNoProblems(play);
                })
                // An existing per-filter delay opens ticked and round-trips unchanged
                .story("ExistingMaxTaskCreationDelayOpensTicked", context -> render(context, VALID,
                        filter(11, feedExpression())
                                .maxTaskCreationDelay(new SimpleDuration(5L, TimeUnit.MINUTES))
                                .build()))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    screen.findByText("Edit Filter", StroomDom.DIALOG_TITLE);
                    play.expect(screen.querySelector(DELAY_ENABLED)).toBeChecked();
                    saveExistingFilter(play, screen);
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put("/processorFilter/v1/11")
                                    .withJsonBodyContaining(
                                            "{\"maxTaskCreationDelay\": {\"time\": 5, \"timeUnit\": \"MINUTES\"}}")
                                    .toSpyMatcher()));
                    expectNoProblems(play);
                })
                // Feed dependencies are a grid with an Add/Edit/Remove toolbar and an editor popup
                .story("FeedDependenciesUseTheEditorPopup", context -> render(context, VALID, null))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    screen.findByText("Add Filter", StroomDom.DIALOG_TITLE);
                    // Differs from React: the dialog is on the page's body
                    play.click(screen.getByRole("button", TextMatch.startingWith("Set Feed Dependencies")));
                    final Play deps = dialog(screen, "Set Feed Dependencies");
                    // GWT's two columns, and Remove disabled until a row is selected.
                    // Differs from React: GWT's FeedDependencyListPresenter.enableButtons enables Edit
                    // whenever the filter isn't read only, with or without a selection
                    play.expect(deps.getByRole("columnheader", "Feed")).toBeInTheDocument();
                    play.expect(deps.getByRole("columnheader", "Type")).toBeInTheDocument();
                    play.expect(deps.getByTitle("Edit Reference")).not().toHaveClass("disabled");
                    play.expect(deps.getByTitle("Remove Reference")).toHaveClass("disabled");
                    // Add opens the editor popup
                    play.click(deps.getByTitle("New Reference"));
                    final Play editor = dialog(screen, "New Feed Dependency");
                    // GWT refuses to close with no feed
                    play.click(editor.getByRole("button", StroomDom.button("OK")));
                    final Play alert = screen.within(
                            screen.findByText("You must specify a feed to use.").closest(StroomDom.DIALOG));
                    play.click(alert.getByRole("button", StroomDom.button("Close")));
                    // Differs from React: the feed is a document picker (DocSelectionBoxPresenter),
                    // not a text field: open it and choose MY_FEED from the explorer tree
                    play.fireEvent().mouseDown(editor.querySelector(".dropDownView-container"), EventInit.create());
                    final Play picker = dialog(screen, "Choose item");
                    play.click(picker.findByText("MY_FEED"));
                    play.click(picker.getByRole("button", StroomDom.button("OK")));
                    // Differs from React: GWT also requires a stream type
                    play.click(editor.querySelector(StroomDom.SELECTION_BOX));
                    play.click(screen.findByText("Events"));
                    play.click(editor.getByRole("button", StroomDom.button("OK")));
                    // The row lands in the grid, and selecting it enables Edit/Remove
                    deps.findByText("MY_FEED");
                    play.click(deps.getByText("MY_FEED"));
                    play.waitFor(() -> play.expect(deps.getByTitle("Remove Reference")).not().toHaveClass("disabled"));
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                });
    }

    // The OK button of the dialog captioned with the given text
    private static Query okButton(final Play screen, final String caption) {
        return dialog(screen, caption).getByRole("button", StroomDom.button("OK"));
    }

    private static Play dialog(final Play screen, final String caption) {
        return screen.within(screen.findByText(caption, StroomDom.DIALOG_TITLE).closest(StroomDom.DIALOG));
    }

    // Drives OK through the single tracker confirmation to the save
    private static void saveExistingFilter(final Play play, final Play screen) {
        play.click(okButton(screen, "Edit Filter"));
        final Play confirm = screen.within(
                screen.findByText(TextMatch.containingIgnoreCase(UPDATE_EXISTING)).closest(StroomDom.DIALOG));
        play.click(confirm.getByRole("button", StroomDom.button("OK")));
    }

    private static Map<?, ?> parse(final String body) {
        return body == null
                ? Map.of()
                : (Map<?, ?>) JsonValues.parse(body);
    }

    private static void expectNoProblems(final Play play) {
        play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
        play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
    }

    private static ExpressionOperator emptyExpression() {
        return ExpressionOperator.builder().build();
    }

    // An expression with a Feed term, so only the tracker warning stands between OK and the save
    private static ExpressionOperator feedExpression() {
        return ExpressionOperator.builder()
                .addTerm(ExpressionTerm.builder().field("Feed").condition(Condition.EQUALS).value("TEST").build())
                .build();
    }

    private static ProcessorFilter.Builder filter(final int id, final ExpressionOperator expression) {
        return ProcessorFilter.builder()
                .id(id)
                .priority(10)
                .queryData(QueryData.builder().expression(expression).build());
    }

    private static RestFixtures fixtures(final String validation) {
        return TreeFixtures.explorerRoutes(new ExplorerFixture(ExplorerFixture.folder("System",
                        ExplorerFixture.doc("MY_FEED", FeedDoc.TYPE))))
                .post(VALIDATE_PATH, RestReply.json(validation))
                .put("/processorFilter/v1/*", request -> RestReply.json(request.getBody()))
                .get("/meta/v1/getTypes", RestReply.json("[\"Reference\", \"Events\"]"))
                .build();
    }

    private static Widget render(final StoryContext context,
                                 final RestFixtures fixtures,
                                 final ProcessorFilter filter) {
        final ProcessingScreenGinjector injector = GWT.create(ProcessingScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures)
                .injector(injector)
                // The confirmations and alerts are Stroom's real dialogs
                .realAlerts()
                .build();
        harness.fn(ON_SAVED);
        final DocRef pipelineRef = DocRef.builder().type(PipelineDoc.TYPE).uuid("p1").name("My Pipeline").build();
        harness.afterStartUp(() -> {
            final ProcessorEditPresenter presenter = injector.getProcessorEditPresenter();
            // As ProcessorPresenter adds or edits a filter
            presenter.show(ProcessorType.PIPELINE, pipelineRef, filter, emptyExpression(), result ->
                    harness.spy(ON_SAVED, result == null
                            ? null
                            : String.valueOf(result.getId())));
        });
        return harness.asWidget();
    }
}
