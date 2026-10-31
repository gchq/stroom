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

import stroom.ai.impl.extraction.ExtractionPlan.Sample;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/// That a search extraction was tried on a dashboard table's events with preview_extraction, and could be used: the
/// table, the pipeline it extracted with then, a hash of the fields tried, and an event it was tried on. A proposal
/// is only offered for fields tried so (see `propose_extraction_pipeline`), and its XSLT is written for the
/// namespace of that event.
///
/// It is kept in the chat's workspace under `/tabs/`, which only Stroom writes: the model can read it, but can't
/// make or change one.
///
/// @param dashboard     The dashboard's UUID.
/// @param componentId   The table's component id.
/// @param tablePipeline The UUID of the extraction pipeline the table extracted with, or null for none.
/// @param xsltHash      The SHA-256 of the code of the table's extraction XSLT the fields were tried as a change
///                      to, or null for a table with none.
/// @param fieldsHash    The hash of the fields tried (see [#fieldsHash(List)]).
/// @param sample        An event it was tried on; null if the last preview of the table can't be used (so any
///                      earlier one can't be either).
public record PreviewRecord(@JsonProperty("dashboard") String dashboard,
                            @JsonProperty("componentId") String componentId,
                            @JsonProperty("tablePipeline") String tablePipeline,
                            @JsonProperty("xsltHash") String xsltHash,
                            @JsonProperty("fieldsHash") String fieldsHash,
                            @JsonProperty("sample") Sample sample) {

    @JsonCreator
    public PreviewRecord {
        // Nothing to normalise.
    }

    /// Where the record of a table's last usable preview is kept.
    ///
    /// @param dashboard   The dashboard's UUID.
    /// @param componentId The table's component id.
    /// @return The path, in the read-only `/tabs/` area.
    public static WorkspacePath path(final String dashboard, final String componentId) {
        return WorkspacePath.parse("/tabs/extraction-previews/" + safe(dashboard) + "-" + safe(componentId)
                                   + ".json");
    }

    /// The hash of some fields, as tried or proposed: each one's name, XPath and type, in order.
    ///
    /// @param fields The fields.
    /// @return The hash.
    public static String fieldsHash(final List<ExtractionField> fields) {
        return ExtractionChange.sha256(JsonUtil.writeValueAsString(NullSafe.list(fields), false));
    }

    /// The record of a preview that can't be used, replacing any earlier one of the table, so the table's last
    /// preview is the one that counts.
    ///
    /// @param dashboard   The dashboard's UUID.
    /// @param componentId The table's component id.
    /// @return The record.
    public static PreviewRecord unusable(final String dashboard, final String componentId) {
        return new PreviewRecord(dashboard, componentId, null, null, null, null);
    }

    /// @param json A record, as JSON.
    /// @return The record, or null if it can't be read, or is of a preview that can't be used.
    public static PreviewRecord fromJson(final String json) {
        try {
            final PreviewRecord record = JsonUtil.readValue(json, PreviewRecord.class);
            return record == null || record.sample() == null
                    ? null
                    : record;
        } catch (final RuntimeException e) {
            return null;
        }
    }

    /// @return The record, as JSON.
    public String toJson() {
        return JsonUtil.writeValueAsString(this, false);
    }

    // A name made safe for a workspace path.
    private static String safe(final String text) {
        return NullSafe.string(text).replaceAll("[^A-Za-z0-9_.-]", "_");
    }
}
