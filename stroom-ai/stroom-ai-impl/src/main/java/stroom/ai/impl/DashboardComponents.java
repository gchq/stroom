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

package stroom.ai.impl;

import stroom.docref.DocRef;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// The components of a dashboard (queries, tables, text and so on), read from the description of it the browser
/// gives the AI chat (each component's heading, e.g. `## Table 'Events' (id table-1)`, and a query's data source),
/// so changes can name them as the user sees them.
public final class DashboardComponents {

    /// A query component, as its heading names its type.
    public static final String QUERY = "Query";
    /// A table component.
    public static final String TABLE = "Table";
    /// A text component.
    public static final String TEXT = "Text";

    private static final Pattern HEADING = Pattern.compile("^## (.+?) '(.*)' \\(id ([^)]+)\\)$");
    private static final Pattern DATA_SOURCE = Pattern.compile("^Data source: (\\S+) '(.*)' \\(uuid ([^)]+)\\)$");

    private DashboardComponents() {
        // Static methods only.
    }

    /// @param text The dashboard's description, as the browser gives it.
    /// @return Its components, in the order given.
    public static List<Component> parse(final String text) {
        final List<Component> components = new ArrayList<>();
        Component current = null;
        for (final String line : NullSafe.string(text).split("\n")) {
            final Matcher heading = HEADING.matcher(line);
            if (heading.matches()) {
                if (current != null) {
                    components.add(current);
                }
                current = new Component(heading.group(1), heading.group(2), heading.group(3), null);
                continue;
            }
            final Matcher dataSource = DATA_SOURCE.matcher(line);
            if (current != null && dataSource.matches()) {
                current = new Component(current.type(), current.name(), current.id(),
                        new DocRef(dataSource.group(1), dataSource.group(3), dataSource.group(2)));
            }
        }
        if (current != null) {
            components.add(current);
        }
        return List.copyOf(components);
    }

    /// @param components A dashboard's components.
    /// @param name       A component's name, as the user sees it.
    /// @return The component with the name: exactly, else ignoring case if only one matches.
    public static Optional<Component> find(final List<Component> components, final String name) {
        if (name == null) {
            return Optional.empty();
        }
        final Optional<Component> exact = components.stream().filter(c -> name.equals(c.name())).findFirst();
        if (exact.isPresent()) {
            return exact;
        }
        final List<Component> matches = components.stream().filter(c -> name.equalsIgnoreCase(c.name())).toList();
        return matches.size() == 1
                ? Optional.of(matches.getFirst())
                : Optional.empty();
    }

    /// @param components A dashboard's components.
    /// @return Each named with its type, e.g. "Table 'Events'", for telling the model what there is.
    public static String describe(final List<Component> components) {
        return String.join(", ", components.stream().map(c -> c.type() + " '" + c.name() + "'").toList());
    }


    // --------------------------------------------------------------------------------


    /// A dashboard component.
    ///
    /// @param type       Its type, as its heading names it, e.g. [#TABLE].
    /// @param name       Its name, as the user sees it.
    /// @param id         Its component id.
    /// @param dataSource For a query, its data source, or null.
    public record Component(String type, String name, String id, DocRef dataSource) {

    }
}
