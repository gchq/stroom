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


package stroom.gwt.workbench.framework.client.play;

import stroom.gwt.workbench.framework.client.play.Keys.KeyAction;
import stroom.gwt.workbench.framework.client.play.PlayStep.Kind;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.dom.client.Element;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/// Builds the steps of a play function, with the same vocabulary as React Storybook's play
/// functions (Testing Library queries, `userEvent`, `fireEvent`, `waitFor` and `expect`), e.g.
/// ```
/// play.click(play.getByRole("button", "Save"));
/// play.waitFor(() -> play.expect(play.screen().getByText("Saved")).toBeVisible());
/// play.expect(play.spy("onSave")).toHaveBeenCalledTimes(1);
/// ```
///
/// Calling a method adds a step; the steps run once the play function has returned. Queries and
/// values are lazy, so find their element (or read their value) when the step runs.
///
/// Steps can only be added while the play function runs, not while a step runs: e.g. calling
/// `play.click(...)` inside [#run(String, Runnable)] or a [Value]'s supplier throws a
/// [PlayException] (rather than being silently ignored). To use a value read part way through the
/// play in a later step, capture it with [#capture(String, Supplier)].
///
/// A `findBy*`/`findAllBy*` method adds a step that waits for the element at the point it is
/// called, as `await findBy...` waits there in a React play; the query it returns can then be used
/// by later steps (which find the element again as they run). So
/// `final Query dialog = screen.findByRole("dialog");` waits for the dialog, and a bare
/// `screen.findByText("Saved");` is the equivalent of `await screen.findByText('Saved')`.
///
/// A builder's queries search the story's root element (`within(canvasElement)`); those of
/// [#screen()] search the whole `<body>` (`within(document.body)`), where GWT attaches popups and
/// dialogs; those of [#within(Query)] search an element.
public final class Play {

    // Shared with any builders created by screen() and within(...)
    private final Build build;
    private final Query scope;

    /// Creates a play builder for a whole story.
    public Play() {
        this(null, new Build());
    }

    private Play(final Query scope, final Build build) {
        this.scope = scope;
        this.build = build;
    }

    /// @return The steps added, in order.
    List<PlayStep> getSteps() {
        return build.steps;
    }

    /// Stops steps being added, as the play function has returned and its steps are about to
    /// run. Called by [PlayRunner].
    void finishBuilding() {
        build.finished = true;
    }

    /// @return True while the play function is adding its steps, i.e. it hasn't returned and no
    /// step is running.
    boolean isBuilding() {
        return !build.finished && !PlayStep.isRunning();
    }

    /// Adds a step to the play function, or to the `step` or `waitFor` group being built.
    ///
    /// @param step The step.
    /// @throws PlayException If the play function has finished adding its steps, e.g. a step is
    ///                       added inside [#run(String, Runnable)].
    void addStep(final PlayStep step) {
        checkBuilding();
        build.targets.peek().add(step);
    }

    private void checkBuilding() {
        if (build.finished || PlayStep.isRunning()) {
            throw new PlayException("A step can't be added while the steps run, e.g. by play.click(...), "
                                    + "play.expect(...) or play.findBy...(...) inside play.run(...) or a "
                                    + "value's supplier. Add the step in the play function itself, use "
                                    + "play.waitFor(...) to retry it, or play.capture(...) to read a value "
                                    + "for a later step.");
        }
    }

    // ---------- Scopes ----------

    /// @return A builder whose queries search the whole document body, the equivalent of
    /// `within(document.body)` or `screen`. Use it for popups, menus and dialogs, which GWT
    /// attaches to the body rather than the story's root element.
    public Play screen() {
        return new Play(Query.body(), build);
    }

    /// @param container A query for the element to search within.
    /// @return A builder whose queries only find elements within the container, the equivalent of
    /// `within(element)`.
    public Play within(final Query container) {
        return new Play(Objects.requireNonNull(container, "container"), build);
    }

    /// @return A query for the document's `<body>` element itself, e.g. as the target of
    /// `fireEvent.mouseUp(document)` or `fireEvent.keyDown(document.body, ...)`.
    public Query body() {
        return Query.body();
    }

    // ---------- getBy* ----------

    /// A query for the single element with the role, the equivalent of `getByRole(role)`; an error if
    /// there's none or several.
    ///
    /// @param role The ARIA role, e.g. `button`.
    /// @return The query.
    public Query getByRole(final String role) {
        return byRole(Variant.GET, role, null);
    }

    /// A query for the single element with the role and accessible name, the equivalent of `getByRole(role,
    /// { name })`; an error if there's none or several. The accessible name is worked out as
    /// Testing Library does (`aria-labelledby`, then `aria-label`, then e.g. a label, `alt` or,
    /// for roles such as button, link, tab and option, the visible text of the content), so
    /// hidden content doesn't count. For any name, use [#getByRole(String)] (a null name, which
    /// must be cast, e.g. `(String) null`, also means any).
    ///
    /// @param role The ARIA role, e.g. `button`.
    /// @param name The accessible name, exactly, e.g. the button's text.
    /// @return The query.
    public Query getByRole(final String role, final String name) {
        return byRole(Variant.GET, role, name != null
                ? TextMatch.exact(name)
                : null);
    }

    /// A query for the single element with the role and a matching accessible name, the equivalent of
    /// `getByRole(role, { name: /regex/ })`; an error if there's none or several.
    ///
    /// @param role The ARIA role, e.g. `button`.
    /// @param name Matches the accessible name, e.g. `TextMatch.exactIgnoreCase("ok")`.
    /// @return The query.
    public Query getByRole(final String role, final TextMatch name) {
        return byRole(Variant.GET, role, name);
    }

    /// A query for the single element whose own text matches, the equivalent of `getByText(text)`; an error
    /// if there's none or several.
    ///
    /// @param text The element's own text (whitespace collapsed), exactly.
    /// @return The query.
    public Query getByText(final String text) {
        return byText(Variant.GET, TextMatch.exact(text), null);
    }

    /// A query for the single element whose own text matches, the equivalent of `getByText(/regex/)`; an
    /// error if there's none or several.
    ///
    /// @param text Matches the element's own text (whitespace collapsed).
    /// @return The query.
    public Query getByText(final TextMatch text) {
        return byText(Variant.GET, text, null);
    }

    /// A query for the single element whose own text matches and the selector, the equivalent of
    /// `getByText(text, { selector })`; an error if there's none or several.
    ///
    /// @param text Matches the element's own text (whitespace collapsed).
    /// @param selector Only elements matching this CSS selector are included, e.g. `label`.
    /// @return The query.
    public Query getByText(final TextMatch text, final String selector) {
        return byText(Variant.GET, text, selector);
    }

    /// A query for the single element whose own text is the text and that matches the selector, the
    /// equivalent of `getByText(text, { selector })`; an error if there's none or several.
    ///
    /// @param text     The element's own text (whitespace collapsed), exactly.
    /// @param selector Only elements matching this CSS selector are included, e.g. `label`.
    /// @return The query.
    public Query getByText(final String text, final String selector) {
        return byText(Variant.GET, TextMatch.exact(text), selector);
    }

    /// A query for the single element labelled with matching text, the equivalent of
    /// `getByLabelText(label)`; an error if there's none or several.
    ///
    /// @param label The text of the field's label, `aria-label` or `aria-labelledby` element, exactly.
    /// @return The query.
    public Query getByLabelText(final String label) {
        return byLabelText(Variant.GET, TextMatch.exact(label));
    }

    /// A query for the single element labelled with matching text, the equivalent of
    /// `getByLabelText(/regex/)`; an error if there's none or several.
    ///
    /// @param label Matches the text of the field's label, `aria-label` or `aria-labelledby` element.
    /// @return The query.
    public Query getByLabelText(final TextMatch label) {
        return byLabelText(Variant.GET, label);
    }

    /// A query for the single element with a matching title, the equivalent of `getByTitle(title)`; an
    /// error if there's none or several.
    ///
    /// @param title The element's `title` attribute (or SVG `<title>`), exactly.
    /// @return The query.
    public Query getByTitle(final String title) {
        return byTitle(Variant.GET, TextMatch.exact(title));
    }

