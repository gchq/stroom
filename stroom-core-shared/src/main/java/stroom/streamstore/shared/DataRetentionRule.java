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

package stroom.streamstore.shared;

import stroom.query.shared.ExpressionOperator;
import stroom.util.shared.SharedObject;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DataRetentionRule", propOrder = { "enabled", "expression", "age", "timeUnit", "forever" })
@XmlRootElement(name = "dataRetentionRule")
public class DataRetentionRule implements SharedObject {
    public static final String FOREVER = "Forever";

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

    public DataRetentionRule(final boolean enabled, final ExpressionOperator expression, final int age, final TimeUnit timeUnit, final boolean forever) {
        this.enabled = enabled;
        this.expression = expression;
        this.age = age;
        this.timeUnit = timeUnit;
        this.forever = forever;
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

        if (age != that.age) return false;
        if (forever != that.forever) return false;
        if (expression != null ? !expression.equals(that.expression) : that.expression != null) return false;
        return timeUnit == that.timeUnit;
    }

    @Override
    public int hashCode() {
        int result = expression != null ? expression.hashCode() : 0;
        result = 31 * result + age;
        result = 31 * result + (timeUnit != null ? timeUnit.hashCode() : 0);
        result = 31 * result + (forever ? 1 : 0);
        return result;
    }
}
