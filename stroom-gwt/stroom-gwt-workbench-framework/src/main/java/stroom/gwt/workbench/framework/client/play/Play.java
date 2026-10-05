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

import stroom.gwt.workbench.framework.client.play.PlayStep.Kind;

import com.google.gwt.dom.client.Element;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/// Builds the steps of a play function, with the same vocabulary as React Storybook's play
/// functions (Testing Library queries, `userEvent` and `expect`), e.g.
/// ```
/// play.click(play.getByRole("button", "Save"));
/// play.waitFor(() -> play.expect(play.getByText("Saved")).toBeVisible());
/// ```
///
/// Calling a method adds a step; the steps run once the play function has returned. Queries are
/// lazy, so find their element when the step runs.
public final class Play {

    private final List<PlayStep> steps;
    // The lists steps are added to, the top one changing inside step(...) and waitFor(...).
    // Shared with any builders created by within(...).
    private final Deque<List<PlayStep>> targets;
    private final Query scope;

    /// Creates a play builder for a whole story.
    public Play() {
        this(null, new ArrayDeque<>(), new ArrayList<>());
        targets.push(steps);
    }

    private Play(final Query scope, final Deque<List<PlayStep>> targets, final List<PlayStep> steps) {
        this.scope = scope;
        this.targets = targets;
        this.steps = steps;
    }

    /// @return The steps added, in order.
    List<PlayStep> getSteps() {
        return steps;
    }

    /// Adds a step to the play function, or to the `step` or `waitFor` group being built.
    ///
    /// @param step The step.
    void addStep(final PlayStep step) {
        targets.peek().add(step);
    }

    // ---------- Queries ----------

    /// @param role The ARIA role, e.g. `button`.
    /// @return A query for the single element with the role.
    public Query getByRole(final String role) {
        return getByRole(role, null);
    }

    /// @param role The ARIA role, e.g. `button`.
    /// @param name The accessible name, e.g. the button's text, or null for any.
    /// @return A query for the single element with the role and name.
    public Query getByRole(final String role, final String name) {
        final String args = Expectation.quote(role) + (name != null
                ? ", { name: " + Expectation.quote(name) + " }"
                : "");
        return new Query(scope, "getByRole(" + args + ")",
                "Unable to find an element with the role " + Expectation.quote(role)
                + (name != null
                        ? " and name " + Expectation.quote(name)
                        : ""),
                container -> Dom.queryAllByRole(container, role, name), -1);
    }

    /// @param text The element's own text.
    /// @return A query for the single element whose own text is the text.
    public Query getByText(final String text) {
        return new Query(scope, "getByText(" + Expectation.quote(text) + ")",
                "Unable to find an element with the text: " + text,
                container -> Dom.queryAllByText(container, text), -1);
    }

    /// @param label The text of the field's label or its `aria-label`.
    /// @return A query for the single form field with the label.
    public Query getByLabelText(final String label) {
        return new Query(scope, "getByLabelText(" + Expectation.quote(label) + ")",
                "Unable to find a label with the text of: " + label,
                container -> Dom.queryAllByLabelText(container, label), -1);
    }

    /// @param testId The value of the element's `data-testid` attribute.
    /// @return A query for the single element with the test id.
    public Query getByTestId(final String testId) {
        final String selector = "[data-testid=" + cssString(testId) + "]";
        return new Query(scope, "getByTestId(" + Expectation.quote(testId) + ")",
                "Unable to find an element by: " + selector,
                container -> Dom.querySelectorAll(container, selector), -1);
    }

    /// @param selector A CSS selector.
    /// @return A query for the single element matching the selector.
    public Query querySelector(final String selector) {
        return new Query(scope, "querySelector(" + Expectation.quote(selector) + ")",
                "Unable to find an element matching: " + selector,
                container -> Dom.querySelectorAll(container, selector), -1);
    }

    /// Waits for an element with the role and name to appear, then returns a query for it, the
    /// equivalent of Testing Library's `findByRole`.
    ///
    /// @param role The ARIA role.
    /// @param name The accessible name, or null for any.
    /// @return A query for the element.
    public Query findByRole(final String role, final String name) {
        final Query query = getByRole(role, name);
        addFind(query);
        return query;
    }

    /// Waits for an element with the text to appear, then returns a query for it, the equivalent
    /// of Testing Library's `findByText`.
    ///
    /// @param text The element's own text.
    /// @return A query for the element.
    public Query findByText(final String text) {
        final Query query = getByText(text);
        addFind(query);
        return query;
    }

    private void addFind(final Query query) {
        final String description = query.describe().replace(".getBy", ".findBy");
        addStep(PlayStep.group(Kind.WAIT_FOR, root -> description, List.of(
                PlayStep.action(root -> description, query::resolve))));
    }

