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

import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.client.component.QueryOptionsController;

import java.util.Map;

/// Stories for Stroom's query options dialog ([QueryOptionsController]), opened from a term's
/// Query Options button when its field's data source offers field-level query options (as an
/// Elasticsearch `keyword` field does). The dialog's controls are made from the options'
/// definitions; some apply only given others' values. OK keeps the options that differ from their
/// defaults in the term, and the button is marked while there are any.
///
/// The expression editor's suggestions (`POST /suggest/v1`) are answered with none, and the value
/// picker (`POST /suggest/v1/fieldValues`) from a fixed list (see [QueryWidgets]).
public final class QueryOptionsStories {

    private static final String OPTIONS = "Query Options";
    private static final String OPTIONS_CHANGED = "Query Options (Changed)";
    private static final String CAPTION = "Query Options - Status";

    private QueryOptionsStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Query/QueryOptions", QueryOptionsStories.class)
                .layout(StoryLayout.CENTERED)
                // The dialog for a keyword field: a tick box, a choice and a number, each with its
                // help, the fuzzy options disabled while fuzzy matching is off
                .story("Default", context -> QueryWidgets.editor(context, expression(null),
                        QueryWidgets.statusValues()))
                .withPlay(play -> {
                    final Play dialog = openOptions(play, OPTIONS);
                    play.expect(dialog.getByRole("checkbox", "Case-insensitive")).not().toBeChecked();
                    play.expect(dialog.getByLabelText("Fuzzy matching")).toBeEnabled();
                    play.expect(dialog.getByRole("spinbutton", "Exact prefix length")).toBeDisabled();
                    play.expect(dialog.getByRole("checkbox", "Allow transpositions")).toBeDisabled();
                    play.click(dialog.getByRole("button", StroomDom.button("Cancel")));
                    expectClosed(play);
                    expectNoProblems(play);
                })
                // Ticking Case-insensitive disables fuzzy matching, which ignores case; OK keeps it in
                // the term and marks the button, and the dialog opens again with it ticked
                .story("Ok", context -> QueryWidgets.editor(context, expression(null),
                        QueryWidgets.statusValues()))
                .withPlay(play -> {
                    final Play dialog = openOptions(play, OPTIONS);
                    play.click(dialog.getByRole("checkbox", "Case-insensitive"));
                    play.waitFor(() -> play.expect(dialog.getByLabelText("Fuzzy matching")).toBeDisabled());
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    expectClosed(play);
                    play.waitFor(() -> play.expect(play.screen().getByRole("button", OPTIONS_CHANGED))
                            .toHaveClass("icon-colour__blue"));
                    play.waitFor(() -> play.expect(play.spy(QueryWidgets.ON_CHANGE)).toHaveBeenCalled());
                    // The term is still being edited, so its button is pressed again directly
                    final Play again = openDialog(play, OPTIONS_CHANGED);
                    play.expect(again.getByRole("checkbox", "Case-insensitive")).toBeChecked();
                    play.click(again.getByRole("button", StroomDom.button("Cancel")));
                    expectClosed(play);
                    expectNoProblems(play);
                })
                // Cancel keeps the term's options as they were
                .story("Cancel", context -> QueryWidgets.editor(context, expression(null),
                        QueryWidgets.statusValues()))
                .withPlay(play -> {
                    final Play dialog = openOptions(play, OPTIONS);
                    play.click(dialog.getByRole("checkbox", "Case-insensitive"));
                    play.click(dialog.getByRole("button", StroomDom.button("Cancel")));
                    expectClosed(play);
                    play.expect(play.screen().getByRole("button", OPTIONS)).not().toHaveClass("icon-colour__blue");
                    play.expect(play.spy(QueryWidgets.ON_CHANGE)).not().toHaveBeenCalled();
                    expectNoProblems(play);
                })
                // A term read with options other than the defaults has its button marked
                .story("ChangedOptions", context -> QueryWidgets.editor(context,
                        expression(Map.of("caseInsensitive", "true")), QueryWidgets.statusValues()))
                .withPlay(play -> {
                    QueryWidgets.editTerm(play, "Status = error");
                    play.expect(play.screen().findByRole("button", OPTIONS_CHANGED))
                            .toHaveClass("icon-colour__blue");
                    expectNoProblems(play);
                })
                // Left open, to look at: the options of an Elasticsearch keyword field, the fuzzy ones
                // disabled while fuzzy matching is off
                .story("Open", context -> QueryWidgets.editor(context, expression(null),
                        QueryWidgets.statusValues()))
                .withPlay(play -> {
                    final Play dialog = openOptions(play, OPTIONS);
                    play.expect(dialog.getByRole("checkbox", "Allow transpositions")).toBeDisabled();
                    expectNoProblems(play);
                })
                // Duplicating a term keeps its query options
                .story("DuplicateKeepsOptions", context -> QueryWidgets.editor(context,
                        expression(Map.of("caseInsensitive", "true")), QueryWidgets.statusValues()))
                .withPlay(play -> {
                    QueryWidgets.editTerm(play, "Status = error");
                    play.click(play.getByRole("button", "Duplicate Selected Expression Item"));
                    // The copy, as the original is still being edited, so shows no label
                    play.click(play.findByText("Status = error", ".expression-item__label"));
                    play.waitFor(() -> play.expect(play.screen().getByRole("button", OPTIONS_CHANGED))
                            .toHaveClass("icon-colour__blue"));
                    expectNoProblems(play);
                })
                // Changing a term's field keeps only the options the new field offers: UserId offers
                // none, so going back to Status leaves it with the defaults
                .story("FieldChangeDropsOptions", context -> QueryWidgets.editor(context,
                        expression(Map.of("caseInsensitive", "true")), QueryWidgets.statusValues()))
                .withPlay(play -> {
                    QueryWidgets.editTerm(play, "Status = error");
                    play.expect(play.screen().findByRole("button", OPTIONS_CHANGED)).toBeInTheDocument();
                    chooseField(play, "UserId");
                    play.waitFor(() -> play.expect(play.screen().queryByRole("button", TextMatch.startingWith(OPTIONS)))
                            .toBeNull());
                    chooseField(play, "Status");
                    play.waitFor(() -> play.expect(play.screen().getByRole("button", OPTIONS)).toBeInTheDocument());
                    play.expect(play.screen().getByRole("button", OPTIONS)).not().toHaveClass("icon-colour__blue");
                    expectNoProblems(play);
                })
                // A field whose data source offers no query options (UserId) has no Options button
                .story("OnlyForFieldsWithOptions", context -> QueryWidgets.editor(context, expression(null),
                        QueryWidgets.statusValues()))
                .withPlay(play -> {
                    QueryWidgets.editTerm(play, "UserId contains admin");
                    play.expect(play.screen().queryByRole("button", OPTIONS)).toBeNull();
                    QueryWidgets.editTerm(play, "Status = error");
                    play.expect(play.screen().findByRole("button", OPTIONS)).toBeInTheDocument();
                    expectNoProblems(play);
                });
    }

    // Chooses a field for the term being edited, from its field picker
    private static void chooseField(final Play play, final String field) {
        play.click(play.querySelector(".term-editor__item--field .selection-box__input"));
        play.click(play.screen().findByRole("option", field));
    }

    // Edits the Status term and presses its Query Options button (by its name), returning the dialog
    private static Play openOptions(final Play play, final String buttonName) {
        QueryWidgets.editTerm(play, "Status = error");
        return openDialog(play, buttonName);
    }

    // Presses the Query Options button (by its name) of the term being edited, returning the dialog
    private static Play openDialog(final Play play, final String buttonName) {
        final Play screen = play.screen();
        play.click(screen.findByRole("button", buttonName));
        final Query title = screen.findByText(CAPTION, StroomDom.DIALOG_TITLE);
        return screen.within(title.closest(StroomDom.DIALOG));
    }

    private static void expectClosed(final Play play) {
        play.waitFor(() -> play.expect(play.screen().queryByText(TextMatch.exact(CAPTION), StroomDom.DIALOG_TITLE))
                .toBeNull());
    }

    private static void expectNoProblems(final Play play) {
        play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
        play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
    }

    /// `UserId contains admin AND Status = error`, the Status term holding the options given.
    private static ExpressionOperator expression(final Map<String, String> statusOptions) {
        return ExpressionOperator.builder()
                .op(Op.AND)
                .addTerm(ExpressionTerm.builder()
                        .field("UserId")
                        .condition(Condition.CONTAINS)
                        .value("admin")
                        .build())
                .addTerm(ExpressionTerm.builder()
                        .field("Status")
                        .condition(Condition.EQUALS)
                        .value("error")
                        .options(statusOptions)
                        .build())
                .build();
    }
}
