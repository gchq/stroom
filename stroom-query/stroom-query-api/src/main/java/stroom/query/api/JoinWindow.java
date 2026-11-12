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

package stroom.query.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

/// The time window of an event-to-event join, as in `within 10m after logon.EventTime`.
///
/// A joined row matches only when its time falls inside the window, which starts or ends at the time
/// of the row it is joined to.
@JsonPropertyOrder({"duration", "after", "timeField"})
@JsonInclude(Include.NON_NULL)
@Schema(description = "The time window a joined row's time must fall in")
public final class JoinWindow {

    @Schema(description = "The length of the window as a StroomQL duration, e.g. 10m")
    @JsonProperty
    private final String duration;

    @Schema(description = "True if the window starts at the time field and runs forward, false if it ends " +
                          "there and runs back")
    @JsonProperty
    private final boolean after;

    @Schema(description = "The field of the rows being joined that holds the time the window starts or " +
                          "ends at")
    @JsonProperty
    private final String timeField;

    /// Creates a join window.
    ///
    /// @param duration  The length of the window as a StroomQL duration, e.g. `10m`.
    /// @param after     True if the window starts at the time field and runs forward, false if it
    ///                  ends there and runs back.
    /// @param timeField The field of the rows being joined that the window starts or ends at.
    @JsonCreator
    public JoinWindow(@JsonProperty("duration") final String duration,
                      @JsonProperty("after") final boolean after,
                      @JsonProperty("timeField") final String timeField) {
        this.duration = duration;
        this.after = after;
        this.timeField = timeField;
    }

    /// Gets the length of the window.
    ///
    /// @return The length as a StroomQL duration, e.g. `10m`.
    public String getDuration() {
        return duration;
    }

    /// Finds whether the window runs forward from the time field.
    ///
    /// @return True if the window starts at the time field and runs forward, false if it ends there.
    public boolean isAfter() {
        return after;
    }

    /// Gets the field the window starts or ends at.
    ///
    /// @return The field name, e.g. `logon.EventTime`.
    public String getTimeField() {
        return timeField;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final JoinWindow that = (JoinWindow) o;
        return after == that.after &&
               Objects.equals(duration, that.duration) &&
               Objects.equals(timeField, that.timeField);
    }

    @Override
    public int hashCode() {
        return Objects.hash(duration, after, timeField);
    }

    @Override
    public String toString() {
        return "within " + duration + (after
                ? " after "
                : " before ") + timeField;
    }
}