    /// A query for the single element with a matching title, the equivalent of `getByTitle(/regex/)`; an
    /// error if there's none or several.
    ///
    /// @param title Matches the element's `title` attribute (or SVG `<title>`).
    /// @return The query.
    public Query getByTitle(final TextMatch title) {
        return byTitle(Variant.GET, title);
    }

    /// A query for the single element with a matching placeholder, the equivalent of
    /// `getByPlaceholderText(placeholder)`; an error if there's none or several.
    ///
    /// @param placeholder The field's `placeholder` attribute, exactly.
    /// @return The query.
    public Query getByPlaceholderText(final String placeholder) {
        return byPlaceholderText(Variant.GET, TextMatch.exact(placeholder));
    }

    /// A query for the single element with a matching placeholder, the equivalent of
    /// `getByPlaceholderText(/regex/)`; an error if there's none or several.
    ///
    /// @param placeholder Matches the field's `placeholder` attribute.
    /// @return The query.
    public Query getByPlaceholderText(final TextMatch placeholder) {
        return byPlaceholderText(Variant.GET, placeholder);
    }

    /// A query for the single element with a matching `data-testid`, the equivalent of
    /// `getByTestId(testId)`; an error if there's none or several.
    ///
    /// @param testId The element's `data-testid` attribute, exactly.
    /// @return The query.
    public Query getByTestId(final String testId) {
        return byTestId(Variant.GET, TextMatch.exact(testId));
    }

    /// A query for the single element with a matching `data-testid`, the equivalent of
    /// `getByTestId(/regex/)`; an error if there's none or several.
    ///
    /// @param testId Matches the element's `data-testid` attribute.
    /// @return The query.
    public Query getByTestId(final TextMatch testId) {
        return byTestId(Variant.GET, testId);
    }

    /// A query for the single element whose current value matches, the equivalent of
    /// `getByDisplayValue(value)`; an error if there's none or several.
    ///
    /// @param value The field's current value (or a select's selected option's text), exactly.
    /// @return The query.
    public Query getByDisplayValue(final String value) {
        return byDisplayValue(Variant.GET, TextMatch.exact(value));
    }

    /// A query for the single element whose current value matches, the equivalent of
    /// `getByDisplayValue(/regex/)`; an error if there's none or several.
    ///
    /// @param value Matches the field's current value (or a select's selected option's text).
    /// @return The query.
    public Query getByDisplayValue(final TextMatch value) {
        return byDisplayValue(Variant.GET, value);
    }

    // ---------- getAllBy* ----------

    /// A query for all the elements with the role (at least one), the equivalent of `getAllByRole(role)`.
    ///
    /// @param role The ARIA role, e.g. `button`.
    /// @return The query.
    public Query getAllByRole(final String role) {
        return byRole(Variant.GET_ALL, role, null);
    }

    /// A query for all the elements with the role and accessible name (at least one), the equivalent of
    /// `getAllByRole(role, { name })`.
    ///
    /// @param role The ARIA role, e.g. `button`.
    /// @param name The accessible name, exactly, e.g. the button's text.
    /// @return The query.
    public Query getAllByRole(final String role, final String name) {
        return byRole(Variant.GET_ALL, role, name != null
                ? TextMatch.exact(name)
                : null);
    }

    /// A query for all the elements with the role and a matching accessible name (at least one), the
    /// equivalent of `getAllByRole(role, { name: /regex/ })`.
    ///
    /// @param role The ARIA role, e.g. `button`.
    /// @param name Matches the accessible name, e.g. `TextMatch.exactIgnoreCase("ok")`.
    /// @return The query.
    public Query getAllByRole(final String role, final TextMatch name) {
        return byRole(Variant.GET_ALL, role, name);
    }

    /// A query for all the elements whose own text matches (at least one), the equivalent of
    /// `getAllByText(text)`.
    ///
    /// @param text The element's own text (whitespace collapsed), exactly.
    /// @return The query.
    public Query getAllByText(final String text) {
        return byText(Variant.GET_ALL, TextMatch.exact(text), null);
    }

    /// A query for all the elements whose own text matches (at least one), the equivalent of
    /// `getAllByText(/regex/)`.
    ///
    /// @param text Matches the element's own text (whitespace collapsed).
    /// @return The query.
    public Query getAllByText(final TextMatch text) {
        return byText(Variant.GET_ALL, text, null);
    }

    /// A query for all the elements whose own text matches and the selector (at least one), the equivalent
    /// of `getAllByText(text, { selector })`.
    ///
    /// @param text Matches the element's own text (whitespace collapsed).
    /// @param selector Only elements matching this CSS selector are included, e.g. `label`.
    /// @return The query.
    public Query getAllByText(final TextMatch text, final String selector) {
        return byText(Variant.GET_ALL, text, selector);
    }

    /// A query for all the elements whose own text is the text and that match the selector (at least
    /// one), the equivalent of `getAllByText(text, { selector })`.
    ///
    /// @param text     The element's own text (whitespace collapsed), exactly.
    /// @param selector Only elements matching this CSS selector are included, e.g. `label`.
    /// @return The query.
    public Query getAllByText(final String text, final String selector) {
        return byText(Variant.GET_ALL, TextMatch.exact(text), selector);
    }

    /// A query for all the elements labelled with matching text (at least one), the equivalent of
    /// `getAllByLabelText(label)`.
    ///
    /// @param label The text of the field's label, `aria-label` or `aria-labelledby` element, exactly.
    /// @return The query.
    public Query getAllByLabelText(final String label) {
        return byLabelText(Variant.GET_ALL, TextMatch.exact(label));
    }

    /// A query for all the elements labelled with matching text (at least one), the equivalent of
    /// `getAllByLabelText(/regex/)`.
    ///
    /// @param label Matches the text of the field's label, `aria-label` or `aria-labelledby` element.
    /// @return The query.
    public Query getAllByLabelText(final TextMatch label) {
        return byLabelText(Variant.GET_ALL, label);
    }

    /// A query for all the elements with a matching title (at least one), the equivalent of
    /// `getAllByTitle(title)`.
    ///
    /// @param title The element's `title` attribute (or SVG `<title>`), exactly.
    /// @return The query.
    public Query getAllByTitle(final String title) {
        return byTitle(Variant.GET_ALL, TextMatch.exact(title));
    }

    /// A query for all the elements with a matching title (at least one), the equivalent of
    /// `getAllByTitle(/regex/)`.
    ///
    /// @param title Matches the element's `title` attribute (or SVG `<title>`).
    /// @return The query.
    public Query getAllByTitle(final TextMatch title) {
        return byTitle(Variant.GET_ALL, title);
    }

    /// A query for all the elements with a matching placeholder (at least one), the equivalent of
    /// `getAllByPlaceholderText(placeholder)`.
    ///
    /// @param placeholder The field's `placeholder` attribute, exactly.
    /// @return The query.
    public Query getAllByPlaceholderText(final String placeholder) {
        return byPlaceholderText(Variant.GET_ALL, TextMatch.exact(placeholder));
    }

    /// A query for all the elements with a matching placeholder (at least one), the equivalent of
    /// `getAllByPlaceholderText(/regex/)`.
    ///
    /// @param placeholder Matches the field's `placeholder` attribute.
    /// @return The query.
    public Query getAllByPlaceholderText(final TextMatch placeholder) {
        return byPlaceholderText(Variant.GET_ALL, placeholder);
    }

    /// A query for all the elements with a matching `data-testid` (at least one), the equivalent of
    /// `getAllByTestId(testId)`.
    ///
    /// @param testId The element's `data-testid` attribute, exactly.
    /// @return The query.
    public Query getAllByTestId(final String testId) {
        return byTestId(Variant.GET_ALL, TextMatch.exact(testId));
    }

    /// A query for all the elements with a matching `data-testid` (at least one), the equivalent of
    /// `getAllByTestId(/regex/)`.
    ///
    /// @param testId Matches the element's `data-testid` attribute.
    /// @return The query.
    public Query getAllByTestId(final TextMatch testId) {
        return byTestId(Variant.GET_ALL, testId);
    }

