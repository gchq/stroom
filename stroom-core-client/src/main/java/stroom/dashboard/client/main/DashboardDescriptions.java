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

package stroom.dashboard.client.main;

import stroom.ai.shared.AiTabSnapshotPart;
import stroom.query.api.ExpressionOperator;
import stroom.util.shared.NullSafe;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/// Reads a dashboard's description ([DashboardPresenter#getComparableDescription()]) by component, so changes the AI
/// chat applied are applied, and undone, on top of whatever else the user has changed since, as long as what they
/// replace hasn't changed.
public final class DashboardDescriptions {

    // What starts a component's heading, e.g. "## Table 'Events' (id table-1)".
    private static final String HEADING = "## ";
    // What comes before the component's id at the end of its heading.
    private static final String ID = " (id ";

    private DashboardDescriptions() {
        // Static methods only.
    }

    /// @param description A dashboard's description.
    /// @return Each component's part of it (its heading and the lines below it, up to the next heading, without
    /// blank ones), by its component id.
    public static Map<String, String> componentParts(final String description) {
        final Map<String, String> parts = new HashMap<>();
        String id = null;
        StringBuilder part = null;
        for (final String line : NullSafe.string(description).split("\n")) {
            if (line.startsWith(HEADING) && line.endsWith(")") && line.lastIndexOf(ID) > 0) {
                if (id != null) {
                    parts.put(id, part.toString());
                }
                id = line.substring(line.lastIndexOf(ID) + ID.length(), line.length() - 1);
                part = new StringBuilder();
            }
            // Blank lines only separate components, so a component's part is the same whatever follows it.
            if (part != null && !line.isBlank()) {
                part.append(line).append('\n');
            }
        }
        if (id != null) {
            parts.put(id, part.toString());
        }
        return parts;
    }

    /// @param before       A dashboard's description at one time.
    /// @param after        Its description at another.
    /// @param componentIds Some of its components' ids.
    /// @return True if each of the components is described the same in both (or is in neither).
    public static boolean sameComponents(final String before,
                                         final String after,
                                         final Collection<String> componentIds) {
        final Map<String, String> partsBefore = componentParts(before);
        final Map<String, String> partsAfter = componentParts(after);
        return NullSafe.collection(componentIds).stream()
                .allMatch(id -> Objects.equals(partsBefore.get(id), partsAfter.get(id)));
    }

    /// @param proposedHash The hash ([AiTabSnapshotPart#hashLines(String)]) of a query's expression as the
    ///                     dashboard's description gave it when a change to it was proposed.
    /// @param expression   The query's expression now, or null.
    /// @return True if the expression is as it was.
    public static boolean sameExpression(final String proposedHash, final ExpressionOperator expression) {
        // As the description gives it.
        final String described = String.valueOf(NullSafe.get(expression, ExpressionOperator::toMultiLineString));
        return AiTabSnapshotPart.hashLines(described).equalsIgnoreCase(proposedHash);
    }
}
