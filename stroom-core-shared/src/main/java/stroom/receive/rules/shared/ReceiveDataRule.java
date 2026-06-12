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

package stroom.receive.rules.shared;


import stroom.query.api.ExpressionOperator;
import stroom.util.shared.SerialisationTestConstructor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({"ruleNumber", "creationTime", "name", "enabled", "expression", "action"})
@JsonInclude(Include.NON_NULL)
public class ReceiveDataRule {

    @JsonProperty
    private final int ruleNumber;
    @JsonProperty
    private final long creationTime;
    @JsonProperty
    private final String name;
    @JsonProperty
    private final boolean enabled;
    @JsonProperty
    private final ExpressionOperator expression;
    @JsonProperty
    private final ReceiveAction action;

    @JsonCreator
    public ReceiveDataRule(@JsonProperty("ruleNumber") final int ruleNumber,
                           @JsonProperty("creationTime") final long creationTime,
                           @JsonProperty("name") final String name,
                           @JsonProperty("enabled") final boolean enabled,
                           @JsonProperty("expression") final ExpressionOperator expression,
                           @JsonProperty("action") final ReceiveAction action) {
        this.ruleNumber = ruleNumber;
        this.creationTime = creationTime;
        this.name = name;
        this.enabled = enabled;
        this.expression = expression;
        this.action = Objects.requireNonNull(action);
    }

    @SerialisationTestConstructor
    private ReceiveDataRule() {
        this(1,
                0L,
                "test",
                true,
                ExpressionOperator.builder().build(),
                ReceiveAction.RECEIVE);
    }

    private ReceiveDataRule(final Builder builder) {
        this(builder.ruleNumber,
                builder.creationTime,
                builder.name,
                builder.enabled,
                builder.expression,
                builder.action);
    }

    public int getRuleNumber() {
        return ruleNumber;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public String getName() {
        return name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public ExpressionOperator getExpression() {
        return expression;
    }

    public ReceiveAction getAction() {
        return action;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ReceiveDataRule that = (ReceiveDataRule) o;
        return ruleNumber == that.ruleNumber &&
               creationTime == that.creationTime &&
               enabled == that.enabled &&
               Objects.equals(name, that.name) &&
               Objects.equals(expression, that.expression) &&
               action == that.action;
    }

    @Override
    public int hashCode() {
        return Objects.hash(ruleNumber, creationTime, name, enabled, expression, action);
    }

    @Override
    public String toString() {
        // Create a rule name that includes the rule number.
        final String ruleName;
        if (name != null && !name.isEmpty()) {
            ruleName = ruleNumber + " " + name;
        } else {
            ruleName = String.valueOf(ruleNumber);
        }
        return ruleName;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return copy(this);
    }

    public static Builder copy(final ReceiveDataRule copy) {
        final Builder builder = new Builder();
        builder.ruleNumber = copy.getRuleNumber();
        builder.creationTime = copy.getCreationTime();
        builder.name = copy.getName();
        builder.enabled = copy.isEnabled();
        builder.expression = copy.getExpression();
        builder.action = copy.getAction();
        return builder;
    }


    // --------------------------------------------------------------------------------


    public static final class Builder {

        private int ruleNumber;
        private long creationTime;
        private String name;
        private boolean enabled;
        private ExpressionOperator expression;
        private ReceiveAction action;

        private Builder() {
        }

        public Builder withRuleNumber(final int ruleNumber) {
            this.ruleNumber = ruleNumber;
            return this;
        }

        public Builder withCreationTime(final long creationTime) {
            this.creationTime = creationTime;
            return this;
        }

        public Builder withName(final String name) {
            this.name = name;
            return this;
        }

        public Builder withEnabled(final boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder withExpression(final ExpressionOperator expression) {
            this.expression = expression;
            return this;
        }

        public Builder withAction(final ReceiveAction action) {
            this.action = action;
            return this;
        }

        public ReceiveDataRule build() {
            return new ReceiveDataRule(this);
        }
    }
}
