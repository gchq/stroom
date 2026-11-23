/*
 * Copyright 2023 Crown Copyright
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

import java.util.Objects;

@JsonPropertyOrder({"timeField", "windowSize", "advanceSize", "offset", "timeZone"})
@JsonInclude(Include.NON_NULL)
public final class HoppingWindow implements Window {

    @JsonProperty
    private final String timeField;
    @JsonProperty
    private final String windowSize;
    @JsonProperty
    private final String advanceSize;
    @JsonProperty
    private final String offset;
    @JsonProperty
    private final String timeZone;

    /// Creates the time windows of a `window` clause, as in
    /// `window EventTime by 1d advance 1h offset 6h`.
    ///
    /// @param timeField   The field whose time puts a row in its windows.
    /// @param windowSize  The size of each window, as a duration such as `1d`.
    /// @param advanceSize How long after one window starts the next one does, as a duration such as
    ///                    `1h`, or `null` if the windows don't overlap.
    /// @param offset      How far to move every window start, as a duration such as `6h`, or `-6h` to
    ///                    move them earlier, or `null` for none.
    /// @param timeZone    The id of the time zone that the windows follow.
    @JsonCreator
    public HoppingWindow(@JsonProperty("timeField") final String timeField,
                         @JsonProperty("windowSize") final String windowSize,
                         @JsonProperty("advanceSize") final String advanceSize,
                         @JsonProperty("offset") final String offset,
                         @JsonProperty("timeZone") final String timeZone) {
        this.timeField = timeField;
        this.windowSize = windowSize;
        this.advanceSize = advanceSize;
        this.offset = offset;
        this.timeZone = timeZone;
    }

    /// Gets the field whose time puts a row in its windows.
    ///
    /// @return The name of the time field.
    public String getTimeField() {
        return timeField;
    }

    /// Gets the size of each window.
    ///
    /// @return The size, as a duration such as `1d`.
    public String getWindowSize() {
        return windowSize;
    }

    /// Gets how long after one window starts the next one does.
    ///
    /// @return The advance, as a duration such as `1h`, or `null` if the windows don't overlap.
    public String getAdvanceSize() {
        return advanceSize;
    }

    /// Gets how far every window start is moved.
    ///
    /// @return The offset, as a duration such as `6h` or `-6h`, or `null` for none.
    public String getOffset() {
        return offset;
    }

    /// Gets the time zone that the windows follow.
    ///
    /// @return The id of the time zone.
    public String getTimeZone() {
        return timeZone;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final HoppingWindow that = (HoppingWindow) o;
        return Objects.equals(timeField, that.timeField) &&
               Objects.equals(windowSize, that.windowSize) &&
               Objects.equals(advanceSize, that.advanceSize) &&
               Objects.equals(offset, that.offset) &&
               Objects.equals(timeZone, that.timeZone);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timeField, windowSize, advanceSize, offset, timeZone);
    }

    @Override
    public String toString() {
        return "HoppingWindow{" +
               "timeField='" + timeField + '\'' +
               ", windowSize='" + windowSize + '\'' +
               ", advanceSize='" + advanceSize + '\'' +
               ", offset='" + offset + '\'' +
               ", timeZone='" + timeZone + '\'' +
               '}';
    }
}
