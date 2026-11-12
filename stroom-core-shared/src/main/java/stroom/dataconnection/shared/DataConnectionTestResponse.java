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

package stroom.dataconnection.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/// The outcome of testing the connection of a [DataConnectionDoc].
@JsonInclude(Include.NON_NULL)
public class DataConnectionTestResponse {

    @JsonProperty
    private final boolean ok;
    @JsonProperty
    private final String message;

    /// Creates a test response.
    ///
    /// @param ok      Whether the connection worked; `null` is taken as `false`.
    /// @param message What the test found, e.g. the database product and version, or the error.
    @JsonCreator
    public DataConnectionTestResponse(@JsonProperty("ok") final Boolean ok,
                                          @JsonProperty("message") final String message) {
        this.ok = Objects.requireNonNullElse(ok, false);
        this.message = message;
    }

    /// @return Whether the connection worked.
    public boolean isOk() {
        return ok;
    }

    /// @return What the test found, e.g. the database product and version, or the error.
    public String getMessage() {
        return message;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DataConnectionTestResponse that = (DataConnectionTestResponse) o;
        return ok == that.ok && Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ok, message);
    }

    @Override
    public String toString() {
        return "DataConnectionTestResponse{" +
               "ok=" + ok +
               ", message='" + message + '\'' +
               '}';
    }
}
