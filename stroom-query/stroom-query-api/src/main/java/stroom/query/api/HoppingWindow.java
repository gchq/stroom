/*
 * Copyright 2016-2025 Crown Copyright
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

@JsonPropertyOrder({"timeField", "windowSize", "advanceSize"})
@JsonInclude(Include.NON_NULL)
public final class HoppingWindow implements Window {

    @JsonProperty
    private final String timeField;
    @JsonProperty
    private final String windowSize;
    @JsonProperty
    private final String advanceSize;
    @JsonProperty
    private final String function;

    @JsonCreator
    public HoppingWindow(@JsonProperty("timeField") final String timeField,
                         @JsonProperty("windowSize") final String windowSize,
                         @JsonProperty("advanceSize") final String advanceSize,
                         @JsonProperty("function") final String function) {
        this.timeField = timeField;
        this.windowSize = windowSize;
        this.advanceSize = advanceSize;
        this.function = function;
    }

    public String getTimeField() {
        return timeField;
    }

    public String getWindowSize() {
        return windowSize;
    }

    public String getAdvanceSize() {
        return advanceSize;
    }

    public String getFunction() {
        return function;
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
        return Objects.equals(timeField, that.timeField) && Objects.equals(windowSize,
                that.windowSize) && Objects.equals(advanceSize, that.advanceSize) && Objects.equals(
                function,
                that.function);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timeField, windowSize, advanceSize, function);
    }

    @Override
    public String toString() {
        return "HoppingWindow{" +
                "timeField='" + timeField + '\'' +
                ", windowSize='" + windowSize + '\'' +
                ", advanceSize='" + advanceSize + '\'' +
                ", function='" + function + '\'' +
                '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String timeField = "EventTime";
        private String windowSize = "10m";
        private String advanceSize = "1m";
        private String function = "count()";

        private Builder() {
        }

        private Builder(final HoppingWindow hoppingWindow) {
            this.timeField = hoppingWindow.timeField;
            this.windowSize = hoppingWindow.windowSize;
            this.advanceSize = hoppingWindow.advanceSize;
            this.function = hoppingWindow.function;
        }

        public Builder timeField(final String timeField) {
            this.timeField = timeField;
            return this;
        }

        public Builder windowSize(final String windowSize) {
            this.windowSize = windowSize;
            return this;
        }

        public Builder advanceSize(final String advanceSize) {
            this.advanceSize = advanceSize;
            return this;
        }

        public Builder function(final String function) {
            this.function = function;
            return this;
        }

        public HoppingWindow build() {
            return new HoppingWindow(timeField, windowSize, advanceSize, function);
        }
    }
}
