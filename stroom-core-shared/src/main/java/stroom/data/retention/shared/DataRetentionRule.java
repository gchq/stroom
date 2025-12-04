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

package stroom.data.retention.shared;


import stroom.query.api.ExpressionOperator;
import stroom.util.shared.time.TimeUnit;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Comparator;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class DataRetentionRule {

    public static final String FOREVER = "Forever";

    @JsonProperty
    private int ruleNumber;
    @JsonProperty
    private long creationTime;
    @JsonProperty
    private String name;
    @JsonProperty
    private boolean enabled;
    @JsonProperty
    private ExpressionOperator expression;
    @JsonProperty
    private int age;
    @JsonProperty
    private TimeUnit timeUnit;
    @JsonProperty
    private boolean forever;

    public DataRetentionRule() {
    }

    @JsonCreator
    public DataRetentionRule(@JsonProperty("ruleNumber") final int ruleNumber,
                             @JsonProperty("creationTime") final long creationTime,
                             @JsonProperty("name") final String name,
                             @JsonProperty("enabled") final boolean enabled,
                             @JsonProperty("expression") final ExpressionOperator expression,
                             @JsonProperty("age") final int age,
                             @JsonProperty("timeUnit") final TimeUnit timeUnit,
                             @JsonProperty("forever") final boolean forever) {
        this.ruleNumber = ruleNumber;
        this.creationTime = creationTime;
        this.name = name;
        this.enabled = enabled;
        this.expression = expression;
        this.age = age;
        this.timeUnit = timeUnit;
        this.forever = forever;
    }

    public static DataRetentionRule foreverRule(final int ruleNumber,
                                                final long creationTime,
                                                final String name,
                                                final boolean enabled,
                                                final ExpressionOperator expression) {
        // Forever so the age parts are ignored.
        return new DataRetentionRule(
                ruleNumber,
                creationTime,
                name,
                enabled,
                expression,
                50,
                TimeUnit.YEARS,
                true);
    }

    public static DataRetentionRule ageRule(final int ruleNumber,
                                            final long creationTime,
                                            final String name,
                                            final boolean enabled,
                                            final ExpressionOperator expression,
                                            final int age,
                                            final TimeUnit timeUnit) {
        // Forever so the age parts are ignored.
        return new DataRetentionRule(
                ruleNumber,
                creationTime,
                name,
                enabled,
                expression,
                age,
                timeUnit,
                false);
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

    public int getAge() {
        return age;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    public boolean isForever() {
        return forever;
    }

    @JsonIgnore
    public String getAgeString() {

        // IMPORTANT - The return value is used as the serialised form of the rule age in the
        // meta_retention_tracker table so think carefully before you change it

        if (forever) {
            return FOREVER;
        }

        final StringBuilder sb = new StringBuilder()
                .append(age);
        if (timeUnit != null) {
            sb.append(" ").append(timeUnit.getDisplayValue());
            if (age == 1) {
                sb.setLength(sb.length() - 1);
            }
        }
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DataRetentionRule that = (DataRetentionRule) o;
        return ruleNumber == that.ruleNumber &&
                creationTime == that.creationTime &&
                enabled == that.enabled &&
                age == that.age &&
                forever == that.forever &&
                Objects.equals(name, that.name) &&
                Objects.equals(expression, that.expression) &&
                timeUnit == that.timeUnit;
    }

    @Override
    public int hashCode() {
        return Objects.hash(ruleNumber, creationTime, name, enabled, expression, age, timeUnit, forever);
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

    public static Comparator<DataRetentionRule> comparingByRuleNumber() {
        return Comparator.comparing(DataRetentionRule::getRuleNumber);
    }

    public static Comparator<DataRetentionRule> comparingByDescendingRuleNumber() {
        return Comparator.comparing(DataRetentionRule::getRuleNumber).reversed();
    }
}