    /// A query for all the elements whose current value matches (at least one), the equivalent of
    /// `getAllByDisplayValue(value)`.
    ///
    /// @param value The field's current value (or a select's selected option's text), exactly.
    /// @return The query.
    public Query getAllByDisplayValue(final String value) {
        return byDisplayValue(Variant.GET_ALL, TextMatch.exact(value));
    }

    /// A query for all the elements whose current value matches (at least one), the equivalent of
    /// `getAllByDisplayValue(/regex/)`.
    ///
    /// @param value Matches the field's current value (or a select's selected option's text).
    /// @return The query.
    public Query getAllByDisplayValue(final TextMatch value) {
        return byDisplayValue(Variant.GET_ALL, value);
    }

    // ---------- queryBy* ----------

    /// A query for the single element with the role, if any, the equivalent of `queryByRole(role)`, e.g.
    /// for `toBeNull()`.
    ///
    /// @param role The ARIA role, e.g. `button`.
    /// @return The query.
    public Query queryByRole(final String role) {
        return byRole(Variant.QUERY, role, null);
    }

    /// A query for the single element with the role and accessible name, if any, the equivalent of
    /// `queryByRole(role, { name })`, e.g. for `toBeNull()`.
    ///
    /// @param role The ARIA role, e.g. `button`.
    /// @param name The accessible name, exactly, e.g. the button's text.
    /// @return The query.
    public Query queryByRole(final String role, final String name) {
        return byRole(Variant.QUERY, role, name != null
                ? TextMatch.exact(name)
                : null);
    }

    /// A query for the single element with the role and a matching accessible name, if any, the equivalent
    /// of `queryByRole(role, { name: /regex/ })`, e.g. for `toBeNull()`.
    ///
    /// @param role The ARIA role, e.g. `button`.
    /// @param name Matches the accessible name, e.g. `TextMatch.exactIgnoreCase("ok")`.
    /// @return The query.
    public Query queryByRole(final String role, final TextMatch name) {
        return byRole(Variant.QUERY, role, name);
    }

    /// A query for the single element whose own text matches, if any, the equivalent of
    /// `queryByText(text)`, e.g. for `toBeNull()`.
    ///
    /// @param text The element's own text (whitespace collapsed), exactly.
    /// @return The query.
    public Query queryByText(final String text) {
        return byText(Variant.QUERY, TextMatch.exact(text), null);
    }

    /// A query for the single element whose own text matches, if any, the equivalent of
    /// `queryByText(/regex/)`, e.g. for `toBeNull()`.
    ///
    /// @param text Matches the element's own text (whitespace collapsed).
    /// @return The query.
    public Query queryByText(final TextMatch text) {
        return byText(Variant.QUERY, text, null);
    }

    /// A query for the single element whose own text matches and the selector, if any, the equivalent of
    /// `queryByText(text, { selector })`, e.g. for `toBeNull()`.
    ///
    /// @param text Matches the element's own text (whitespace collapsed).
    /// @param selector Only elements matching this CSS selector are included, e.g. `label`.
    /// @return The query.
    public Query queryByText(final TextMatch text, final String selector) {
        return byText(Variant.QUERY, text, selector);
    }

    /// A query for the single element whose own text is the text and that matches the selector, if any,
    /// the equivalent of `queryByText(text, { selector })`.
    ///
    /// @param text     The element's own text (whitespace collapsed), exactly.
    /// @param selector Only elements matching this CSS selector are included, e.g. `label`.
    /// @return The query.
    public Query queryByText(final String text, final String selector) {
        return byText(Variant.QUERY, TextMatch.exact(text), selector);
    }

    /// A query for the single element labelled with matching text, if any, the equivalent of
    /// `queryByLabelText(label)`, e.g. for `toBeNull()`.
    ///
    /// @param label The text of the field's label, `aria-label` or `aria-labelledby` element, exactly.
    /// @return The query.
    public Query queryByLabelText(final String label) {
        return byLabelText(Variant.QUERY, TextMatch.exact(label));
    }

    /// A query for the single element labelled with matching text, if any, the equivalent of
    /// `queryByLabelText(/regex/)`, e.g. for `toBeNull()`.
    ///
    /// @param label Matches the text of the field's label, `aria-label` or `aria-labelledby` element.
    /// @return The query.
    public Query queryByLabelText(final TextMatch label) {
        return byLabelText(Variant.QUERY, label);
    }

    /// A query for the single element with a matching title, if any, the equivalent of
    /// `queryByTitle(title)`, e.g. for `toBeNull()`.
    ///
    /// @param title The element's `title` attribute (or SVG `<title>`), exactly.
    /// @return The query.
    public Query queryByTitle(final String title) {
        return byTitle(Variant.QUERY, TextMatch.exact(title));
    }

    /// A query for the single element with a matching title, if any, the equivalent of
    /// `queryByTitle(/regex/)`, e.g. for `toBeNull()`.
    ///
    /// @param title Matches the element's `title` attribute (or SVG `<title>`).
    /// @return The query.
    public Query queryByTitle(final TextMatch title) {
        return byTitle(Variant.QUERY, title);
    }

    /// A query for the single element with a matching placeholder, if any, the equivalent of
    /// `queryByPlaceholderText(placeholder)`, e.g. for `toBeNull()`.
    ///
    /// @param placeholder The field's `placeholder` attribute, exactly.
    /// @return The query.
    public Query queryByPlaceholderText(final String placeholder) {
        return byPlaceholderText(Variant.QUERY, TextMatch.exact(placeholder));
    }

    /// A query for the single element with a matching placeholder, if any, the equivalent of
    /// `queryByPlaceholderText(/regex/)`, e.g. for `toBeNull()`.
    ///
    /// @param placeholder Matches the field's `placeholder` attribute.
    /// @return The query.
    public Query queryByPlaceholderText(final TextMatch placeholder) {
        return byPlaceholderText(Variant.QUERY, placeholder);
    }

    /// A query for the single element with a matching `data-testid`, if any, the equivalent of
    /// `queryByTestId(testId)`, e.g. for `toBeNull()`.
    ///
    /// @param testId The element's `data-testid` attribute, exactly.
    /// @return The query.
    public Query queryByTestId(final String testId) {
        return byTestId(Variant.QUERY, TextMatch.exact(testId));
    }

    /// A query for the single element with a matching `data-testid`, if any, the equivalent of
    /// `queryByTestId(/regex/)`, e.g. for `toBeNull()`.
    ///
    /// @param testId Matches the element's `data-testid` attribute.
    /// @return The query.
    public Query queryByTestId(final TextMatch testId) {
        return byTestId(Variant.QUERY, testId);
    }

    /// A query for the single element whose current value matches, if any, the equivalent of
    /// `queryByDisplayValue(value)`, e.g. for `toBeNull()`.
    ///
    /// @param value The field's current value (or a select's selected option's text), exactly.
    /// @return The query.
    public Query queryByDisplayValue(final String value) {
        return byDisplayValue(Variant.QUERY, TextMatch.exact(value));
    }

    /// A query for the single element whose current value matches, if any, the equivalent of
    /// `queryByDisplayValue(/regex/)`, e.g. for `toBeNull()`.
    ///
    /// @param value Matches the field's current value (or a select's selected option's text).
    /// @return The query.
    public Query queryByDisplayValue(final TextMatch value) {
        return byDisplayValue(Variant.QUERY, value);
    }

    // ---------- queryAllBy* ----------

    /// A query for all the elements with the role (perhaps none), the equivalent of `queryAllByRole(role)`.
    ///
    /// @param role The ARIA role, e.g. `button`.
    /// @return The query.
    public Query queryAllByRole(final String role) {
        return byRole(Variant.QUERY_ALL, role, null);
    }

    /// A query for all the elements with the role and accessible name (perhaps none), the equivalent of
    /// `queryAllByRole(role, { name })`.
    ///
    /// @param role The ARIA role, e.g. `button`.
    /// @param name The accessible name, exactly, e.g. the button's text.
    /// @return The query.
    public Query queryAllByRole(final String role, final String name) {
        return byRole(Variant.QUERY_ALL, role, name != null
                ? TextMatch.exact(name)
                : null);
    }

