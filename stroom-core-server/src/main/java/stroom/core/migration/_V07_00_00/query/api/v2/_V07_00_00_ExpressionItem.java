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
 */

package stroom.core.migration._V07_00_00.query.api.v2;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = _V07_00_00_ExpressionOperator.class, name = "operator"),
        @JsonSubTypes.Type(value = _V07_00_00_ExpressionTerm.class, name = "term")
})
@XmlType(name = "ExpressionItem", propOrder = {"enabled"})
@XmlSeeAlso({_V07_00_00_ExpressionOperator.class, _V07_00_00_ExpressionTerm.class})
@XmlAccessorType(XmlAccessType.FIELD)
@ApiModel(
        description = "Base type for an item in an expression tree",
        subTypes = {_V07_00_00_ExpressionOperator.class, _V07_00_00_ExpressionTerm.class})
public abstract class _V07_00_00_ExpressionItem implements Serializable {
    private static final long serialVersionUID = -8483817637655853635L;
    private static final Boolean ENABLED_DEFAULT = Boolean.TRUE;

    @XmlElement
    @ApiModelProperty(
            value = "Whether this item in the expression tree is enabled or not",
            example = "true",
            required = true)
    private Boolean enabled;

    _V07_00_00_ExpressionItem() {
    }

    public _V07_00_00_ExpressionItem(final Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getEnabled() {
        return Optional.ofNullable(enabled).orElse(ENABLED_DEFAULT);
    }

    @JsonIgnore
    public boolean enabled() {
        return enabled == null || enabled;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        stroom.query.api.v2.ExpressionItem that = (stroom.query.api.v2.ExpressionItem) o;
        return Objects.equals(getEnabled(), that.getEnabled());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getEnabled());
    }

    abstract void append(final StringBuilder sb, final String pad, final boolean singleLine);

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        append(sb, "", true);
        return sb.toString();
    }

    public String toMultiLineString() {
        final StringBuilder sb = new StringBuilder();
        append(sb, "", false);
        return sb.toString();
    }

    /**
     * Builder for constructing a {@link stroom.query.api.v2.ExpressionItem}. This is an abstract type, each subclass
     * of ExpressionItem should provide a builder that extends this one.
     */
    public static abstract class Builder<T extends _V07_00_00_ExpressionItem, CHILD_CLASS extends Builder<T, ?>> {
        private Boolean enabled;

        public Builder() {
        }

        public Builder(final Boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * @param value Sets the terms state to enabled if true or null, disabled if false
         *
         * @return The Builder Builder, enabling method chaining
         */
        public CHILD_CLASS enabled(final Boolean value) {
            this.enabled = value;
            return self();
        }

        /**
         * Accessible to child classes when buildPojo() is called.
         *
         * @return Whether the expression is enabled or not
         */
        protected Boolean getEnabled() {
            return enabled;
        }

        protected abstract CHILD_CLASS self();

        public abstract T build();
    }
}