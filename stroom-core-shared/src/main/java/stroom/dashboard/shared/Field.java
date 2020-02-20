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

package stroom.dashboard.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.docref.HasDisplayValue;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;
import java.util.Objects;

@XmlAccessorType(XmlAccessType.FIELD)
@JsonPropertyOrder({"id", "name", "expression", "sort", "filter", "format", "group", "width", "visible", "special"})
@JsonInclude(Include.NON_DEFAULT)
@XmlRootElement(name = "field")
@XmlType(name = "Field", propOrder = {"id", "name", "expression", "sort", "filter", "format", "group", "width", "visible", "special"})
public class Field implements Serializable, HasDisplayValue {
    private static final long serialVersionUID = 7327802315955158337L;

    @XmlElement(name = "id")
    @JsonProperty("id")
    private String id;
    @XmlElement(name = "name")
    @JsonProperty("name")
    private String name;
    @XmlElement(name = "expression")
    @JsonProperty("expression")
    private String expression;
    @XmlElement(name = "sort")
    @JsonProperty("sort")
    private Sort sort;
    @XmlElement(name = "filter")
    @JsonProperty("filter")
    private Filter filter;
    @XmlElement(name = "format")
    @JsonProperty("format")
    private Format format;
    @XmlElement(name = "group")
    @JsonProperty("group")
    private Integer group;
    @XmlElement(name = "width")
    @JsonProperty("width")
    private int width = 200;
    @XmlElement(name = "visible")
    @JsonProperty("visible")
    private boolean visible = true;
    @XmlElement(name = "special")
    @JsonProperty(value = "special")
    private boolean special = false;

    public Field() {
    }

    public Field(final String name) {
        this.name = name;
    }

    @JsonCreator
    public Field(@JsonProperty("id") final String id,
                 @JsonProperty("name") final String name,
                 @JsonProperty("expression") final String expression,
                 @JsonProperty("sort") final Sort sort,
                 @JsonProperty("filter") final Filter filter,
                 @JsonProperty("format") final Format format,
                 @JsonProperty("group") final Integer group,
                 @JsonProperty("width") final int width,
                 @JsonProperty("visible") final boolean visible,
                 @JsonProperty("special") final boolean special) {
        this.id = id;
        this.name = name;
        this.expression = expression;
        this.sort = sort;
        this.filter = filter;
        this.format = format;
        this.group = group;
        this.width = width;
        this.visible = visible;
        this.special = special;
    }

    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getExpression() {
        return expression;
    }

    public void setExpression(final String expression) {
        this.expression = expression;
    }

    public Sort getSort() {
        return sort;
    }

    public void setSort(final Sort sort) {
        this.sort = sort;
    }

    public Filter getFilter() {
        return filter;
    }

    public void setFilter(final Filter filter) {
        this.filter = filter;
    }

    public Format getFormat() {
        return format;
    }

    public void setFormat(final Format format) {
        this.format = format;
    }

    public Integer getGroup() {
        return group;
    }

    public void setGroup(final Integer group) {
        this.group = group;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(final int width) {
        this.width = width;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(final boolean visible) {
        this.visible = visible;
    }

    public boolean isSpecial() {
        return special;
    }

    public void setSpecial(final boolean special) {
        this.special = special;
    }

    @JsonIgnore
    @XmlTransient
    @Override
    public String getDisplayValue() {
        return name;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Field field = (Field) o;

        return Objects.equals(id, field.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public Field copy() {
        final Field field = new Field();
        field.id = id;
        field.name = name;
        field.expression = expression;
        if (sort != null) {
            field.sort = sort.copy();
        }
        if (filter != null) {
            field.filter = filter.copy();
        }
        if (format != null) {
            field.format = format.copy();
        }
        field.group = group;
        field.width = width;
        field.visible = visible;
        field.special = special;

        return field;
    }

    public static boolean equalsId(final Field lhs, final Field rhs) {
        if (lhs == null && rhs == null) {
            return true;
        }
        if (lhs != null && rhs != null) {
            return Objects.equals(lhs.id, rhs.id);
        }
        return false;
    }
}
