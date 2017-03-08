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

package stroom.query.api;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ExpressionOperator.class, name = "operator"),
        @JsonSubTypes.Type(value = ExpressionTerm.class, name = "term")
})
@XmlType(name = "ExpressionItem", propOrder = {"enabled"})
@XmlSeeAlso({ExpressionOperator.class, ExpressionTerm.class})
@XmlAccessorType(XmlAccessType.FIELD)
public abstract class ExpressionItem implements Serializable {
    private static final long serialVersionUID = -8483817637655853635L;

    @XmlElement
    private Boolean enabled;

    public ExpressionItem() {
    }

    public ExpressionItem(final Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(final Boolean enabled) {
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled == null || enabled;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof ExpressionItem)) return false;

        final ExpressionItem that = (ExpressionItem) o;

        return enabled != null ? enabled.equals(that.enabled) : that.enabled == null;
    }

    @Override
    public int hashCode() {
        return enabled != null ? enabled.hashCode() : 0;
    }

    public abstract void append(final StringBuilder sb, final String pad, final boolean singleLine);

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        append(sb, "", true);
        return sb.toString().trim();
    }

    public String toMultiLineString() {
        final StringBuilder sb = new StringBuilder();
        append(sb, "", false);
        return sb.toString().trim();
    }
}