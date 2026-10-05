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

import com.google.gwt.dom.client.Element;

import java.util.function.Function;
import java.util.function.Predicate;

/// An expectation about an element, the equivalent of `expect(element).toBe...()` in React
/// Storybook's play functions (with the `@testing-library/jest-dom` matchers). Each assertion
/// method adds a step to the play function.
public final class Expectation {

    private final Play play;
    private final Query query;
    private final boolean negated;

    /// @param play    The play function to add the expectation's step to.
    /// @param query   The element the expectation is about.
    /// @param negated True if the expectation is negated with `.not`.
    Expectation(final Play play, final Query query, final boolean negated) {
        this.play = play;
        this.query = query;
        this.negated = negated;
    }

    /// @return The opposite expectation, e.g. `expect(element).not.toHaveClass("hidden")`.
    public Expectation not() {
        return new Expectation(play, query, !negated);
    }

    /// Expects the element to be in the document. With [#not()], expects no element to match the
    /// query.
    public void toBeInTheDocument() {
        addCount("toBeInTheDocument()", Expectation::isPresent, true);
    }

    /// Expects the query to match no element, the equivalent of
    /// `expect(queryByText(...)).toBeNull()` or `expect(el.querySelector(...)).toBeNull()`. With
    /// [#not()], expects it to match at least one.
    public void toBeNull() {
        addCount("toBeNull()", count -> !isPresent(count), true);
    }

    /// Expects the query to match a number of elements, the equivalent of
    /// `expect(getAllByRole("row")).toHaveLength(3)`.
    ///
    /// @param length The number of elements.
    public void toHaveLength(final int length) {
        addCount("toHaveLength(" + length + ")", count -> count == length, false);
    }

    /// Expects the element to be visible.
    public void toBeVisible() {
        add("toBeVisible()", Dom::isVisible);
    }

    /// Expects the element's text to contain some text, after whitespace is collapsed, as
    /// `toHaveTextContent("text")` does.
    ///
    /// @param text The text.
    public void toHaveTextContent(final String text) {
        add("toHaveTextContent(" + quote(text) + ")", element -> Dom.getTextContent(element).contains(text));
    }

    /// Expects the element's text, after whitespace is collapsed, to match, as
    /// `toHaveTextContent(/regex/)` does.
    ///
    /// @param match How to match, e.g. `TextMatch.exact("Saved")` for the whole text.
    public void toHaveTextContent(final TextMatch match) {
        add("toHaveTextContent(" + match.describe() + ")", element -> match.matches(Dom.getTextContent(element)));
    }

    /// Expects the element to have all the CSS classes.
    ///
    /// @param classNames The classes, each of which may be several separated by spaces.
    public void toHaveClass(final String... classNames) {
        final StringBuilder args = new StringBuilder();
        for (final String className : classNames) {
            if (args.length() > 0) {
                args.append(", ");
            }
            args.append(quote(className));
        }
        add("toHaveClass(" + args + ")", element -> hasClasses(element.getClassName(), classNames));
    }

    /// @param actualClassName The element's `className`.
    /// @param classNames      The expected classes, each of which may be several separated by
    ///                        spaces.
    /// @return True if the element has all the classes (and at least one is given).
    static boolean hasClasses(final String actualClassName, final String... classNames) {
        final String padded = " " + (actualClassName != null
                ? actualClassName.replace('\t', ' ').replace('\n', ' ')
                : "") + " ";
        boolean any = false;
        for (final String classNameList : classNames) {
            for (final String className : classNameList.trim().split(" ")) {
                if (!className.isEmpty()) {
                    any = true;
                    if (!padded.contains(" " + className + " ")) {
                        return false;
                    }
                }
            }
        }
        return any;
    }

    /// Expects the element to have the keyboard focus.
    public void toHaveFocus() {
        add("toHaveFocus()", Dom::hasFocus);
    }

    /// Expects a form field to have a value.
    ///
    /// @param value The value.
    public void toHaveValue(final String value) {
        add("toHaveValue(" + quote(value) + ")", element -> value.equals(Dom.getValue(element)));
    }

    /// Expects a check box (or ARIA checkbox) to be checked.
    public void toBeChecked() {
        add("toBeChecked()", Dom::isChecked);
    }

    /// Expects the element to be disabled.
    public void toBeDisabled() {
        add("toBeDisabled()", Dom::isDisabled);
    }

    /// Expects the element to be enabled.
    public void toBeEnabled() {
        add("toBeEnabled()", element -> !Dom.isDisabled(element));
    }

    /// Expects the element to have an attribute, whatever its value.
    ///
    /// @param name The name of the attribute.
    public void toHaveAttribute(final String name) {
        add("toHaveAttribute(" + quote(name) + ")", element -> Dom.hasAttribute(element, name));
    }

    /// Expects the element to have an attribute with a value.
    ///
    /// @param name  The name of the attribute.
    /// @param value The value.
    public void toHaveAttribute(final String name, final String value) {
        add("toHaveAttribute(" + quote(name) + ", " + quote(value) + ")",
                element -> value.equals(Dom.getAttribute(element, name)));
    }

    /// Expects the element to have a computed style.
    ///
    /// @param property The CSS property, e.g. `font-weight`.
    /// @param value    The computed value, e.g. `700`.
    public void toHaveStyle(final String property, final String value) {
        add("toHaveStyle({ " + property + ": " + quote(value) + " })",
                element -> value.equals(Dom.getComputedStyle(element, property)));
    }

    private String prefix() {
        return "expect(";
    }

    private String suffix(final String matcher) {
        return ")" + (negated
                ? ".not"
                : "") + "." + matcher;
    }

    /// Adds an expectation about the number of elements the query matches. If `single`, a query
    /// for a single element must not match several, as Testing Library's `getBy`/`queryBy` throw.
    private void addCount(final String matcher, final Predicate<Integer> predicate, final boolean single) {
        play.addStep(PlayStep.action(root -> prefix() + query.describe() + suffix(matcher), root -> {
            final int count = query.countIn(root);
            if (single && count > 1 && !query.isAll()) {
                // Throws the 'found multiple elements' error
                query.resolve(root);
            }
            if (predicate.test(count) == negated) {
                throw new PlayException(prefix() + query.describe() + suffix(matcher) + " failed\n\n"
                                        + "Matching elements: " + count);
            }
        }));
    }

    private void add(final String matcher, final Predicate<Element> predicate) {
        final Function<Element, String> describer = root -> prefix()
                                                           + (root != null && !query.isAll()
                ? Dom.describe(query.resolve(root))
                : query.describe())
                                                           + suffix(matcher);
        play.addStep(PlayStep.action(describer, root -> {
            final Element element = query.resolve(root);
            if (predicate.test(element) == negated) {
                throw new PlayException(prefix() + Dom.describe(element) + suffix(matcher) + " failed");
            }
        }));
    }

    /// @param matchCount The number of elements a query matches.
    /// @return True if the element is in the document, i.e. the query matches at least one
    /// element.
    static boolean isPresent(final int matchCount) {
        return matchCount > 0;
    }

    /// Quotes text as a JavaScript string for showing in the Interactions addon, e.g. `a"b` =>
    /// `"a\"b"`.
    ///
    /// @param text The text, or null for an empty string.
    /// @return The quoted text.
    static String quote(final String text) {
        return "\"" + (text == null
                ? ""
                : text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")) + "\"";
    }
}
