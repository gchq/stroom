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

package stroom.ai.impl.extraction;

import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/// A search extraction the agent proposed for a dashboard table, as its `create_extraction_pipeline` button keeps
/// it: everything Stroom needs to write it, and to set the table up to use it, once the user confirms it.
///
/// @param component   The table's name, as the user sees it.
/// @param componentId The table's component id.
/// @param namespace   The events' namespace, or blank for none.
/// @param fields      The fields it gives, besides StreamId and EventId.
/// @param newColumns  The fields to add the table columns for: those it gives that no column shows yet.
public record ExtractionPlan(@JsonProperty("component") String component,
                             @JsonProperty("componentId") String componentId,
                             @JsonProperty("namespace") String namespace,
                             @JsonProperty("fields") List<ExtractionField> fields,
                             @JsonProperty("newColumns") List<String> newColumns) {

    @JsonCreator
    public ExtractionPlan {
        namespace = NullSafe.string(namespace);
        fields = NullSafe.list(fields);
        newColumns = NullSafe.list(newColumns);
    }

    /// @param json A plan, as JSON.
    /// @return The plan.
    /// @throws IllegalArgumentException if it can't be read, or its fields can't be extracted.
    public static ExtractionPlan fromJson(final String json) {
        final ExtractionPlan plan;
        try {
            plan = JsonUtil.readValue(json, ExtractionPlan.class);
        } catch (final RuntimeException e) {
            throw new IllegalArgumentException("The extraction can't be read: " + e.getMessage(), e);
        }
        if (plan == null || NullSafe.isBlankString(plan.component()) || NullSafe.isBlankString(plan.componentId())
            || plan.fields().isEmpty()) {
            throw new IllegalArgumentException("The extraction has no table or no fields.");
        }
        if (NullSafe.string(plan.namespace()).chars()
                .anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c))) {
            throw new IllegalArgumentException("The extraction's namespace isn't one.");
        }
        final List<String> problems = ExtractionField.problems(plan.fields());
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("The extraction's fields can't be extracted: "
                                               + String.join(" ", problems));
        }
        if (plan.newColumns().stream().anyMatch(column -> plan.fields().stream()
                .noneMatch(field -> field.name().equals(column)))) {
            throw new IllegalArgumentException("The extraction adds a column for a field it doesn't give.");
        }
        return plan;
    }

    /// @return The plan, as JSON.
    public String toJson() {
        return JsonUtil.writeValueAsString(this, false);
    }
}
