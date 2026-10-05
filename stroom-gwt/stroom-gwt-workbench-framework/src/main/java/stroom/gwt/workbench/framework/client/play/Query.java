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

import com.google.gwt.core.client.JsArray;
import com.google.gwt.dom.client.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/// Finds an element (or elements) in the story when a step runs, e.g.
/// `getByRole("button", { name: "Save" })`. Queries are lazy so that they find the element as it
/// is at the time, as Testing Library's do.
///
/// A query made by a `getBy`/`queryBy`/`findBy` method must match exactly one element when an
/// element is needed (e.g. to click it); one made by a `getAllBy`/`queryAllBy`/`findAllBy` method
/// stands for all its matches, e.g. for `toHaveLength`, and gives the first of them when a single
/// element is needed. Use [#nth(int)] to pick one match.
///
/// A query also gives [Value]s derived from its element(s), read when a step runs, e.g.
/// `play.expect(query.textContent()).toBe("Save")`.
public final class Query {

    private static final String BODY_DESCRIPTION = "document.body";
    private static final String STORY_ROOT_DESCRIPTION = "<div#workbench-root>";

    // The query for the element to search within (or, if chained, to start from), or null for
    // the story's root element
    private final Query scope;
    private final boolean chained;
    // E.g. getByText and getAllByText
    private final String singleMethod;
    private final String allMethod;
    // The arguments as JavaScript, e.g. "Open", { selector: "label" }
    private final String args;
    private final boolean all;
    private final String notFoundMessage;
    private final Function<Element, JsArray<Element>> finder;
    private final int index;

    /// @param scope           The query for the element to search within, or null for the story.
    /// @param singleMethod    The method for a single element, e.g. `getByText`.
    /// @param allMethod       The method for all the matching elements, e.g. `getAllByText`.
    /// @param args            The arguments as JavaScript, e.g. `"Open"`.
    /// @param all             True if the query stands for all its matches.
    /// @param notFoundMessage The error if no element matches.
    /// @param finder          Finds all the matching elements within a container.
    Query(final Query scope,
          final String singleMethod,
          final String allMethod,
          final String args,
          final boolean all,
          final String notFoundMessage,
          final Function<Element, JsArray<Element>> finder) {
        this(scope, false, singleMethod, allMethod, args, all, notFoundMessage, finder, -1);
    }

    private Query(final Query scope,
                  final boolean chained,
                  final String singleMethod,
                  final String allMethod,
                  final String args,
                  final boolean all,
                  final String notFoundMessage,
                  final Function<Element, JsArray<Element>> finder,
                  final int index) {
        this.scope = scope;
        this.chained = chained;
        this.singleMethod = singleMethod;
        this.allMethod = allMethod;
        this.args = args;
        this.all = all;
        this.notFoundMessage = notFoundMessage;
        this.finder = finder;
        this.index = index;
    }

    /// @return A query for the document's `<body>`, e.g. to fire events on it, or as the scope of
    /// [Play#screen()] which finds popups and dialogs attached to the body.
    static Query body() {
        return new Query(null, BODY_DESCRIPTION, BODY_DESCRIPTION, null, false,
                "Unable to find the document body", Dom::body);
    }

    /// @param index The index of the match to use, when several elements match, the equivalent of
    ///              `getAllByText("Open")[1]`.
    /// @return A query for one of the elements this query matches.
    /// @throws IllegalArgumentException If the index is negative.
    public Query nth(final int index) {
        if (index < 0) {
            throw new IllegalArgumentException("The index must not be negative: " + index);
        }
        return new Query(scope, chained, singleMethod, allMethod, args, true, notFoundMessage, finder, index);
    }

    /// @return A query for the first element this query matches, i.e. `nth(0)`.
    public Query first() {
        return nth(0);
    }

    /// @param selector A CSS selector, e.g. `[role="row"]`.
    /// @return A query for the closest ancestor of this query's element (or the element itself)
    /// that matches the selector, the equivalent of `element.closest(selector)`.
    public Query closest(final String selector) {
        return new Query(this, true, "closest", "closest", Expectation.quote(selector), false,
                "Unable to find an ancestor of " + describe() + " matching: " + selector,
                element -> Dom.closest(element, selector), -1);
    }

