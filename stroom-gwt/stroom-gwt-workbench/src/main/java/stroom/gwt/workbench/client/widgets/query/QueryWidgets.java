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

package stroom.gwt.workbench.client.widgets.query;

import stroom.docref.DocRef;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestHandler;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.widgets.dateandtime.DateTimeWidgets;
import stroom.gwt.workbench.client.widgets.selectors.SelectorWidgets;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.datasource.QueryField;
import stroom.query.api.datasource.QueryOptionDefinition;
import stroom.query.api.datasource.QueryOptionRule;
import stroom.query.api.datasource.QueryOptionSet;
import stroom.query.client.DateTimeSettingsFactory;
import stroom.query.client.TimeZones;
import stroom.query.client.component.ExpressionEditor;
import stroom.query.client.component.ExpressionEditorController;
import stroom.query.client.component.ExpressionTreeController;
import stroom.query.client.component.FieldValuePicker;
import stroom.query.client.component.FieldValuePickerController;
import stroom.query.client.component.QueryOptions;
import stroom.query.client.component.QueryOptionsController;
import stroom.query.client.component.TermEditorController;
import stroom.query.client.model.SimpleFieldSelectionListModel;

import com.google.gwt.dom.client.Style;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// What the query widgets' stories share: Stroom's term editor controller, made as GIN makes it,
/// and, for the term's value picker and query options stories, an expression editor over a data
/// source whose `Status` field offers query options and its most common values, as an
/// Elasticsearch `keyword` field does.
final class QueryWidgets {

    /// The spy told of the expression each time it changes.
    static final String ON_CHANGE = "onChange";
    /// The spy told of each value picker request's filter (`<none>` for none).
    static final String VALUE_REQUESTS = "valueRequests";

    private static final DocRef DATA_SOURCE = new DocRef("ElasticIndex", "elastic-index-uuid-1", "Events");

    private QueryWidgets() {
        // Static utility
    }

    /// Stroom's term editor controller, with what GIN gives it.
    ///
    /// @param harness The harness the editor is shown in.
    /// @return The controller.
    static TermEditorController termEditorController(final ScreenHarness harness) {
        final DateTimeSettingsFactory dateTimeSettingsFactory = new DateTimeSettingsFactory(
                new TimeZones(harness.getRestFactory()),
                harness.getUserPreferencesManager());
        return new TermEditorController(harness.getEventBus(),
                harness.getRestFactory(),
                harness.getUiConfigCache(),
                SelectorWidgets.docSelectionControllerProvider(harness),
                SelectorWidgets.userRefSelectionControllerProvider(harness),
                DateTimeWidgets.dateTimeBoxControllers(harness),
                () -> new QueryOptionsController(harness.getEventBus(), new QueryOptions()),
                () -> new FieldValuePickerController(harness.getEventBus(),
                        new FieldValuePicker(),
                        harness.getRestFactory(),
                        dateTimeSettingsFactory));
    }

    /// An expression editor over the stories' data source, its value picker's requests answered by
    /// `fieldValues`.
    ///
    /// @param context     The story's context.
    /// @param expression  The expression shown.
    /// @param fieldValues Answers the value picker's requests.
    /// @return The harness showing the editor.
    static Widget editor(final StoryContext context,
                         final ExpressionOperator expression,
                         final RestHandler fieldValues) {
        final ScreenHarness[] holder = new ScreenHarness[1];
        final ScreenHarness harness = ScreenHarness.create(context, RestFixtures
                .builder()
                .post("/suggest/v1", RestReply.json("{\"list\": []}"))
                .post(FieldValuesFixture.FIELD_VALUES, request -> {
                    final String filter = FieldValuesFixture.filterOf(request.getBody());
                    holder[0].spy(VALUE_REQUESTS, filter == null
                            ? "<none>"
                            : filter);
                    return fieldValues.reply(request);
                })
                .build());
        holder[0] = harness;
        harness.fn(VALUE_REQUESTS);
        final ExpressionTreeController tree = new ExpressionTreeController(harness.getEventBus(),
                () -> termEditorController(harness));
        final ExpressionEditorController controller = new ExpressionEditorController(harness.getEventBus(), tree)
                .attach(new ExpressionEditor());
        harness.own(controller);
        final SimpleFieldSelectionListModel fields = new SimpleFieldSelectionListModel();
        fields.addItems(fields());
        controller.init(DATA_SOURCE, fields);
        controller.read(expression);
        harness.fn(ON_CHANGE);
        controller.setOnChange(() -> harness.spy(ON_CHANGE, controller.write().toString()));
        harness.add(frame(controller.asWidget()));
        return harness.asWidget();
    }