    /// A query for all the elements with the role and a matching accessible name (perhaps none), the
    /// equivalent of `queryAllByRole(role, { name: /regex/ })`.
    ///
    /// @param role The ARIA role, e.g. `button`.
    /// @param name Matches the accessible name, e.g. `TextMatch.exactIgnoreCase("ok")`.
    /// @return The query.
    public Query queryAllByRole(final String role, final TextMatch name) {
        return byRole(Variant.QUERY_ALL, role, name);
    }

    /// A query for all the elements whose own text matches (perhaps none), the equivalent of
    /// `queryAllByText(text)`.
    ///
    /// @param text The element's own text (whitespace collapsed), exactly.
    /// @return The query.
    public Query queryAllByText(final String text) {
        return byText(Variant.QUERY_ALL, TextMatch.exact(text), null);
    }

    /// A query for all the elements whose own text matches (perhaps none), the equivalent of
    /// `queryAllByText(/regex/)`.
    ///
    /// @param text Matches the element's own text (whitespace collapsed).
    /// @return The query.
    public Query queryAllByText(final TextMatch text) {
        return byText(Variant.QUERY_ALL, text, null);
    }

    /// A query for all the elements whose own text matches and the selector (perhaps none), the equivalent
    /// of `queryAllByText(text, { selector })`.
    ///
    /// @param text Matches the element's own text (whitespace collapsed).
    /// @param selector Only elements matching this CSS selector are included, e.g. `label`.
    /// @return The query.
    public Query queryAllByText(final TextMatch text, final String selector) {
        return byText(Variant.QUERY_ALL, text, selector);
    }

    /// A query for all the elements whose own text is the text and that match the selector (perhaps
    /// none), the equivalent of `queryAllByText(text, { selector })`.
    ///
    /// @param text     The element's own text (whitespace collapsed), exactly.
    /// @param selector Only elements matching this CSS selector are included, e.g. `label`.
    /// @return The query.
    public Query queryAllByText(final String text, final String selector) {
        return byText(Variant.QUERY_ALL, TextMatch.exact(text), selector);
    }

    /// A query for all the elements labelled with matching text (perhaps none), the equivalent of
    /// `queryAllByLabelText(label)`.
    ///
    /// @param label The text of the field's label, `aria-label` or `aria-labelledby` element, exactly.
    /// @return The query.
    public Query queryAllByLabelText(final String label) {
        return byLabelText(Variant.QUERY_ALL, TextMatch.exact(label));
    }

    /// A query for all the elements labelled with matching text (perhaps none), the equivalent of
    /// `queryAllByLabelText(/regex/)`.
    ///
    /// @param label Matches the text of the field's label, `aria-label` or `aria-labelledby` element.
    /// @return The query.
    public Query queryAllByLabelText(final TextMatch label) {
        return byLabelText(Variant.QUERY_ALL, label);
    }

    /// A query for all the elements with a matching title (perhaps none), the equivalent of
    /// `queryAllByTitle(title)`.
    ///
    /// @param title The element's `title` attribute (or SVG `<title>`), exactly.
    /// @return The query.
    public Query queryAllByTitle(final String title) {
        return byTitle(Variant.QUERY_ALL, TextMatch.exact(title));
    }

    /// A query for all the elements with a matching title (perhaps none), the equivalent of
    /// `queryAllByTitle(/regex/)`.
    ///
    /// @param title Matches the element's `title` attribute (or SVG `<title>`).
    /// @return The query.
    public Query queryAllByTitle(final TextMatch title) {
        return byTitle(Variant.QUERY_ALL, title);
    }

    /// A query for all the elements with a matching placeholder (perhaps none), the equivalent of
    /// `queryAllByPlaceholderText(placeholder)`.
    ///
    /// @param placeholder The field's `placeholder` attribute, exactly.
    /// @return The query.
    public Query queryAllByPlaceholderText(final String placeholder) {
        return byPlaceholderText(Variant.QUERY_ALL, TextMatch.exact(placeholder));
    }

    /// A query for all the elements with a matching placeholder (perhaps none), the equivalent of
    /// `queryAllByPlaceholderText(/regex/)`.
    ///
    /// @param placeholder Matches the field's `placeholder` attribute.
    /// @return The query.
    public Query queryAllByPlaceholderText(final TextMatch placeholder) {
        return byPlaceholderText(Variant.QUERY_ALL, placeholder);
    }

    /// A query for all the elements with a matching `data-testid` (perhaps none), the equivalent of
    /// `queryAllByTestId(testId)`.
    ///
    /// @param testId The element's `data-testid` attribute, exactly.
    /// @return The query.
    public Query queryAllByTestId(final String testId) {
        return byTestId(Variant.QUERY_ALL, TextMatch.exact(testId));
    }

    /// A query for all the elements with a matching `data-testid` (perhaps none), the equivalent of
    /// `queryAllByTestId(/regex/)`.
    ///
    /// @param testId Matches the element's `data-testid` attribute.
    /// @return The query.
    public Query queryAllByTestId(final TextMatch testId) {
        return byTestId(Variant.QUERY_ALL, testId);
    }

    /// A query for all the elements whose current value matches (perhaps none), the equivalent of
    /// `queryAllByDisplayValue(value)`.
    ///
    /// @param value The field's current value (or a select's selected option's text), exactly.
    /// @return The query.
    public Query queryAllByDisplayValue(final String value) {
        return byDisplayValue(Variant.QUERY_ALL, TextMatch.exact(value));
    }

    /// A query for all the elements whose current value matches (perhaps none), the equivalent of
    /// `queryAllByDisplayValue(/regex/)`.
    ///
    /// @param value Matches the field's current value (or a select's selected option's text).
    /// @return The query.
    public Query queryAllByDisplayValue(final TextMatch value) {
        return byDisplayValue(Variant.QUERY_ALL, value);
    }

    // ---------- findBy* ----------

    /// Waits for a single element with the role, then returns a query for it, the equivalent of
    /// `findByRole(role)`.
    ///
    /// @param role The ARIA role, e.g. `button`.
    /// @return The query.
    public Query findByRole(final String role) {
        return byRole(Variant.FIND, role, null);
    }

    /// Waits for a single element with the role and accessible name, then returns a query for it, the
    /// equivalent of `findByRole(role, { name })`.
    ///
    /// @param role The ARIA role, e.g. `button`.
    /// @param name The accessible name, exactly, e.g. the button's text.
    /// @return The query.
    public Query findByRole(final String role, final String name) {
        return byRole(Variant.FIND, role, name != null
                ? TextMatch.exact(name)
                : null);
    }

    /// Waits for a single element with the role and a matching accessible name, then returns a query for
    /// it, the equivalent of `findByRole(role, { name: /regex/ })`.
    ///
    /// @param role The ARIA role, e.g. `button`.
    /// @param name Matches the accessible name, e.g. `TextMatch.exactIgnoreCase("ok")`.
    /// @return The query.
    public Query findByRole(final String role, final TextMatch name) {
        return byRole(Variant.FIND, role, name);
    }

    /// Waits for a single element whose own text matches, then returns a query for it, the equivalent of
    /// `findByText(text)`.
    ///
    /// @param text The element's own text (whitespace collapsed), exactly.
    /// @return The query.
    public Query findByText(final String text) {
        return byText(Variant.FIND, TextMatch.exact(text), null);
    }

    /// Waits for a single element whose own text matches, then returns a query for it, the equivalent of
    /// `findByText(/regex/)`.
    ///
    /// @param text Matches the element's own text (whitespace collapsed).
    /// @return The query.
    public Query findByText(final TextMatch text) {
        return byText(Variant.FIND, text, null);
    }

    /// Waits for a single element whose own text matches and the selector, then returns a query for it, the
    /// equivalent of `findByText(text, { selector })`.
    ///
    /// @param text Matches the element's own text (whitespace collapsed).
    /// @param selector Only elements matching this CSS selector are included, e.g. `label`.
    /// @return The query.
    public Query findByText(final TextMatch text, final String selector) {
        return byText(Variant.FIND, text, selector);
    }

