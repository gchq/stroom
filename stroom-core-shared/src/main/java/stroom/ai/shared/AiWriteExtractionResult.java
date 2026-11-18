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

package stroom.ai.shared;

import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/// What happened when a search extraction the user confirmed was written: the new pipeline, and the changes to
/// make to the dashboard's table (unsaved) to use it.
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class AiWriteExtractionResult {

    @JsonProperty
    private final boolean done;
    @JsonProperty
    private final String message;
    @JsonProperty
    private final DocRef pipeline;
    @JsonProperty
    private final String tableChanges;

    /// @param done         True if the pipeline and its XSLT were written.
    /// @param message      What happened, or why nothing was written.
    /// @param pipeline     The new pipeline, or null.
    /// @param tableChanges The changes to make to the table, as JSON operations (as a proposed dashboard change
    ///                     gives them), or null.
    @JsonCreator
    public AiWriteExtractionResult(@JsonProperty("done") final Boolean done,
                                   @JsonProperty("message") final String message,
                                   @JsonProperty("pipeline") final DocRef pipeline,
                                   @JsonProperty("tableChanges") final String tableChanges) {
        this.done = Objects.requireNonNullElse(done, false);
        this.message = message;
        this.pipeline = pipeline;
        this.tableChanges = tableChanges;
    }

    /// @param message Why nothing was written.
    /// @return A result saying so.
    public static AiWriteExtractionResult failed(final String message) {
        return new AiWriteExtractionResult(false, message, null, null);
    }

    /// @return True if the pipeline and its XSLT were written.
    public boolean isDone() {
        return done;
    }

    /// @return What happened, or why nothing was written.
    public String getMessage() {
        return message;
    }

    /// @return The new pipeline, or null.
    public DocRef getPipeline() {
        return pipeline;
    }

    /// @return The changes to make to the table, as JSON operations, or null.
    public String getTableChanges() {
        return tableChanges;
    }
}