    /// @return The query as Storybook shows it in the Interactions addon, e.g.
    /// `within(<div#workbench-root>).getByRole("button", { name: "Save" })`.
    public String describe() {
        return describeAs(all
                ? allMethod
                : singleMethod);
    }

    /// @param method The method to show for this query, e.g. `findByText` rather than `getByText`.
    /// @return The query as Storybook shows it, with the method.
    String describeAs(final String method) {
        if (args == null) {
            // document.body
            return singleMethod;
        }
        final String queryText = method + "(" + args + ")" + (index >= 0
                ? "[" + index + "]"
                : "");
        if (chained) {
            return scope.describe() + "." + queryText;
        }
        final String scopeText = scope != null
                ? scope.describe()
                : STORY_ROOT_DESCRIPTION;
        return "within(" + scopeText + ")." + queryText;
    }

    @Override
    public String toString() {
        return describe();
    }

    /// @return True if the query stands for all its matches rather than a single element.
    boolean isAll() {
        return all && index < 0;
    }

    /// Finds the element.
    ///
    /// @param root The element containing the story.
    /// @return The element.
    /// @throws PlayException If there is no matching element, or more than one for a query for a
    ///                       single element.
    public Element resolve(final Element root) {
        final JsArray<Element> matches = findAll(root);
        return matches.get(pick(matches.length(), index, all, notFoundMessage));
    }

    /// Picks which match a query uses.
    ///
    /// @param matchCount      The number of elements the query's finder matches.
    /// @param index           The index of the match to use, or -1 for none in particular.
    /// @param all             True if the query stands for all its matches.
    /// @param notFoundMessage The error if no element matches.
    /// @return The index of the match to use.
    /// @throws PlayException If there is no such match, or several when a single one is needed.
    static int pick(final int matchCount, final int index, final boolean all, final String notFoundMessage) {
        if (index >= 0) {
            if (index >= matchCount) {
                throw new PlayException(notFoundMessage + (matchCount > 0
                        ? " at index " + index + " (found " + matchCount + ")"
                        : ""));
            }
            return index;
        }
        if (matchCount == 0) {
            throw new PlayException(notFoundMessage);
        }
        if (matchCount > 1 && !all) {
            throw new PlayException("Found multiple elements (" + matchCount + "): "
                                    + notFoundMessage.replace("Unable to find an element", "elements")
                                    + " (use nth() to pick one)");
        }
        return 0;
    }

    /// @param root The element containing the story.
    /// @return All the elements the query matches (at most one if it uses [#nth(int)]).
    /// @throws PlayException If the query's scope can't be found or its selector isn't valid.
    List<Element> resolveAll(final Element root) {
        final JsArray<Element> matches = findAll(root);
        final List<Element> elements = new ArrayList<>();
        if (index >= 0) {
            if (index < matches.length()) {
                elements.add(matches.get(index));
            }
            return elements;
        }
        for (int i = 0; i < matches.length(); i++) {
            elements.add(matches.get(i));
        }
        return elements;
    }

    /// @param root The element containing the story.
    /// @return The number of elements the query matches, which is at most one if it uses
    /// [#nth(int)].
    /// @throws PlayException If the query's scope can't be found or its selector isn't valid.
    int countIn(final Element root) {
        return countMatches(findAll(root).length(), index);
    }

    /// @param matchCount The number of elements the query's finder matches.
    /// @param index      The index of the match to use, or -1 for all of them.
    /// @return The number of elements the query matches.
    static int countMatches(final int matchCount, final int index) {
        if (index < 0) {
            return matchCount;
        }
        return index < matchCount
                ? 1
                : 0;
    }

    private JsArray<Element> findAll(final Element root) {
        final Element container = scope != null
                ? scope.resolve(root)
                : root;
        return finder.apply(container);
    }

