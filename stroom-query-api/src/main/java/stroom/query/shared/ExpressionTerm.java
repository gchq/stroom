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

package stroom.query.shared;

import stroom.entity.shared.DocRef;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "term", propOrder = {"field", "condition", "value", "dictionary"})
public class ExpressionTerm extends ExpressionItem {
    private static final long serialVersionUID = 9035311895540457146L;

    @XmlElement(name = "field")
    private String field;
    @XmlElement(name = "condition")
    private Condition condition = Condition.CONTAINS;
    @XmlElement(name = "value")
    private String value;
    @XmlElement(name = "dictionary")
    private DocRef dictionary;

    public ExpressionTerm() {
        // Default constructor necessary for GWT serialisation.
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

    public String getField() {
        return field;
    }

    public void setField(final String field) {
        this.field = field;
    }

    public Condition getCondition() {
        return condition;
    }

    public void setCondition(final Condition condition) {
        this.condition = condition;
    }

    public String getValue() {
        return value;
    }

    public void setValue(final String value) {
        this.value = value;
    }

    public DocRef getDictionary() {
        return dictionary;
    }

    public void setDictionary(final DocRef dictionary) {
        this.dictionary = dictionary;
    }

    @Override
    public void append(final StringBuilder sb, final String pad, final boolean singleLine) {
        if (isEnabled()) {
            if (field != null) {
                sb.append(field);
            }
            sb.append(" ");
            if (condition != null) {
                sb.append(condition.getDisplayValue());
            }
            sb.append(" ");
            if (Condition.IN_DICTIONARY.equals(condition)) {
                if (dictionary != null) {
                    sb.append(dictionary.getUuid());
                }
            } else if (value != null) {
                sb.append(value);
            }
        }
    }

    protected <T extends ExpressionTerm> T copyTo(T dest) {
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
    public boolean contains(final String fieldToFind) {
        return this.field.equals(fieldToFind);
    }

    @Override
    public boolean internalEquals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final ExpressionTerm term = (ExpressionTerm) o;

        if (field != null ? !field.equals(term.field) : term.field != null) return false;
        if (condition != term.condition) return false;
        if (value != null ? !value.equals(term.value) : term.value != null) return false;
        return dictionary != null ? dictionary.equals(term.dictionary) : term.dictionary == null;
    }

    @Override
    public int internalHashCode() {
        int result = field != null ? field.hashCode() : 0;
        result = 31 * result + (condition != null ? condition.hashCode() : 0);
        result = 31 * result + (value != null ? value.hashCode() : 0);
        result = 31 * result + (dictionary != null ? dictionary.hashCode() : 0);
        return result;
    }
}
