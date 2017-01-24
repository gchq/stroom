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

package stroom.query.api;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.util.shared.HasDisplayValue;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;

@JsonPropertyOrder({"field", "condition", "value", "dictionary"})
@XmlType(name = "expressionTerm", propOrder = { "field", "condition", "value", "dictionary" })
public class ExpressionTerm extends ExpressionItem {
    private static final long serialVersionUID = 9035311895540457146L;

    private String field;
    private Condition condition;
    private String value;
    private DocRef dictionary;

    public ExpressionTerm() {
    }

    public ExpressionTerm(final String field, final Condition condition, final String value) {
        this.field = field;
        this.condition = condition;
        this.value = value;
    }

    public ExpressionTerm(final String field, final Condition condition, final DocRef dictionary) {
        this.field = field;
        this.condition = condition;
        this.dictionary = dictionary;
    }

    @XmlElement
    public String getField() {
        return field;
    }

    public void setField(final String field) {
        this.field = field;
    }

    @XmlElement
    public Condition getCondition() {
        return condition;
    }

    public void setCondition(final Condition condition) {
        this.condition = condition;
    }

    @XmlElement
    public String getValue() {
        return value;
    }

    public void setValue(final String value) {
        this.value = value;
    }

    @XmlElement
    public DocRef getDictionary() {
        return dictionary;
    }

    public void setDictionary(final DocRef dictionary) {
        this.dictionary = dictionary;
    }

    private <T extends ExpressionTerm> T copyTo(T dest) {
        dest = super.copyTo(dest);
        ((ExpressionTerm) dest).condition = condition;
        ((ExpressionTerm) dest).field = field;
        ((ExpressionTerm) dest).value = value;
        ((ExpressionTerm) dest).dictionary = dictionary;
        return dest;
    }

    @Override
    public ExpressionTerm copy() {
        return copyTo(new ExpressionTerm());
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        final ExpressionTerm that = (ExpressionTerm) o;

        if (field != null ? !field.equals(that.field) : that.field != null) return false;
        if (condition != that.condition) return false;
        if (value != null ? !value.equals(that.value) : that.value != null) return false;
        return dictionary != null ? dictionary.equals(that.dictionary) : that.dictionary == null;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (field != null ? field.hashCode() : 0);
        result = 31 * result + (condition != null ? condition.hashCode() : 0);
        result = 31 * result + (value != null ? value.hashCode() : 0);
        result = 31 * result + (dictionary != null ? dictionary.hashCode() : 0);
        return result;
    }

    @Override
    public void append(final StringBuilder sb, final String pad, final boolean singleLine) {
        if (enabled()) {
            sb.append(field);
            sb.append(" ");
            sb.append(condition.getDisplayValue());
            sb.append(" ");
            if (Condition.IN_DICTIONARY.equals(condition)) {
                if (dictionary != null) {
                    sb.append(dictionary.getUuid());
                }
            } else {
                sb.append(value);
            }
        }
    }

    public enum Condition implements HasDisplayValue {
        CONTAINS("contains"), EQUALS("="), GREATER_THAN(">"), GREATER_THAN_OR_EQUAL_TO(">="), LESS_THAN(
                "<"), LESS_THAN_OR_EQUAL_TO("<="), BETWEEN("between"), IN("in"), IN_DICTIONARY("in dictionary");

        public static final Condition[] SIMPLE_CONDITIONS = {EQUALS, GREATER_THAN, GREATER_THAN_OR_EQUAL_TO, LESS_THAN,
                LESS_THAN_OR_EQUAL_TO, BETWEEN};
        public static final String IN_CONDITION_DELIMITER = ",";
        private final String displayValue;

        Condition(final String displayValue) {
            this.displayValue = displayValue;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }
    }
}