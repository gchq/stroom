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

/// What happened when the user clicked a button an MCP tool's result offered (see `UserActions`).
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class AiUserActionResult {

    @JsonProperty
    private final boolean done;
    @JsonProperty
    private final String message;
    @JsonProperty
    private final DocRef openDocument;

    /// @param done         True if the action was done.
    /// @param message      What happened, or why it was not done.
    /// @param openDocument A document for the browser to open, or null.
    @JsonCreator
    public AiUserActionResult(@JsonProperty("done") final Boolean done,
                              @JsonProperty("message") final String message,
                              @JsonProperty("openDocument") final DocRef openDocument) {
        this.done = Objects.requireNonNullElse(done, false);
        this.message = message;
        this.openDocument = openDocument;
    }

    /// @return True if the action was done.
    public boolean isDone() {
        return done;
    }

    /// @return What happened, or why it was not done.
    public String getMessage() {
        return message;
    }

    /// @return A document for the browser to open, or null.
    public DocRef getOpenDocument() {
        return openDocument;
    }
}
