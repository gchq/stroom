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

package stroom.alert.rule.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class ThresholdAlertRule extends AbstractAlertRule {

    @JsonProperty
    private final String timeField;
    @JsonProperty
    private final String thresholdField;
    @JsonProperty
    private final long threshold;

    @JsonCreator
    public ThresholdAlertRule(@JsonProperty("executionDelay") final String executionDelay,
                              @JsonProperty("executionFrequency") final String executionFrequency,
                              @JsonProperty("timeField") final String timeField,
                              @JsonProperty("thresholdField") final String thresholdField,
                              @JsonProperty("threshold") final long threshold) {
        super(executionDelay, executionFrequency);
        this.timeField = timeField;
        this.thresholdField = thresholdField;
        this.threshold = threshold;
    }

    public String getTimeField() {
        return timeField;
    }

    public String getThresholdField() {
        return thresholdField;
    }

    public long getThreshold() {
        return threshold;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final ThresholdAlertRule that = (ThresholdAlertRule) o;
        return threshold == that.threshold && Objects.equals(timeField,
                that.timeField) && Objects.equals(thresholdField, that.thresholdField);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), timeField, thresholdField, threshold);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static class Builder extends AbstractBuilder<ThresholdAlertRule, Builder> {

        private String timeField = "EventTime";
        private String thresholdField;
        private long threshold;

        public Builder() {
        }

        public Builder(final ThresholdAlertRule alertRule) {
            super(alertRule);
            this.timeField = alertRule.timeField;
            this.thresholdField = alertRule.thresholdField;
            this.threshold = alertRule.threshold;
        }

        public Builder timeField(final String timeField) {
            this.timeField = timeField;
            return this;
        }

        public Builder thresholdField(final String thresholdField) {
            this.thresholdField = thresholdField;
            return self();
        }

        public Builder threshold(final long threshold) {
            this.threshold = threshold;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public ThresholdAlertRule build() {
            return new ThresholdAlertRule(executionDelay, executionFrequency, timeField, thresholdField, threshold);
        }
    }
}
