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

package stroom.ai.impl.tools.timeline;

import stroom.docref.DocRef;
import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;

import java.util.Map;

/// What a `create_timeline_dashboard` button writes, as kept in it (see [ProposeTimelineDashboardTool]): which
/// timeline table of the chat's workspace, as it was when the button was offered (its hash), and the chart it is
/// drawn with. The table is read again when the button is clicked, and written only if it is unchanged.
///
/// @param entity      The user (or entity).
/// @param table       [TimelineDataConnection#SPANS] or [TimelineDataConnection#ACTIVITIES].
/// @param path        The table's path in the chat's workspace.
/// @param hash        The SHA-256 of the table's CSV when the button was offered, as hex.
/// @param rows        Its rows.
/// @param fromMs      The period's start, the dashboard's time range.
/// @param toMs        The period's end.
/// @param description The Data Connection's description.
/// @param mappings    The column the timeline chart draws for each of its roles, by role id.
/// @param replace     The Data Connection of an earlier run for the same entity and table that it replaces (and
///                    renames), or null to write a new one.
public record TimelineDashboardPlan(String entity,
                                    String table,
                                    String path,
                                    String hash,
                                    int rows,
                                    long fromMs,
                                    long toMs,
                                    String description,
                                    Map<String, String> mappings,
                                    DocRef replace) {

    /// Copies the mappings.
    public TimelineDashboardPlan {
        mappings = Map.copyOf(NullSafe.map(mappings));
    }

    /// @return It as JSON, as the button keeps it.
    public String toJson() {
        return JsonUtil.writeValueAsString(this, false);
    }

    /// Reads a plan a button kept.
    ///
    /// @param json The plan's JSON.
    /// @return The plan.
    /// @throws IllegalArgumentException If it can't be read, or isn't a whole plan.
    public static TimelineDashboardPlan fromJson(final String json) {
        final TimelineDashboardPlan plan;
        try {
            plan = JsonUtil.readValue(json, TimelineDashboardPlan.class);
        } catch (final RuntimeException e) {
            throw new IllegalArgumentException("The timeline dashboard's plan can't be read: " + e.getMessage(), e);
        }
        if (plan == null || NullSafe.isBlankString(plan.entity()) || plan.table() == null
            || !TimelineDataConnection.TABLES.contains(plan.table()) || NullSafe.isBlankString(plan.path())
            || NullSafe.isBlankString(plan.hash()) || plan.mappings().isEmpty()) {
            throw new IllegalArgumentException("The timeline dashboard's plan isn't whole.");
        }
        return plan;
    }
}