    /// Waits for a single element whose own text is the text and that matches the selector, then
    /// returns a query for it, the equivalent of `findByText(text, { selector })`.
    ///
    /// @param text     The element's own text (whitespace collapsed), exactly.
    /// @param selector Only elements matching this CSS selector are included, e.g. `label`.
    /// @return The query.
    public Query findByText(final String text, final String selector) {
        return byText(Variant.FIND, TextMatch.exact(text), selector);
    }

    /// Waits for a single element labelled with matching text, then returns a query for it, the equivalent
    /// of `findByLabelText(label)`.
    ///
    /// @param label The text of the field's label, `aria-label` or `aria-labelledby` element, exactly.
    /// @return The query.
    public Query findByLabelText(final String label) {
        return byLabelText(Variant.FIND, TextMatch.exact(label));
    }

    /// Waits for a single element labelled with matching text, then returns a query for it, the equivalent
    /// of `findByLabelText(/regex/)`.
    ///
    /// @param label Matches the text of the field's label, `aria-label` or `aria-labelledby` element.
    /// @return The query.
    public Query findByLabelText(final TextMatch label) {
        return byLabelText(Variant.FIND, label);
    }

    /// Waits for a single element with a matching title, then returns a query for it, the equivalent of
    /// `findByTitle(title)`.
    ///
    /// @param title The element's `title` attribute (or SVG `<title>`), exactly.
    /// @return The query.
    public Query findByTitle(final String title) {
        return byTitle(Variant.FIND, TextMatch.exact(title));
    }

    /// Waits for a single element with a matching title, then returns a query for it, the equivalent of
    /// `findByTitle(/regex/)`.
    ///
    /// @param title Matches the element's `title` attribute (or SVG `<title>`).
    /// @return The query.
    public Query findByTitle(final TextMatch title) {
        return byTitle(Variant.FIND, title);
    }

    /// Waits for a single element with a matching placeholder, then returns a query for it, the equivalent
    /// of `findByPlaceholderText(placeholder)`.
    ///
    /// @param placeholder The field's `placeholder` attribute, exactly.
    /// @return The query.
    public Query findByPlaceholderText(final String placeholder) {
        return byPlaceholderText(Variant.FIND, TextMatch.exact(placeholder));
    }

    /// Waits for a single element with a matching placeholder, then returns a query for it, the equivalent
    /// of `findByPlaceholderText(/regex/)`.
    ///
    /// @param placeholder Matches the field's `placeholder` attribute.
    /// @return The query.
    public Query findByPlaceholderText(final TextMatch placeholder) {
        return byPlaceholderText(Variant.FIND, placeholder);
    }

    /// Waits for a single element with a matching `data-testid`, then returns a query for it, the
    /// equivalent of `findByTestId(testId)`.
    ///
    /// @param testId The element's `data-testid` attribute, exactly.
    /// @return The query.
    public Query findByTestId(final String testId) {
        return byTestId(Variant.FIND, TextMatch.exact(testId));
    }

    /// Waits for a single element with a matching `data-testid`, then returns a query for it, the
    /// equivalent of `findByTestId(/regex/)`.
    ///
    /// @param testId Matches the element's `data-testid` attribute.
    /// @return The query.
    public Query findByTestId(final TextMatch testId) {
        return byTestId(Variant.FIND, testId);
    }

    /// Waits for a single element whose current value matches, then returns a query for it, the equivalent
    /// of `findByDisplayValue(value)`.
    ///
    /// @param value The field's current value (or a select's selected option's text), exactly.
    /// @return The query.
    public Query findByDisplayValue(final String value) {
        return byDisplayValue(Variant.FIND, TextMatch.exact(value));
    }

    /// Waits for a single element whose current value matches, then returns a query for it, the equivalent
    /// of `findByDisplayValue(/regex/)`.
    ///
    /// @param value Matches the field's current value (or a select's selected option's text).
    /// @return The query.
    public Query findByDisplayValue(final TextMatch value) {
        return byDisplayValue(Variant.FIND, value);
    }

    // ---------- findAllBy* ----------

    /// Waits for at least one element with the role, then returns a query for them all, the equivalent of
    /// `findAllByRole(role)`.
    ///
    /// @param role The ARIA role, e.g. `button`.
    /// @return The query.
    public Query findAllByRole(final String role) {
        return byRole(Variant.FIND_ALL, role, null);
    }

    /// Waits for at least one element with the role and accessible name, then returns a query for them all,
    /// the equivalent of `findAllByRole(role, { name })`.
    ///
    /// @param role The ARIA role, e.g. `button`.
    /// @param name The accessible name, exactly, e.g. the button's text.
    /// @return The query.
    public Query findAllByRole(final String role, final String name) {
        return byRole(Variant.FIND_ALL, role, name != null
                ? TextMatch.exact(name)
                : null);
    }

    /// Waits for at least one element with the role and a matching accessible name, then returns a query
    /// for them all, the equivalent of `findAllByRole(role, { name: /regex/ })`.
    ///
    /// @param role The ARIA role, e.g. `button`.
    /// @param name Matches the accessible name, e.g. `TextMatch.exactIgnoreCase("ok")`.
    /// @return The query.
    public Query findAllByRole(final String role, final TextMatch name) {
        return byRole(Variant.FIND_ALL, role, name);
    }

    /// Waits for at least one element whose own text matches, then returns a query for them all, the
    /// equivalent of `findAllByText(text)`.
    ///
    /// @param text The element's own text (whitespace collapsed), exactly.
    /// @return The query.
    public Query findAllByText(final String text) {
        return byText(Variant.FIND_ALL, TextMatch.exact(text), null);
    }

    /// Waits for at least one element whose own text matches, then returns a query for them all, the
    /// equivalent of `findAllByText(/regex/)`.
    ///
    /// @param text Matches the element's own text (whitespace collapsed).
    /// @return The query.
    public Query findAllByText(final TextMatch text) {
        return byText(Variant.FIND_ALL, text, null);
    }

    /// Waits for at least one element whose own text matches and the selector, then returns a query for
    /// them all, the equivalent of `findAllByText(text, { selector })`.
    ///
    /// @param text Matches the element's own text (whitespace collapsed).
    /// @param selector Only elements matching this CSS selector are included, e.g. `label`.
    /// @return The query.
    public Query findAllByText(final TextMatch text, final String selector) {
        return byText(Variant.FIND_ALL, text, selector);
    }

    /// Waits for at least one element whose own text is the text and that matches the selector, then
    /// returns a query for them all, the equivalent of `findAllByText(text, { selector })`.
    ///
    /// @param text     The element's own text (whitespace collapsed), exactly.
    /// @param selector Only elements matching this CSS selector are included, e.g. `label`.
    /// @return The query.
    public Query findAllByText(final String text, final String selector) {
        return byText(Variant.FIND_ALL, TextMatch.exact(text), selector);
    }

    /// Waits for at least one element labelled with matching text, then returns a query for them all, the
    /// equivalent of `findAllByLabelText(label)`.
    ///
    /// @param label The text of the field's label, `aria-label` or `aria-labelledby` element, exactly.
    /// @return The query.
    public Query findAllByLabelText(final String label) {
        return byLabelText(Variant.FIND_ALL, TextMatch.exact(label));
    }

    /// Waits for at least one element labelled with matching text, then returns a query for them all, the
    /// equivalent of `findAllByLabelText(/regex/)`.
    ///
    /// @param label Matches the text of the field's label, `aria-label` or `aria-labelledby` element.
    /// @return The query.
    public Query findAllByLabelText(final TextMatch label) {
        return byLabelText(Variant.FIND_ALL, label);
    }

    /// Waits for at least one element with a matching title, then returns a query for them all, the
    /// equivalent of `findAllByTitle(title)`.
    ///
    /// @param title The element's `title` attribute (or SVG `<title>`), exactly.
    /// @return The query.
    public Query findAllByTitle(final String title) {
        return byTitle(Variant.FIND_ALL, TextMatch.exact(title));
    }

