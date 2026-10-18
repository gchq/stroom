/*
 * Copyright 2016 Crown Copyright
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class AskStroomAiResponse {

    @JsonProperty
    private final String message;
    @JsonProperty
    private final boolean tabSnapshotNeeded;

    public AskStroomAiResponse(final String message) {
        this(message, false);
    }

    /// @param message           A message, or null.
    /// @param tabSnapshotNeeded True if only the active tab's hash was sent, but the server doesn't hold a copy with
    ///                          it, so the next question should send the tab in full.
    @JsonCreator
    public AskStroomAiResponse(@JsonProperty("message") final String message,
                               @JsonProperty("tabSnapshotNeeded") final Boolean tabSnapshotNeeded) {
        this.message = message;
        this.tabSnapshotNeeded = tabSnapshotNeeded != null && tabSnapshotNeeded;
    }

    public String getMessage() {
        return message;
    }

    /// @return True if the next question should send the active tab in full, as the server doesn't hold the copy
    /// whose hash was sent.
    public boolean isTabSnapshotNeeded() {
        return tabSnapshotNeeded;
    }
}
