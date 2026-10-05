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
/// Storybook's play functions. Each assertion method adds a step to the play function.
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
        // Resolving the query checks it's there
        add("toBeInTheDocument()", Dom::isConnected, true);
    }

    /// Expects the element to be visible.
    public void toBeVisible() {
        add("toBeVisible()", Dom::isVisible, false);
    }

    /// Expects the element's text to contain some text.
    ///
    /// @param text The text.
    public void toHaveTextContent(final String text) {
        add("toHaveTextContent(" + quote(text) + ")",
                element -> Dom.getTextContent(element).contains(text), false);
    }

    /// Expects the element to have a CSS class.
    ///
    /// @param className The class.
    public void toHaveClass(final String className) {
        add("toHaveClass(" + quote(className) + ")", element -> element.hasClassName(className), false);
    }

    /// Expects the element to have the keyboard focus.
    public void toHaveFocus() {
        add("toHaveFocus()", Dom::hasFocus, false);
    }

    /// Expects a form field to have a value.
    ///
    /// @param value The value.
    public void toHaveValue(final String value) {
        add("toHaveValue(" + quote(value) + ")", element -> value.equals(Dom.getValue(element)), false);
    }

    /// Expects a check box (or ARIA checkbox) to be checked.
    public void toBeChecked() {
        add("toBeChecked()", Dom::isChecked, false);
    }

    /// Expects the element to be disabled.
    public void toBeDisabled() {
        add("toBeDisabled()", Dom::isDisabled, false);
    }

    /// Expects the element to be enabled.
    public void toBeEnabled() {
        add("toBeEnabled()", element -> !Dom.isDisabled(element), false);
    }

    /// Expects the element to have an attribute with a value.
    ///
    /// @param name  The name of the attribute.
    /// @param value The value.
    public void toHaveAttribute(final String name, final String value) {
        add("toHaveAttribute(" + quote(name) + ", " + quote(value) + ")",
                element -> value.equals(element.getAttribute(name)), false);
    }

    private void add(final String matcher,
                     final Predicate<Element> predicate,
                     final boolean allowMissing) {
        final String not = negated
                ? ".not"
                : "";
        final Function<Element, String> describer = root -> "expect("
                                                           + (root != null
                ? Dom.describe(query.resolve(root))
                : query.describe())
                                                           + ")" + not + "." + matcher;
        play.addStep(PlayStep.action(describer, root -> {
            if (allowMissing && negated) {
                // Any match, even several, means the element is there
                if (isPresent(query.count(root))) {
                    throw new PlayException("expect(" + query.describe() + ")" + not + "." + matcher
                                            + " failed");
                }
                return;
            }
            final Element element = query.resolve(root);
            if (predicate.test(element) == negated) {
                throw new PlayException("expect(" + Dom.describe(element) + ")" + not + "." + matcher
                                        + " failed");
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
                : text.replace("\"", "\\\"")) + "\"";
    }
}
