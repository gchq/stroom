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
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Objects;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ThresholdAlertRule.class, name = "threshold")
})
@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public abstract class AbstractAlertRule {

    @JsonProperty
    private final String timeField;
    @JsonProperty
    private final String executionDelay;
    @JsonProperty
    private final String executionFrequency;

    @JsonCreator
    public AbstractAlertRule(@JsonProperty("timeField") final String timeField,
                             @JsonProperty("executionDelay") final String executionDelay,
                             @JsonProperty("executionFrequency") final String executionFrequency) {
        this.timeField = timeField;
        this.executionDelay = executionDelay;
        this.executionFrequency = executionFrequency;
    }

    public String getTimeField() {
        return timeField;
    }

    public String getExecutionDelay() {
        return executionDelay;
    }

    public String getExecutionFrequency() {
        return executionFrequency;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AbstractAlertRule that = (AbstractAlertRule) o;
        return Objects.equals(timeField, that.timeField) && Objects.equals(executionDelay,
                that.executionDelay) && Objects.equals(executionFrequency, that.executionFrequency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timeField, executionDelay, executionFrequency);
    }

    @Override
    public String toString() {
        return "AbstractAlertRule{" +
                "timeField='" + timeField + '\'' +
                ", executionDelay='" + executionDelay + '\'' +
                ", executionFrequency='" + executionFrequency + '\'' +
                '}';
    }

    public abstract static class AbstractBuilder<T extends AbstractAlertRule, B extends AbstractBuilder<T, ?>> {

        protected String timeField = "EventTime";
        protected String executionDelay;
        protected String executionFrequency;

        public AbstractBuilder() {
        }

        public AbstractBuilder(final AbstractAlertRule alertRule) {
            this.timeField = alertRule.timeField;
            this.executionDelay = alertRule.executionDelay;
            this.executionFrequency = alertRule.executionFrequency;
        }

        public B timeField(final String timeField) {
            this.timeField = timeField;
            return self();
        }

        public B executionDelay(final String executionDelay) {
            this.executionDelay = executionDelay;
            return self();
        }

        public B executionFrequency(final String executionFrequency) {
            this.executionFrequency = executionFrequency;
            return self();
        }

        protected abstract B self();

        public abstract T build();
    }
}
