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
@XmlType(name = "DataReceiptRule", propOrder = {"creationTime", "name", "enabled", "expression", "action"})
@XmlRootElement(name = "dataReceiptRule")
public class DataReceiptRule implements SharedObject {
    private static final long serialVersionUID = -4466080173384628077L;

    @XmlElement(name = "creationTime")
    private long creationTime;
    @XmlElement(name = "name")
    private String name;
    @XmlElement(name = "enabled")
    private boolean enabled;
    @XmlElement(name = "expression")
    private ExpressionOperator expression;
    @XmlElement(name = "action")
    private DataReceiptAction action;

    private transient int ruleNumber;

    public DataReceiptRule() {
        // Default constructor for GWT serialisation.
    }

    public DataReceiptRule(
            final ExpressionOperator expression,
            final DataReceiptAction action) {
        this.expression = expression;
        this.action = action;
    }

    public DataReceiptRule(final long creationTime,
                           final String name,
                           final boolean enabled,
                           final ExpressionOperator expression,
                           final DataReceiptAction action) {
        this.creationTime = creationTime;
        this.name = name;
        this.enabled = enabled;
        this.expression = expression;
        this.action = action;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public ExpressionOperator getExpression() {
        return expression;
    }

    public DataReceiptAction getAction() {
        return action;
    }

    @XmlTransient
    public int getRuleNumber() {
        return ruleNumber;
    }

    public void setRuleNumber(final int ruleNumber) {
        this.ruleNumber = ruleNumber;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final DataReceiptRule that = (DataReceiptRule) o;

        if (creationTime != that.creationTime) return false;
        if (enabled != that.enabled) return false;
        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        if (expression != null ? !expression.internalEquals(that.expression) : that.expression != null) return false;
        return action == that.action;
    }

    @Override
    public int hashCode() {
        int result = (int) (creationTime ^ (creationTime >>> 32));
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (enabled ? 1 : 0);
        result = 31 * result + (expression != null ? expression.internalHashCode() : 0);
        result = 31 * result + (action != null ? action.hashCode() : 0);
        return result;
    }
}
