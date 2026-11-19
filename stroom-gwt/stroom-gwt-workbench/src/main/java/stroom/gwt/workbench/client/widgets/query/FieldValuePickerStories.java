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

import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.client.component.FieldValuePickerController;

/// Stories for Stroom's value picker ([FieldValuePickerController]), opened from a term's Pick Value
/// button when its field's data source can list its most common values (as an Elasticsearch
/// `keyword` field can) and its condition is `=`, `!=` or `in`. It lists the values with the
/// share of records that have each, filtered as the user types. For `=` picking a value puts it in
/// the term and closes the picker; for `in` each value is ticked while the term holds it, picking
/// one adds or removes it, and the picker stays open.
///
/// | Endpoint | Reply |
/// | --- | --- |
/// | `POST /suggest/v1/fieldValues` | `error`, `warn`, `info` and `a,b`, as [QueryWidgets#statusValues()] |
/// | `POST /suggest/v1` | No suggestions |
public final class FieldValuePickerStories {

    private static final String VALUES = "Pick Value";

    private FieldValuePickerStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Query/FieldValuePicker", FieldValuePickerStories.class)
                .layout(StoryLayout.CENTERED)
                // The values with their shares, what they were counted across, and the filter with
                // the focus; picking one puts it in the term and closes the picker
                .story("Default", context -> QueryWidgets.editor(context,
                        expression(Condition.EQUALS, "error"), QueryWidgets.statusValues()))
                .withPlay(play -> {
                    final Play picker = openPicker(play, "Status = error");
                    play.expect(picker.getByText("All time - 1,000 records")).toBeInTheDocument();
                    play.expect(option(picker, "error")).toHaveTextContent(TextMatch.containing("50.0%"));
                    play.expect(option(picker, "warn"))
                            .toHaveAttribute("aria-description", "30.0%, 300 of 1,000 records");
                    play.expect(picker.getByRole("searchbox", "Filter values")).toHaveFocus();
                    play.click(option(picker, "warn"));
                    expectClosed(play);
                    play.expect(QueryWidgets.valueBox(play)).toHaveValue("warn");
                    play.waitFor(() -> play.expect(play.spy(QueryWidgets.ON_CHANGE)).toHaveBeenCalled());
                    expectNoProblems(play);
                })
                // For 'in', the term's values are ticked; picking one adds it to the term (or
                // removes it) and the picker stays open. A value holding a comma can't be picked
                .story("PickSeveral", context -> QueryWidgets.editor(context,
                        expression(Condition.IN, "error"), QueryWidgets.statusValues()))
                .withPlay(play -> {
                    final Play picker = openPicker(play, "Status in error");
                    play.expect(option(picker, "error")).toHaveAttribute("aria-checked", "true");
                    play.expect(option(picker, "warn")).toHaveAttribute("aria-checked", "false");
                    play.click(option(picker, "warn"));
                    play.waitFor(() -> play.expect(option(picker, "warn")).toHaveAttribute("aria-checked", "true"));
                    play.expect(QueryWidgets.valueBox(play)).toHaveValue("error, warn");
                    play.click(option(picker, "error"));
                    play.waitFor(() -> play.expect(option(picker, "error")).toHaveAttribute("aria-checked", "false"));
                    play.expect(QueryWidgets.valueBox(play)).toHaveValue("warn");
                    play.expect(option(picker, "a,b")).toHaveAttribute("aria-description",
                            "5.0%, 50 of 1,000 records. Values holding a comma can't be used with 'in'");
                    play.click(option(picker, "a,b"));
                    play.expect(QueryWidgets.valueBox(play)).toHaveValue("warn");
                    // Escape closes it, keeping what was picked
                    play.keyboard("{Escape}");
                    expectClosed(play);
                    play.expect(QueryWidgets.valueBox(play)).toHaveValue("warn");
                    expectNoProblems(play);
                })
                // Left open, to look at: an 'in' term's picker, its value ticked, each value with its share
                // as a bar and a percentage, and the value holding a comma faded
                .story("Open", context -> QueryWidgets.editor(context,
                        expression(Condition.IN, "error"), QueryWidgets.statusValues()))
                .withPlay(play -> {
                    final Play picker = openPicker(play, "Status in error");
                    play.expect(option(picker, "error")).toHaveAttribute("aria-checked", "true");
                    play.expect(option(picker, "a,b")).toHaveTextContent(TextMatch.containing("5.0%"));
                    expectNoProblems(play);
                })
                // Typing in the filter asks for the values containing it; Enter picks the first
                .story("Filter", context -> QueryWidgets.editor(context,
                        expression(Condition.EQUALS, "error"), QueryWidgets.statusValues()))
                .withPlay(play -> {
                    final Play picker = openPicker(play, "Status = error");
                    play.type(picker.getByRole("searchbox", "Filter values"), "wa");
                    play.waitFor(() -> play.expect(picker.queryByRole("option", "error"))
                            .toBeNull());
                    play.expect(play.spy(QueryWidgets.VALUE_REQUESTS)).toHaveBeenLastCalledWith("wa");
                    play.keyboard("{Enter}");
                    expectClosed(play);
                    play.expect(QueryWidgets.valueBox(play)).toHaveValue("warn");
                    expectNoProblems(play);
                })
                // Escape in the empty filter closes the picker, leaving the term as it was
                .story("Cancel", context -> QueryWidgets.editor(context,
                        expression(Condition.EQUALS, "error"), QueryWidgets.statusValues()))
                .withPlay(play -> {
                    openPicker(play, "Status = error");
                    play.keyboard("{Escape}");
                    expectClosed(play);
                    play.expect(QueryWidgets.valueBox(play)).toHaveValue("error");
                    play.expect(play.spy(QueryWidgets.ON_CHANGE)).not().toHaveBeenCalled();
                    expectNoProblems(play);
                })
                // A field with no values says so
                .story("NoValues", context -> QueryWidgets.editor(context,
                        expression(Condition.EQUALS, "error"),
                        request -> RestReply.json("{\"values\": [], \"totalCount\": 0, \"allTime\": true}")))
                .withPlay(play -> {
                    final Play picker = openPicker(play, "Status = error");
                    play.expect(picker.findByText("No values found")).toBeInTheDocument();
                    expectNoProblems(play);
                })
                // A failed request says why in the picker, in place of the values
                .story("FetchFails", context -> QueryWidgets.editor(context,
                        expression(Condition.EQUALS, "error"),
                        request -> RestReply.error(500, "Index not found")))
                .withPlay(play -> {
                    final Play picker = openPicker(play, "Status = error");
                    play.expect(picker.findByText(TextMatch.startingWith("Unable to fetch values")))
                            .toBeInTheDocument();
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                })
                // Only for a field whose data source lists its values (not UserId), and only for =,
                // != and in
                .story("OnlyForFieldsWithValues", context -> QueryWidgets.editor(context,
                        expression(Condition.EQUALS, "error"), QueryWidgets.statusValues()))
                .withPlay(play -> {
                    QueryWidgets.editTerm(play, "UserId contains admin");
                    play.expect(play.screen().queryByRole("button", VALUES)).toBeNull();
                    QueryWidgets.editTerm(play, "Status = error");
                    play.expect(play.screen().findByRole("button", VALUES)).toBeInTheDocument();
                    expectNoProblems(play);
                });
    }

    // Edits a term, by the start of its name, and presses its Values button, returning the picker
    private static Play openPicker(final Play play, final String term) {
        QueryWidgets.editTerm(play, term);
        final Play screen = play.screen();
        play.click(screen.findByRole("button", VALUES));
        return screen.within(screen.findByRole("searchbox", "Filter values").closest(".field-value-picker"));
    }

    // A value in the picker, by its text
    private static Query option(final Play picker, final String value) {
        return picker.findByRole("option", value);
    }

    private static void expectClosed(final Play play) {
        play.waitFor(() -> play.expect(play.screen().queryByRole("searchbox", "Filter values")).toBeNull());
    }

    private static void expectNoProblems(final Play play) {
        play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
        play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
    }

    /// `UserId contains admin AND Status <condition> <value>`.
    private static ExpressionOperator expression(final Condition condition, final String value) {
        return ExpressionOperator.builder()
                .op(Op.AND)
                .addTerm(ExpressionTerm.builder()
                        .field("UserId")
                        .condition(Condition.CONTAINS)
                        .value("admin")
                        .build())
                .addTerm(ExpressionTerm.builder()
                        .field("Status")
                        .condition(condition)
                        .value(value)
                        .build())
                .build();
    }
}
