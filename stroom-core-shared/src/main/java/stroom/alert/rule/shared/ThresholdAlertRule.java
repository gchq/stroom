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

import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class ThresholdAlertRule extends AbstractAlertRule {

    @JsonCreator
    public ThresholdAlertRule(@JsonProperty("executionDelay") final String executionDelay,
                              @JsonProperty("executionFrequency") final String executionFrequency,
                              @JsonProperty("timeField") final String timeField,
                              @JsonProperty("destinationFeed") final DocRef destinationFeed) {
        super(timeField, executionDelay, executionFrequency, destinationFeed);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static class Builder extends AbstractBuilder<ThresholdAlertRule, Builder> {


        public Builder() {
        }

        public Builder(final ThresholdAlertRule alertRule) {
            super(alertRule);
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public ThresholdAlertRule build() {
            return new ThresholdAlertRule(executionDelay, executionFrequency, timeField, destinationFeed);
        }
    }
}