    // ---------- Values, read when a step runs ----------

    /// @return The element, e.g. to pass to a widget in [Play#run(String, Runnable)].
    public Value<Element> element() {
        return value("", () -> resolve(PlayStep.currentRoot()));
    }

    /// @return The number of elements the query matches, the equivalent of
    /// `getAllByText(...).length` (or `queryAllBy...`), e.g. for `toBeGreaterThan(0)`.
    public Value<Integer> count() {
        return value(".length", () -> countIn(PlayStep.currentRoot()));
    }

    /// @return True if the query matches at least one element.
    public Value<Boolean> exists() {
        return value(" != null", () -> countIn(PlayStep.currentRoot()) > 0);
    }

    /// @return The element's raw `textContent`, the equivalent of `element.textContent`.
    public Value<String> textContent() {
        return value(".textContent", () -> Dom.getRawTextContent(resolve(PlayStep.currentRoot())));
    }

    /// @return The `textContent` of each element the query matches, the equivalent of
    /// `getAllBy...(...).map(el => el.textContent)`.
    public Value<List<String>> textContents() {
        return value(".map(el => el.textContent)", () -> {
            final List<String> texts = new ArrayList<>();
            for (final Element element : resolveAll(PlayStep.currentRoot())) {
                texts.add(Dom.getRawTextContent(element));
            }
            return texts;
        });
    }

    /// @param name The name of the attribute, e.g. `aria-selected`.
    /// @return The attribute's value, or null if the element doesn't have it, the equivalent of
    /// `element.getAttribute(name)`.
    public Value<String> attribute(final String name) {
        return value(".getAttribute(" + Expectation.quote(name) + ")",
                () -> Dom.getAttribute(resolve(PlayStep.currentRoot()), name));
    }

    /// @return The element's `value` property (e.g. of a field), or null if it has none.
    public Value<String> value() {
        return value(".value", () -> Dom.getValue(resolve(PlayStep.currentRoot())));
    }

    /// @return The element's `className`.
    public Value<String> className() {
        return value(".className", () -> resolve(PlayStep.currentRoot()).getClassName());
    }

    /// @param name The name of a property, e.g. `scrollTop`, `checked` or `tagName`.
    /// @return The element's property as a string, number or boolean (or null), the equivalent of
    /// `element[name]`.
    public Value<Object> property(final String name) {
        return value("." + name, () -> Dom.getProperty(resolve(PlayStep.currentRoot()), name));
    }

    /// @param name The name of a CSS property, e.g. `font-weight`.
    /// @return The element's computed style for the property, the equivalent of
    /// `getComputedStyle(element).getPropertyValue(name)`.
    public Value<String> computedStyle(final String name) {
        return value(".style(" + Expectation.quote(name) + ")",
                () -> Dom.getComputedStyle(resolve(PlayStep.currentRoot()), name));
    }

    /// @return The left of the element's bounding rectangle, from `getBoundingClientRect()`.
    public Value<Double> left() {
        return rect("left");
    }

    /// @return The top of the element's bounding rectangle, from `getBoundingClientRect()`.
    public Value<Double> top() {
        return rect("top");
    }

    /// @return The right of the element's bounding rectangle, from `getBoundingClientRect()`.
    public Value<Double> right() {
        return rect("right");
    }

    /// @return The bottom of the element's bounding rectangle, from `getBoundingClientRect()`.
    public Value<Double> bottom() {
        return rect("bottom");
    }

    /// @return The width of the element's bounding rectangle, from `getBoundingClientRect()`.
    public Value<Double> width() {
        return rect("width");
    }

    /// @return The height of the element's bounding rectangle, from `getBoundingClientRect()`.
    public Value<Double> height() {
        return rect("height");
    }

    private Value<Double> rect(final String side) {
        return value(".getBoundingClientRect()." + side,
                () -> Dom.getRect(resolve(PlayStep.currentRoot()), side));
    }

    private <T> Value<T> value(final String suffix, final Supplier<T> supplier) {
        return Value.of(describe() + suffix, supplier);
    }
}
