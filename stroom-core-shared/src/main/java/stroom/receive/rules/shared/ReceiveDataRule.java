/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.receive.rules.shared;


import stroom.query.api.v2.ExpressionOperator;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

import java.util.Objects;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DataReceiptRule", propOrder = {
        "ruleNumber",
        "creationTime",
        "name",
        "enabled",
        "expression",
        "action"})
@XmlRootElement(name = "dataReceiptRule")
@JsonPropertyOrder({"ruleNumber", "creationTime", "name", "enabled", "expression", "action"})
@JsonInclude(Include.NON_NULL)
public class ReceiveDataRule {

    @XmlElement(name = "ruleNumber")
    @JsonProperty
    private final int ruleNumber;
    @XmlElement(name = "creationTime")
    @JsonProperty
    private final long creationTime;
    @XmlElement(name = "name")
    @JsonProperty
    private final String name;
    @XmlElement(name = "enabled")
    @JsonProperty
    private final boolean enabled;
    @XmlElement(name = "expression")
    @JsonProperty
    private final ExpressionOperator expression;
    @XmlElement(name = "action")
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

    private ReceiveDataRule(final Builder builder) {
        ruleNumber = builder.ruleNumber;
        creationTime = builder.creationTime;
        name = builder.name;
        enabled = builder.enabled;
        expression = builder.expression;
        action = builder.action;
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
        String ruleName;
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

    public static Builder copy(final ReceiveDataRule copy) {
        Builder builder = new Builder();
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
