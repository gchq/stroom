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

import stroom.docref.DocRef;
import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.regex.Pattern;

/// A search extraction the agent proposed for a dashboard table, as its `create_extraction_pipeline` or
/// `change_extraction_pipeline` button keeps it: everything Stroom needs to write it, and to set the table up to use
/// it, once the user goes ahead.
///
/// @param component   The table's name, as the user sees it.
/// @param componentId The table's component id.
/// @param sample      An event of the table it was tried on with preview_extraction, whose namespace (the events')
///                    the XSLT is written for, read when the button is used (see [ExtractionEvents]).
/// @param fields      The fields it gives, besides StreamId and EventId; for a change to an XSLT written by hand,
///                    the fields added to it (see [ExtractionChange]).
/// @param newColumns  The fields to add the table columns for: those it didn't give before that no column shows.
/// @param existing    The table's extraction pipeline, which it changes (or a new child of which it makes), or
///                    null for a table with none.
public record ExtractionPlan(@JsonProperty("component") String component,
                             @JsonProperty("componentId") String componentId,
                             @JsonProperty("sample") Sample sample,
                             @JsonProperty("fields") List<ExtractionField> fields,
                             @JsonProperty("newColumns") List<String> newColumns,
                             @JsonProperty("existing") Existing existing) {

    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");

    @JsonCreator
    public ExtractionPlan {
        fields = NullSafe.list(fields);
        newColumns = NullSafe.list(newColumns);
    }

    /// A search extraction for a table with no extraction pipeline.
    ///
    /// @param component   The table's name, as the user sees it.
    /// @param componentId The table's component id.
    /// @param sample      An event of the table it was tried on.
    /// @param fields      The fields it gives, besides StreamId and EventId.
    /// @param newColumns  The fields to add the table columns for.
    public ExtractionPlan(final String component,
                          final String componentId,
                          final Sample sample,
                          final List<ExtractionField> fields,
                          final List<String> newColumns) {
        this(component, componentId, sample, fields, newColumns, null);
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
        if (plan.sample() == null || plan.sample().streamId() < 1 || plan.sample().eventId() < 1) {
            throw new IllegalArgumentException("The extraction has no event it was tried on.");
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
        final Existing existing = plan.existing();
        if (existing != null && (existing.pipeline() == null || existing.xslt() == null
                                 || NullSafe.isBlankString(existing.element())
                                 || !HASH.matcher(NullSafe.string(existing.codeHash())).matches())) {
            throw new IllegalArgumentException("The extraction pipeline it changes can't be read.");
        }
        return plan;
    }

    /// @return The plan, as JSON.
    public String toJson() {
        return JsonUtil.writeValueAsString(this, false);
    }


    // --------------------------------------------------------------------------------


    /// An event a search extraction was tried on.
    ///
    /// @param streamId Its stream.
    /// @param eventId  Its id in the stream, from 1.
    public record Sample(@JsonProperty("streamId") long streamId,
                         @JsonProperty("eventId") long eventId) {

        @JsonCreator
        public Sample {
            // Nothing to normalise.
        }
    }


    // --------------------------------------------------------------------------------


    /// A table's extraction pipeline, as it was when a change to it was proposed.
    ///
    /// @param pipeline The pipeline.
    /// @param element  Its element whose XSLT writes its records.
    /// @param xslt     That element's XSLT.
    /// @param codeHash The SHA-256 of the XSLT's code then, as lower-case hex: a change is only written to the code
    ///                 it was proposed for.
    public record Existing(@JsonProperty("pipeline") DocRef pipeline,
                           @JsonProperty("element") String element,
                           @JsonProperty("xslt") DocRef xslt,
                           @JsonProperty("codeHash") String codeHash) {

        @JsonCreator
        public Existing {
            // Nothing to normalise.
        }
    }
}
