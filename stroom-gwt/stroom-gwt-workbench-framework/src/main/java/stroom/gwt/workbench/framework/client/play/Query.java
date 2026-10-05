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

import java.util.function.Function;

/// Finds an element in the story when a step runs, e.g. `getByRole("button", "Save")`. Queries
/// are lazy so that they find the element as it is at the time, as Testing Library's do.
public final class Query {

    private static final String GET_BY = "getBy";
    private static final String GET_ALL_BY = "getAllBy";
    private static final String QUERY_SELECTOR = "querySelector(";
    private static final String QUERY_SELECTOR_ALL = "querySelectorAll(";

    private final Query scope;
    // The query for a single element, e.g. getByText("Open")
    private final String description;
    private final String notFoundMessage;
    private final Function<Element, JsArray<Element>> finder;
    private final int index;

    /// @param scope           The query for the element to search within, or null for the story.
    /// @param description     The query for a single element as code, e.g. `getByText("Open")`.
    /// @param notFoundMessage The error if no element matches.
    /// @param finder          Finds all the matching elements within a container.
    /// @param index           The index of the match to use, or -1 if exactly one must match.
    Query(final Query scope,
          final String description,
          final String notFoundMessage,
          final Function<Element, JsArray<Element>> finder,
          final int index) {
        this.scope = scope;
        this.description = description;
        this.notFoundMessage = notFoundMessage;
        this.finder = finder;
        this.index = index;
    }

    /// @param index The index of the match to use, when several elements match.
    /// @return A query for one of the elements this query matches.
    /// @throws IllegalArgumentException If the index is negative.
    public Query nth(final int index) {
        if (index < 0) {
            throw new IllegalArgumentException("The index must not be negative: " + index);
        }
        return new Query(scope, description, notFoundMessage, finder, index);
    }

    /// @return The query as Storybook shows it in the Interactions addon, e.g.
    /// `within(<div#workbench-root>).getByRole("button", { name: "Save" })`.
    public String describe() {
        final String scopeText = scope != null
                ? "within(" + scope.describe() + ")"
                : "within(<div#workbench-root>)";
        final String queryText = index >= 0
                ? toAllForm(description) + "[" + index + "]"
                : description;
        return scopeText + "." + queryText;
    }

    /// @param single A query for a single element, e.g. `getByText("Open")`.
    /// @return The query for all the matching elements, e.g. `getAllByText("Open")`.
    private static String toAllForm(final String single) {
        if (single.startsWith(GET_BY)) {
            return GET_ALL_BY + single.substring(GET_BY.length());
        }
        if (single.startsWith(QUERY_SELECTOR)) {
            return QUERY_SELECTOR_ALL + single.substring(QUERY_SELECTOR.length());
        }
        return single;
    }

    /// Finds the element.
    ///
    /// @param root The element containing the story.
    /// @return The element.
    /// @throws PlayException If there is no matching element, or more than one.
    public Element resolve(final Element root) {
        final JsArray<Element> matches = findAll(root);
        if (index >= 0) {
            if (index >= matches.length()) {
                throw new PlayException(notFoundMessage);
            }
            return matches.get(index);
        }
        if (matches.length() == 0) {
            throw new PlayException(notFoundMessage);
        }
        if (matches.length() > 1) {
            throw new PlayException("Found multiple elements: " + notFoundMessage.replace(
                    "Unable to find an element", "elements") + " (use nth() to pick one)");
        }
        return matches.get(0);
    }

    /// @param root The element containing the story.
    /// @return The number of elements the query matches, which is at most one if it uses
    /// [#nth(int)].
    /// @throws PlayException If the query's scope can't be found or its selector isn't valid.
    int count(final Element root) {
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
}
