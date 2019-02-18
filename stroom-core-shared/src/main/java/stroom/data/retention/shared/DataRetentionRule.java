/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.data.retention.shared;

import stroom.query.api.v2.ExpressionOperator;
import stroom.docref.SharedObject;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import java.util.Objects;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DataRetentionRule", propOrder = {"ruleNumber", "creationTime", "name", "enabled", "expression", "age", "timeUnit", "forever"})
@XmlRootElement(name = "dataRetentionRule")
public class DataRetentionRule implements SharedObject {
    public static final String FOREVER = "Forever";

    @XmlElement(name = "ruleNumber")
    private int ruleNumber;
    @XmlElement(name = "creationTime")
    private long creationTime;
    @XmlElement(name = "name")
    private String name;
    @XmlElement(name = "enabled")
    private boolean enabled;
    @XmlElement(name = "expression")
    private ExpressionOperator expression;
    @XmlElement(name = "age")
    private int age;
    @XmlElement(name = "timeUnit")
    private TimeUnit timeUnit;
    @XmlElement(name = "forever")
    private boolean forever;

    public DataRetentionRule() {
        // Default constructor for GWT serialisation.
    }

    public DataRetentionRule(final int ruleNumber,
                             final long creationTime,
                             final String name,
                             final boolean enabled,
                             final ExpressionOperator expression,
                             final int age,
                             final TimeUnit timeUnit,
                             final boolean forever) {
        this.ruleNumber = ruleNumber;
        this.creationTime = creationTime;
        this.name = name;
        this.enabled = enabled;
        this.expression = expression;
        this.age = age;
        this.timeUnit = timeUnit;
        this.forever = forever;
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

    @XmlTransient
    public String getAgeString() {
        if (forever) {
            return FOREVER;
        }

        final StringBuilder sb = new StringBuilder()
                .append(age)
                .append(" ")
                .append(timeUnit.getDisplayValue());
        if (age == 1) {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
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
        String ruleName;
        if (name != null && !name.isEmpty()) {
            ruleName = ruleNumber + " " + name;
        } else {
            ruleName = String.valueOf(ruleNumber);
        }
        return ruleName;
    }
}