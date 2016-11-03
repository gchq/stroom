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
@XmlType(name = "term", propOrder = { "field", "condition", "value", "dictionary" })
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
}
