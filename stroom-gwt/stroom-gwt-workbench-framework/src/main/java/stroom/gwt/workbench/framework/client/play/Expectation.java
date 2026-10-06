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

import java.util.Objects;
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
    /// `toHaveTextContent("text")` does. As in jest-dom, an empty string only matches an element
    /// with no text (use it with [#not()] to expect some text).
    ///
    /// @param text The text.
    public void toHaveTextContent(final String text) {
        Objects.requireNonNull(text, "text");
        add("toHaveTextContent(" + quote(text) + ")", element -> textContentMatches(Dom.getTextContent(element), text));
    }

    /// @param actual   The element's text with whitespace collapsed.
    /// @param expected The text expected.
    /// @return True if the text contains the expected text, as jest-dom's `toHaveTextContent`
    /// checks, except that an empty string only matches empty text.
    static boolean textContentMatches(final String actual, final String expected) {
        if (expected.isEmpty()) {
            return actual.isEmpty();
        }
        return actual.contains(expected);
    }

    /// Expects the element's text, after whitespace is collapsed, to match, as
    /// `toHaveTextContent(/regex/)` does.
    ///
    /// @param match How to match, e.g. `TextMatch.exact("Saved")` for the whole text.
    public void toHaveTextContent(final TextMatch match) {
        add("toHaveTextContent(" + match.describe() + ")", element -> match.matches(Dom.getTextContent(element)));
    }

    /// Expects the element to have all the CSS classes. As in jest-dom, `not().toHaveClass()` with
    /// no classes expects the element to have no classes at all, and `toHaveClass()` with none is
    /// an error.
    ///
    /// @param classNames The classes, each of which may be several separated by spaces.
    /// @throws IllegalArgumentException If no classes are given and the expectation isn't negated.
    public void toHaveClass(final String... classNames) {
        final StringBuilder args = new StringBuilder();
        for (final String className : classNames) {
            if (args.length() > 0) {
                args.append(", ");
            }
            args.append(quote(className));
        }
        if (!negated && !anyClassGiven(classNames)) {
            throw new IllegalArgumentException("toHaveClass() needs at least one class; use not().toHaveClass() "
                                               + "to expect no classes");
        }
        add("toHaveClass(" + args + ")", element -> hasClasses(Dom.getClassName(element), classNames));
    }

    /// @param classNames Classes, each of which may be several separated by spaces.
    /// @return True if there is at least one class among them.
    static boolean anyClassGiven(final String... classNames) {
        for (final String classNameList : classNames) {
            if (classNameList != null && !classNameList.trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /// @param actualClassName The element's classes.
    /// @param classNames      The expected classes, each of which may be several separated by
    ///                        spaces.
    /// @return True if the element has all the classes or, if none are given, any class.
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
        return any || !padded.trim().isEmpty();
    }

    /// Expects the element to have the keyboard focus.
    public void toHaveFocus() {
        add("toHaveFocus()", Dom::hasFocus);
    }

    /// Expects a form field to have a text value, as jest-dom's `toHaveValue("text")` does, which
    /// compares the value with its type: a text field's value is a string, but a number field's
    /// is a number, so `toHaveValue("5")` fails for a number field (use
    /// [#toHaveValue(Number)]). As in jest-dom, the step fails for a check box or radio button
    /// (use [#toBeChecked()]), even with [#not()].
    ///
    /// @param value The value, e.g. `"Smith"`.
    public void toHaveValue(final String value) {
        Objects.requireNonNull(value, "value");
        addValue("toHaveValue(" + quote(value) + ")", value);
    }

    /// Expects a number field (or an element with a role such as `spinbutton` and
    /// `aria-valuenow`) to have a numeric value, the equivalent of `toHaveValue(1.5)`. As in
    /// jest-dom, a text field's value is a string, so never equals a number, and the step fails
    /// for a check box or radio button, even with [#not()].
    ///
    /// @param value The number.
    public void toHaveValue(final Number value) {
        Objects.requireNonNull(value, "value");
        addValue("toHaveValue(" + Values.formatNumber(value) + ")", value);
    }

    private void addValue(final String matcher, final Object expected) {
        add(matcher, element -> {
            if (Dom.isCheckableInput(element)) {
                throw new PlayException("input with type=checkbox or type=radio cannot be used with "
                                        + ".toHaveValue(). Use .toBeChecked() for type=checkbox or "
                                        + ".toHaveFormValues() instead");
            }
            return typedValueMatches(Dom.getTypedValue(element), expected);
        });
    }

    /// @param actual   The element's value as jest-dom reads it (see `Dom.getTypedValue`): a
    ///                 String, a Double or null.
    /// @param expected The value expected: a String or a Number.
    /// @return True if the values are equal and of the same JavaScript type, as jest-dom
    /// compares them, e.g. the number 5 doesn't equal the text `"5"`.
    static boolean typedValueMatches(final Object actual, final Object expected) {
        if (actual instanceof Number && expected instanceof Number) {
            final double a = Values.toDouble((Number) actual);
            final double e = Values.toDouble((Number) expected);
            return a == e || (Double.isNaN(a) && Double.isNaN(e));
        }
        if (actual instanceof String && expected instanceof String) {
            return actual.equals(expected);
        }
        return false;
    }

    /// Expects a check box, radio button or element with a checkable role (e.g. `checkbox`,
    /// `switch` or `menuitemcheckbox`) and an `aria-checked` of `true` or `false` to be
    /// checked, as jest-dom's `toBeChecked` does. As in jest-dom, an element that can't be
    /// checked fails the expectation, so passes it with [#not()].
    public void toBeChecked() {
        add("toBeChecked()", element -> Dom.getCheckedState(element) == 1);
    }

    /// Expects the element to be disabled, as jest-dom decides: a form control (button, input,
    /// select, text area, option, option group or field set) with the `disabled` attribute or in
    /// a disabled field set (outside its first legend). As in jest-dom, `aria-disabled` doesn't
    /// count; check it with `toHaveAttribute("aria-disabled", "true")`.
    public void toBeDisabled() {
        add("toBeDisabled()", Dom::isDisabled);
    }

    /// Expects the element to be enabled, i.e. not [#toBeDisabled()].
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
    /// @param value The value, or null for any value (as `toHaveAttribute(name, undefined)`).
    public void toHaveAttribute(final String name, final String value) {
        if (value == null) {
            toHaveAttribute(name);
            return;
        }
        add("toHaveAttribute(" + quote(name) + ", " + quote(value) + ")",
                element -> value.equals(Dom.getAttribute(element, name)));
    }

    /// Expects the element to have a computed style, as `toHaveStyle({ property: value })` does
    /// in a real browser: the value is normalised as a style declaration normalises it (e.g.
    /// `#f00` becomes `rgb(255, 0, 0)`) and must then be the same as the computed value. As in
    /// React Storybook, a named colour such as `red` doesn't match, as the computed colour is
    /// `rgb(255, 0, 0)`.
    ///
    /// @param property The CSS property, in CSS or JavaScript form, e.g. `font-weight` or
    ///                 `fontWeight`.
    /// @param value    The value, e.g. `700`.
    public void toHaveStyle(final String property, final String value) {
        Objects.requireNonNull(value, "value");
        final String cssProperty = toCssProperty(Objects.requireNonNull(property, "property"));
        add("toHaveStyle({ " + property + ": " + quote(value) + " })",
                element -> Dom.hasStyle(element, cssProperty, value));
    }

    /// @param property A CSS property in JavaScript (camel case) or CSS form, e.g. `fontWeight`.
    /// @return The property in CSS form, e.g. `font-weight`. Custom properties (`--x`) are kept.
    static String toCssProperty(final String property) {
        if (property.startsWith("--")) {
            return property;
        }
        final StringBuilder sb = new StringBuilder(property.length() + 4);
        for (int i = 0; i < property.length(); i++) {
            final char chr = property.charAt(i);
            if (chr >= 'A' && chr <= 'Z') {
                sb.append('-').append((char) (chr - 'A' + 'a'));
            } else {
                sb.append(chr);
            }
        }
        return sb.toString();
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