    /// @param container A query for the element to search within.
    /// @return A builder whose queries only find elements within the container.
    public Play within(final Query container) {
        return new Play(container, targets, steps);
    }

    // ---------- User events ----------

    /// Clicks an element, as `userEvent.click` does.
    ///
    /// @param target The element.
    public void click(final Query target) {
        addUserEvent("click", target, null, root -> Dom.click(target.resolve(root), false));
    }

    /// Double clicks an element, as `userEvent.dblClick` does.
    ///
    /// @param target The element.
    public void dblClick(final Query target) {
        addUserEvent("dblClick", target, null, root -> Dom.click(target.resolve(root), true));
    }

    /// Moves the mouse over an element, as `userEvent.hover` does.
    ///
    /// @param target The element.
    public void hover(final Query target) {
        addUserEvent("hover", target, null, root -> Dom.hover(target.resolve(root)));
    }

    /// Clicks a field then types text into it, as `userEvent.type` does.
    ///
    /// @param target The field.
    /// @param text   The text to type.
    public void type(final Query target, final String text) {
        addUserEvent("type", target, text, root -> {
            Dom.click(target.resolve(root), false);
            for (int i = 0; i < text.length(); i++) {
                Dom.pressKey(root, String.valueOf(text.charAt(i)));
            }
        });
    }

    /// Clears a field, as `userEvent.clear` does.
    ///
    /// @param target The field.
    public void clear(final Query target) {
        addUserEvent("clear", target, null, root -> Dom.clear(target.resolve(root)));
    }

    /// Selects an option of a `<select>`, as `userEvent.selectOptions` does.
    ///
    /// @param target The select.
    /// @param value  The value or text of the option.
    public void selectOptions(final Query target, final String value) {
        addUserEvent("selectOptions", target, value, root -> Dom.selectOption(target.resolve(root), value));
    }

    /// Presses keys on the focused element, as `userEvent.keyboard` does, e.g. `{Enter}`,
    /// `{ArrowDown}` or `abc`.
    ///
    /// @param keys The keys, with named keys in braces.
    public void keyboard(final String keys) {
        addStep(PlayStep.action(root -> "userEvent.keyboard(" + Expectation.quote(keys) + ")",
                root -> {
                    for (final String key : parseKeys(keys)) {
                        Dom.pressKey(root, key);
                    }
                }));
    }

    /// Moves the focus to the next focusable element, as `userEvent.tab` does.
    public void tab() {
        addStep(PlayStep.action(root -> "userEvent.tab()", root -> Dom.tab(root)));
    }

    private void addUserEvent(final String method,
                              final Query target,
                              final String text,
                              final Consumer<Element> action) {
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

    /// Splits the keys of `userEvent.keyboard(...)` into individual keys, e.g. `ab{Enter}` =>
    /// `a`, `b`, `Enter`. `{Space}` is a space and an unclosed or empty brace is just a character.
    ///
    /// @param keys The keys, with named keys in braces.
    /// @return The keys, in order.
    static List<String> parseKeys(final String keys) {
        final List<String> parsed = new ArrayList<>();
        int i = 0;
        while (i < keys.length()) {
            final char chr = keys.charAt(i);
            final int end = keys.indexOf('}', i);
            if (chr == '{' && end > i + 1) {
                final String name = keys.substring(i + 1, end);
                parsed.add("Space".equals(name)
                        ? " "
                        : name);
                i = end + 1;
            } else {
                parsed.add(String.valueOf(chr));
                i++;
            }
        }
        return parsed;
    }

    // ---------- Expectations and grouping ----------

    /// @param target The element.
    /// @return An expectation about the element, e.g. `expect(element).toBeVisible()`.
    public Expectation expect(final Query target) {
        return new Expectation(this, target, false);
    }

    /// Groups steps under a label, the equivalent of Storybook's `step(label, ...)`.
    ///
    /// @param label The label shown in the Interactions addon.
    /// @param body  Adds the steps in the group.
    public void step(final String label, final Runnable body) {
        addGroup(Kind.STEP, root -> "step(" + Expectation.quote(label) + ")", body);
    }

    /// Retries steps until they all pass or a second has passed, the equivalent of Testing
    /// Library's `waitFor(...)`.
    ///
    /// @param body Adds the steps to retry, usually expectations.
    public void waitFor(final Runnable body) {
        addGroup(Kind.WAIT_FOR, root -> "waitFor(anonymous)", body);
    }

    private void addGroup(final Kind kind,
                          final Function<Element, String> describer,
                          final Runnable body) {
        final List<PlayStep> children = new ArrayList<>();
        targets.push(children);
        try {
            body.run();
        } finally {
            targets.pop();
        }
        addStep(PlayStep.group(kind, describer, children));
    }
}