    /// The value picker's requests answered from a fixed list: `error` (500 of 1,000 records),
    /// `warn` (300), `info` (150) and `a,b` (50), which holds a comma.
    ///
    /// @return The handler.
    static RestHandler statusValues() {
        final Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("error", 500L);
        counts.put("warn", 300L);
        counts.put("info", 150L);
        counts.put("a,b", 50L);
        final FieldValuesFixture fixture = new FieldValuesFixture(counts, 1000);
        return request -> RestReply.json(fixture.find(request.getBody()));
    }

    /// Selects a term, by the start of its name, to edit it.
    ///
    /// @param play The play.
    /// @param name The start of the term's name, e.g. `Status = error`.
    static void editTerm(final Play play, final String name) {
        play.click(play.findByText(TextMatch.startingWith(name), ".expression-item__label"));
        play.waitFor(() -> play.expect(play.querySelector(".term-editor__item--field")).not().toBeNull());
    }

    /// @param play The play.
    /// @return The term's value box.
    static Query valueBox(final Play play) {
        return play.screen().getByRole("combobox", "Value");
    }

    /// The stories' fields: `UserId`, text with no query options or values to pick, and `Status`, a
    /// keyword field with the query options and value picker of an Elasticsearch `keyword` field.
    ///
    /// @return The fields.
    static List<QueryField> fields() {
        return List.of(
                QueryField.createText("UserId"),
                QueryField.createKeyword("Status")
                        .copy()
                        .queryOptionSet(keywordOptions())
                        .supportsTopValues(true)
                        .build());
    }

    /// The query options of an Elasticsearch `keyword` field, copied from Stroom's
    /// `ElasticQueryOptionSets.KEYWORD` (server side, so not in the GWT client).
    ///
    /// @return The options.
    static QueryOptionSet keywordOptions() {
        return QueryOptionSet.of(
                QueryOptionDefinition.builder()
                        .id("caseInsensitive")
                        .displayName("Case-insensitive")
                        .description("Match values regardless of case.")
                        .booleanType(false)
                        .addRule(QueryOptionRule.disabledUnless("fuzziness", "OFF"))
                        .build(),
                QueryOptionDefinition.builder()
                        .id("fuzziness")
                        .displayName("Fuzzy matching")
                        .description("Match values within a number of single-character edits of the value.")
                        .choiceType("OFF")
                        .addChoice("OFF", "Off")
                        .addChoice("AUTO", "Auto (by length)")
                        .addChoice("1", "1 edit")
                        .addChoice("2", "2 edits")
                        .addRule(QueryOptionRule.disabledWhen("caseInsensitive", "true"))
                        .build(),
                QueryOptionDefinition.builder()
                        .id("fuzzyPrefixLength")
                        .displayName("Exact prefix length")
                        .description("Number of leading characters that must match exactly.")
                        .integerType(0, 0, 20)
                        .addRule(QueryOptionRule.disabledWhen("fuzziness", "OFF"))
                        .build(),
                QueryOptionDefinition.builder()
                        .id("fuzzyTranspositions")
                        .displayName("Allow transpositions")
                        .description("Count swapping two adjacent characters (ab to ba) as one edit.")
                        .booleanType(true)
                        .addRule(QueryOptionRule.disabledWhen("fuzziness", "OFF"))
                        .build());
    }

    /// The stories' frame around the editor, wide enough for a term being edited, with its buttons.
    private static Widget frame(final Widget editor) {
        final FlowPanel frame = new FlowPanel();
        final Style style = frame.getElement().getStyle();
        style.setProperty("width", "1100px");
        style.setProperty("height", "420px");
        style.setProperty("border", "1px solid var(--border-color)");
        editor.setSize("100%", "100%");
        frame.add(editor);
        return frame;
    }
}