    /// Waits for at least one element with a matching title, then returns a query for them all, the
    /// equivalent of `findAllByTitle(/regex/)`.
    ///
    /// @param title Matches the element's `title` attribute (or SVG `<title>`).
    /// @return The query.
    public Query findAllByTitle(final TextMatch title) {
        return byTitle(Variant.FIND_ALL, title);
    }

    /// Waits for at least one element with a matching placeholder, then returns a query for them all, the
    /// equivalent of `findAllByPlaceholderText(placeholder)`.
    ///
    /// @param placeholder The field's `placeholder` attribute, exactly.
    /// @return The query.
    public Query findAllByPlaceholderText(final String placeholder) {
        return byPlaceholderText(Variant.FIND_ALL, TextMatch.exact(placeholder));
    }

    /// Waits for at least one element with a matching placeholder, then returns a query for them all, the
    /// equivalent of `findAllByPlaceholderText(/regex/)`.
    ///
    /// @param placeholder Matches the field's `placeholder` attribute.
    /// @return The query.
    public Query findAllByPlaceholderText(final TextMatch placeholder) {
        return byPlaceholderText(Variant.FIND_ALL, placeholder);
    }

    /// Waits for at least one element with a matching `data-testid`, then returns a query for them all, the
    /// equivalent of `findAllByTestId(testId)`.
    ///
    /// @param testId The element's `data-testid` attribute, exactly.
    /// @return The query.
    public Query findAllByTestId(final String testId) {
        return byTestId(Variant.FIND_ALL, TextMatch.exact(testId));
    }

    /// Waits for at least one element with a matching `data-testid`, then returns a query for them all, the
    /// equivalent of `findAllByTestId(/regex/)`.
    ///
    /// @param testId Matches the element's `data-testid` attribute.
    /// @return The query.
    public Query findAllByTestId(final TextMatch testId) {
        return byTestId(Variant.FIND_ALL, testId);
    }

    /// Waits for at least one element whose current value matches, then returns a query for them all, the
    /// equivalent of `findAllByDisplayValue(value)`.
    ///
    /// @param value The field's current value (or a select's selected option's text), exactly.
    /// @return The query.
    public Query findAllByDisplayValue(final String value) {
        return byDisplayValue(Variant.FIND_ALL, TextMatch.exact(value));
    }

    /// Waits for at least one element whose current value matches, then returns a query for them all, the
    /// equivalent of `findAllByDisplayValue(/regex/)`.
    ///
    /// @param value Matches the field's current value (or a select's selected option's text).
    /// @return The query.
    public Query findAllByDisplayValue(final TextMatch value) {
        return byDisplayValue(Variant.FIND_ALL, value);
    }

    // ---------- CSS selectors ----------

    /// @param selector A CSS selector.
    /// @return A query for the first element matching the selector, the equivalent of
    /// `container.querySelector(selector)`; several matching isn't an error. As an expectation's
    /// subject, e.g. `toBeNull()`, it matches at most one element.
    public Query querySelector(final String selector) {
        return build(Variant.SELECTOR, "", Expectation.quote(selector),
                "Unable to find an element matching: " + selector,
                "Found multiple elements matching: " + selector,
                container -> Dom.querySelectorAll(container, selector));
    }

    /// @param selector A CSS selector, e.g. `:scope > *` for the children.
    /// @return A query for all the elements matching the selector, the equivalent of
    /// `container.querySelectorAll(selector)`.
    public Query querySelectorAll(final String selector) {
        return build(Variant.SELECTOR_ALL, "", Expectation.quote(selector),
                "Unable to find an element matching: " + selector,
                "Found multiple elements matching: " + selector,
                container -> Dom.querySelectorAll(container, selector));
    }

    private Query byRole(final Variant variant, final String role, final TextMatch name) {
        Objects.requireNonNull(role, "role");
        final String args = Expectation.quote(role) + (name != null
                ? ", { name: " + name.describe() + " }"
                : "");
        final String nameText = name != null
                ? " and name " + name.describe()
                : "";
        return build(variant, "ByRole", args,
                "Unable to find an accessible element with the role " + Expectation.quote(role) + nameText,
                "Found multiple elements with the role " + Expectation.quote(role) + nameText,
                container -> Dom.queryAllByRole(container, role, name));
    }

    private Query byText(final Variant variant, final TextMatch text, final String selector) {
        Objects.requireNonNull(text, "text");
        final String args = text.describe() + (selector != null
                ? ", { selector: " + Expectation.quote(selector) + " }"
                : "");
        final String selectorText = selector != null
                ? " matching " + selector
                : "";
        return build(variant, "ByText", args,
                "Unable to find an element with the text: " + text.describe() + selectorText,
                "Found multiple elements with the text: " + text.describe() + selectorText,
                container -> Dom.queryAllByText(container, text, selector));
    }

    private Query byLabelText(final Variant variant, final TextMatch label) {
        Objects.requireNonNull(label, "label");
        return build(variant, "ByLabelText", label.describe(),
                "Unable to find a label with the text of: " + label.describe(),
                "Found multiple elements with the text of: " + label.describe(),
                container -> Dom.queryAllByLabelText(container, label));
    }

    private Query byTitle(final Variant variant, final TextMatch title) {
        Objects.requireNonNull(title, "title");
        return build(variant, "ByTitle", title.describe(),
                "Unable to find an element with the title: " + title.describe(),
                "Found multiple elements with the title: " + title.describe(),
                container -> Dom.queryAllByTitle(container, title));
    }

    private Query byPlaceholderText(final Variant variant, final TextMatch placeholder) {
        Objects.requireNonNull(placeholder, "placeholder");
        return build(variant, "ByPlaceholderText", placeholder.describe(),
                "Unable to find an element with the placeholder text of: " + placeholder.describe(),
                "Found multiple elements with the placeholder text of: " + placeholder.describe(),
                container -> Dom.queryAllByAttribute(container, "placeholder", placeholder));
    }

    private Query byTestId(final Variant variant, final TextMatch testId) {
        Objects.requireNonNull(testId, "testId");
        return build(variant, "ByTestId", testId.describe(),
                "Unable to find an element by: [data-testid=" + testId.describe() + "]",
                "Found multiple elements by: [data-testid=" + testId.describe() + "]",
                container -> Dom.queryAllByAttribute(container, "data-testid", testId));
    }

    private Query byDisplayValue(final Variant variant, final TextMatch value) {
        Objects.requireNonNull(value, "value");
        return build(variant, "ByDisplayValue", value.describe(),
                "Unable to find an element with the display value: " + value.describe(),
                "Found multiple elements with the display value: " + value.describe(),
                container -> Dom.queryAllByDisplayValue(container, value));
    }

    private Query build(final Variant variant,
                        final String by,
                        final String args,
                        final String notFoundMessage,
                        final String multipleMessage,
                        final Function<Element, JsArray<Element>> finder) {
        final Query query = new Query(scope, variant.singleMethod + by, variant.allMethod + by, args,
                variant.all, notFoundMessage, multipleMessage, variant == Variant.SELECTOR, finder);
        if (variant.findMethod != null) {
            final String description = query.describeAs(variant.findMethod + by);
            addStep(PlayStep.group(Kind.WAIT_FOR, root -> description, List.of(
                    PlayStep.action(root -> description, root -> {
                        if (variant.all) {
                            if (query.countIn(root) == 0) {
                                throw new PlayException(notFoundMessage);
                            }
                        } else {
                            query.resolve(root);
                        }
                    }))));
        }
        return query;
    }

    // ---------- Spies ----------

    /// @param name The spy's name, e.g. `onClick`, as the story passed to `StoryContext.fn(name)`.
    /// @return The story's spy with the name, the equivalent of `args.onClick` when it's a `fn()`,
    /// e.g. for `play.expect(play.spy("onClick")).toHaveBeenCalled()`.
    public Spy spy(final String name) {
        return Spy.playSpy(name, this);
    }

    // ---------- User events ----------

    /// Clicks an element, as `userEvent.click` does: moves the mouse onto it (from the body, as
    /// each `userEvent` call in a React play starts with a new pointer), presses and releases the
    /// main button (putting the caret at the end of a field's text and moving the focus) and
    /// fires `click`. As in user-event, the step fails if the element has (or inherits)
    /// `pointer-events: none`, and a disabled element (or one in a disabled form control or
    /// field set) only gets the pointer events and the mouse moving over it, no `mousedown`,
    /// `mouseup` or `click`. See `Dom.click` for the details.
    ///
    /// @param target The element.
    public void click(final Query target) {
        addUserEvent("click", target, null, root -> Dom.click(target.resolve(root), 1, 0, 0, true));
    }

    /// Double clicks an element, as `userEvent.dblClick` does: two clicks, then `dblclick`. As in
    /// user-event, the second press selects the last word of a field's text.
    ///
    /// @param target The element.
    public void dblClick(final Query target) {
        addUserEvent("dblClick", target, null, root -> Dom.click(target.resolve(root), 2, 0, 0, true));
    }

    /// Right clicks an element, as `userEvent.pointer({ keys: "[MouseRight]", target })` does:
    /// presses and releases the secondary button, firing `contextmenu` and `auxclick`. As in
    /// user-event, the mouse isn't moved onto the element first, so there are no `mouseover`,
    /// `mouseenter` or `mousemove` events.
    ///
    /// @param target The element.
    public void rightClick(final Query target) {
        addStep(PlayStep.action(root -> "userEvent.pointer({ keys: \"[MouseRight]\", target: "
                                        + target.describe() + " })",
                root -> Dom.click(target.resolve(root), 1, 2, 0, false)));
    }

    /// Moves the mouse onto an element, as `userEvent.hover` does: from the body (as each
    /// `userEvent` call in a React play starts with a new pointer), so `pointerout` and
    /// `mouseout` fire on the body, then the over, enter and move events on the element.
    ///
    /// @param target The element.
    public void hover(final Query target) {
        addUserEvent("hover", target, null, root -> Dom.hover(target.resolve(root), true));
    }

    /// Moves the mouse off an element, as `userEvent.unhover` does. As in a React play, where each
    /// `userEvent` call starts with a new pointer over the body, this only fires `pointermove` and
    /// `mousemove` on the body: it does NOT fire `mouseout` or `mouseleave` on the element. Use
    /// `play.fireEvent().mouseOut(...)` etc. where the React play does.
    ///
    /// @param target The element.
    public void unhover(final Query target) {
        addUserEvent("unhover", target, null, root -> Dom.hover(target.resolve(root), false));
    }

    /// Clicks a field then types into it, as `userEvent.type` does, appending to its text. The
    /// text may include special keys in user-event's syntax, e.g. `abc{Enter}` (see
    /// [#keyboard(String)]). The field fires `beforeinput` and `input` for each character and
    /// `change` when it loses the focus. As in user-event, typing into a disabled field does
    /// nothing, typing into a read only field only fires the key events, `maxlength` is honoured
    /// and a number field only takes text a browser accepts (keeping e.g. `1.` as typed until
    /// `1.5` is complete). The click puts the caret at the end of the text (unless its
    /// `pointerdown` or `mousedown` is cancelled), and typing into a content editable element
    /// inserts the text there, keeping its markup. Keys still held at the end (e.g. `{Shift>}`)
    /// are released.
    ///
    /// @param target The field.
    /// @param text   The text to type.
    public void type(final Query target, final String text) {
        final List<KeyAction> actions = Keys.parse(text);
        addUserEvent("type", target, text, root -> typeInto(target.resolve(root), actions));
    }

    /// Does what `userEvent.type` does: nothing if the element is disabled, otherwise clicks it
    /// (putting the caret at the end of its text) and presses the keys, releasing any still held
    /// at the end.
    ///
    /// @param element The field.
    /// @param actions The keys, from [Keys#parse(String)].
    static void typeInto(final Element element, final List<KeyAction> actions) {
        if (Dom.hasDisabledProperty(element)) {
            // As user-event, which does nothing
            return;
        }
        Dom.click(element, 1, 0, 0, true);
        Keyboard.press(element, actions, true);
    }

    /// Clears a field, as `userEvent.clear` does: focuses it, selects its text and deletes it. As in
    /// user-event, the step fails if the field is disabled or read only.
    ///
    /// @param target The field.
    public void clear(final Query target) {
        addUserEvent("clear", target, null, root -> Dom.clear(target.resolve(root)));
    }

    /// Selects options of a `<select>` (or ARIA listbox), as `userEvent.selectOptions` does.
    ///
    /// @param target The select or listbox.
    /// @param values The values or texts of the options.
    public void selectOptions(final Query target, final String... values) {
        final StringBuilder args = new StringBuilder();
        for (final String value : values) {
            if (args.length() > 0) {
                args.append(", ");
            }
            args.append(Expectation.quote(value));
        }
        final String text = values.length == 1
                ? args.toString()
                : "[" + args + "]";
        addStep(PlayStep.action(root -> "userEvent.selectOptions(" + target.describe() + ", " + text + ")",
                root -> {
                    final JsArrayString array = JavaScriptObject.createArray().cast();
                    for (final String value : values) {
                        array.push(value);
                    }
                    final JsArrayString missing = Dom.selectOptions(target.resolve(root), array);
                    if (missing.length() > 0) {
                        throw new PlayException("Value \"" + missing.get(0) + "\" not found in options");
                    }
                }));
    }

    /// Presses keys on the focused element, as `userEvent.keyboard` does, e.g. `{Enter}`,
    /// `{ArrowDown}`, `{Escape}`, `abc`, `{Shift}{Shift}`, `{Alt>}w{/Alt}` or
    /// `{Control>}a{/Control}`. See [Keys] for the syntax.
    ///
    /// @param keys The keys.
    public void keyboard(final String keys) {
        final List<KeyAction> actions = Keys.parse(keys);
        addStep(PlayStep.action(root -> "userEvent.keyboard(" + Expectation.quote(keys) + ")",
                root -> Keyboard.press(root, actions, false)));
    }

    /// Presses Tab to move the focus to the next focusable element, as `userEvent.tab()` does.
    public void tab() {
        tab(false);
    }

    /// Presses Tab (or Shift+Tab) to move the focus, as `userEvent.tab({ shift })` does.
    ///
    /// @param shift True to move the focus to the previous focusable element.
    public void tab(final boolean shift) {
        final List<KeyAction> actions = Keys.parse(shift
                ? "{Shift>}{Tab}{/Shift}"
                : "{Tab}");
        addStep(PlayStep.action(root -> shift
                        ? "userEvent.tab({ shift: true })"
                        : "userEvent.tab()",
                root -> Keyboard.press(root, actions, false)));
    }

    /// Chooses a file in a file input, as `userEvent.upload(input, new File([content], name,
    /// { type }))` does.
    ///
    /// @param target   The `<input type="file">`.
    /// @param fileName The file's name, e.g. `data.txt`.
    /// @param content  The file's content.
    /// @param mimeType The file's type, e.g. `text/plain`.
    public void upload(final Query target, final String fileName, final String content, final String mimeType) {
        addStep(PlayStep.action(root -> "userEvent.upload(" + target.describe() + ", new File(["
                                        + Expectation.quote(content) + "], " + Expectation.quote(fileName)
                                        + ", { type: " + Expectation.quote(mimeType) + " }))",
                root -> Dom.upload(target.resolve(root), fileName, content, mimeType)));
    }

    /// @return A way to fire single DOM events, the equivalent of Testing Library's `fireEvent`,
    /// e.g. `play.fireEvent().contextMenu(play.getByText("Countries"))`.
    public FireEvent fireEvent() {
        return new FireEvent(this);
    }

    private void addUserEvent(final String method,
                              final Query target,
                              final String text,
                              final Consumer<Element> action) {
        Objects.requireNonNull(target, "target");
        addStep(PlayStep.action(root -> "userEvent." + method + "(" + target.describe()
                                        + (text != null
                ? ", " + Expectation.quote(text)
                : "") + ")", action));
    }

    /// Quotes a value for use as a string in a CSS selector, escaping characters that would end
    /// or break the string, e.g. `a"b` => `"a\"b"`.
    ///
    /// @param value The value, e.g. a test id.
    /// @return The quoted value.
    static String cssString(final String value) {
        final StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            final char chr = value.charAt(i);
            if (chr == '"' || chr == '\\') {
                sb.append('\\').append(chr);
            } else if (chr < ' ' || chr == 0x7F) {
                // Control characters must be written as hex escapes, which end with a space
                sb.append('\\').append(Integer.toHexString(chr)).append(' ');
            } else {
                sb.append(chr);
            }
        }
        return sb.append('"').toString();
    }

    // ---------- Expectations ----------

    /// @param target The element (or, for a `getAllBy`/`queryAllBy` query, elements).
    /// @return An expectation about the element, e.g. `expect(element).toBeVisible()`.
    public Expectation expect(final Query target) {
        return new Expectation(this, Objects.requireNonNull(target, "target"), false);
    }

    /// @param spy The spy, e.g. from [#spy(String)].
    /// @return An expectation about the spy's calls, e.g. `expect(spy).toHaveBeenCalled()`.
    public SpyExpectation expect(final Spy spy) {
        return new SpyExpectation(this, spy, false);
    }

    /// @param value Reads the value when the step runs (each time, inside a `waitFor`), e.g.
    ///              `() -> saved.size()` or a [Value] from a [Query], e.g. `query.textContent()`.
    /// @return An expectation about the value, e.g. `expect(value).toBe(1)`.
    public ValueExpectation expect(final Supplier<?> value) {
        Objects.requireNonNull(value, "value");
        final String label = value instanceof Value
                ? ((Value<?>) value).getLabel()
                : "value";
        return new ValueExpectation(this, label, value, false);
    }

    /// @param label How the value is shown in the Interactions addon if it can't be read, e.g.
    ///              `saved.size()`.
    /// @param value Reads the value when the step runs (each time, inside a `waitFor`).
    /// @return An expectation about the value, e.g. `expect(value).toBe(1)`.
    public ValueExpectation expect(final String label, final Supplier<?> value) {
        return new ValueExpectation(this, label, Objects.requireNonNull(value, "value"), false);
    }

    // ---------- Grouping, waiting and custom steps ----------

    /// Groups steps under a label, the equivalent of Storybook's `step(label, ...)`.
    ///
    /// @param label The label shown in the Interactions addon.
    /// @param body  Adds the steps in the group.
    public void step(final String label, final Runnable body) {
        addGroup(Kind.STEP, root -> "step(" + Expectation.quote(label) + ")", body, PlayStep.DEFAULT_TIMEOUT_MILLIS);
    }

    /// Retries steps until they all pass or a second has passed, the equivalent of Testing
    /// Library's `waitFor(...)`. Any steps can be retried: expectations about elements, values and
    /// spies, and even user events.
    ///
    /// @param body Adds the steps to retry, usually expectations.
    public void waitFor(final Runnable body) {
        waitFor(PlayStep.DEFAULT_TIMEOUT_MILLIS, body);
    }

    /// Retries steps until they all pass or the timeout passes, the equivalent of Testing
    /// Library's `waitFor(..., { timeout })`.
    ///
    /// @param timeoutMillis How long to retry for, in milliseconds.
    /// @param body          Adds the steps to retry, usually expectations.
    public void waitFor(final int timeoutMillis, final Runnable body) {
        final String description = timeoutMillis == PlayStep.DEFAULT_TIMEOUT_MILLIS
                ? "waitFor(anonymous)"
                : "waitFor(anonymous, { timeout: " + timeoutMillis + " })";
        addGroup(Kind.WAIT_FOR, root -> description, body, timeoutMillis);
    }

    /// Pauses before the next step, the equivalent of
    /// `await new Promise((r) => setTimeout(r, millis))`. Prefer [#waitFor(Runnable)], which
    /// carries on as soon as the condition is met. Inside a `waitFor` it does nothing.
    ///
    /// @param millis How long to pause, in milliseconds.
    public void sleep(final int millis) {
        addStep(PlayStep.sleep(root -> "sleep(" + millis + ")", millis));
    }

    /// Adds a step that reads a value as it runs, for later steps to use, the equivalent of
    /// `const before = spy.mock.calls.length;` part way through a React play, e.g.
    /// ```
    /// final Value<Integer> before = play.capture("before", spy.callCount());
    /// play.click(button);
    /// play.expect("calls since", () -> spy.getCallCount() - before.get()).toBe(1);
    /// ```
    /// Expected values (e.g. the argument of `toBe`) are fixed when the play function runs, so
    /// put the captured value in the value being checked, as above.
    ///
    /// @param name  The name shown in the Interactions addon, e.g. `before`.
    /// @param value Reads the value when the step runs.
    /// @param <T>   The type of the value.
    /// @return The value as read by the step. Reading it before the step has run (in this run of
    /// the steps) throws a [PlayException].
    public <T> Value<T> capture(final String name, final Supplier<T> value) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        final Captured<T> captured = new Captured<>(name);
        final String label = value instanceof Value
                ? ((Value<?>) value).getLabel()
                : "value";
        addStep(PlayStep.action(root -> "const " + name + " = " + (captured.isSet()
                        ? Values.format(captured.value)
                        : label),
                root -> captured.set(value.get())));
        return Value.of(name, captured::get);
    }

    /// Adds a step that runs some code, for anything the other methods can't do, e.g. calling a
    /// widget's method or recording a value for a later expectation. [Query] values (e.g.
    /// `query.element().get()`) can be read in it.
    ///
    /// @param description How the step is shown in the Interactions addon.
    /// @param action      What the step does; it fails if this throws.
    public void run(final String description, final Runnable action) {
        Objects.requireNonNull(action, "action");
        addStep(PlayStep.action(root -> description, root -> action.run()));
    }

    private void addGroup(final Kind kind,
                          final Function<Element, String> describer,
                          final Runnable body,
                          final int timeoutMillis) {
        checkBuilding();
        final List<PlayStep> children = new ArrayList<>();
        build.targets.push(children);
        try {
            body.run();
        } finally {
            build.targets.pop();
        }
        addStep(PlayStep.group(kind, describer, children, timeoutMillis));
    }


    // --------------------------------------------------------------------------------


    /// The variants of Testing Library's queries.
    private enum Variant {
        GET("get", "getAll", false, null),
        GET_ALL("get", "getAll", true, null),
        QUERY("query", "queryAll", false, null),
        QUERY_ALL("query", "queryAll", true, null),
        // The query a find method returns is described as a get, as it's then known to be there
        FIND("get", "getAll", false, "find"),
        FIND_ALL("get", "getAll", true, "findAll"),
        SELECTOR("querySelector", "querySelectorAll", false, null),
        SELECTOR_ALL("querySelector", "querySelectorAll", true, null);

        private final String singleMethod;
        private final String allMethod;
        private final boolean all;
        private final String findMethod;

        Variant(final String singleMethod, final String allMethod, final boolean all, final String findMethod) {
            this.singleMethod = singleMethod;
            this.allMethod = allMethod;
            this.all = all;
            this.findMethod = findMethod;
        }
    }


    // --------------------------------------------------------------------------------


    /// The state shared by a play function's builders.
    private static final class Build {

        private final List<PlayStep> steps = new ArrayList<>();
        // The lists steps are added to, the top one changing inside step(...) and waitFor(...)
        private final Deque<List<PlayStep>> targets = new ArrayDeque<>();
        // True once the play function has returned and its steps are running
        private boolean finished;

        private Build() {
            targets.push(steps);
        }
    }


    // --------------------------------------------------------------------------------


    /// A value read by a [#capture(String, Supplier)] step.
    ///
    /// @param <T> The type of the value.
    private static final class Captured<T> {

        private final String name;
        private T value;
        // The run of the steps (see PlayStep#currentRun()) the value was read in, or -1
        private int runId = -1;

        private Captured(final String name) {
            this.name = name;
        }

        private void set(final T value) {
            this.value = value;
            this.runId = PlayStep.currentRun();
        }

        private boolean isSet() {
            return runId == PlayStep.currentRun();
        }

        private T get() {
            if (!isSet()) {
                throw new PlayException("The captured value '" + name + "' was read before the step capturing it "
                                        + "ran");
            }
            return value;
        }
    }
}
