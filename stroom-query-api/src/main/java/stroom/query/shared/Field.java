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

import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.HashCodeBuilder;
import stroom.util.shared.ToStringBuilder;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "field", propOrder = { "name", "expression", "sort", "filter", "format", "group", "width", "visible" })
public class Field implements Serializable {
    private static final long serialVersionUID = 7327802315955158337L;

    @XmlElement(name = "name")
    private String name;
    @XmlElement(name = "expression")
    private String expression;
    @XmlElement(name = "sort")
    private Sort sort;
    @XmlElement(name = "filter")
    private Filter filter;
    @XmlElement(name = "format")
    private Format format;
    @XmlElement(name = "group")
    private Integer group;
    @XmlElement(name = "width")
    private int width = 200;
    @XmlElement(name = "visible")
    private boolean visible = true;

    public Field() {
        // Default constructor necessary for GWT serialisation.
    }

    public Field(final String name) {
        this.name = name;
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

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof Field)) {
            return false;
        }

        final Field field = (Field) o;
        final EqualsBuilder builder = new EqualsBuilder();
        builder.append(name, field.name);
        builder.append(expression, field.expression);
        builder.append(sort, field.sort);
        builder.append(filter, field.filter);
        builder.append(format, field.format);
        builder.append(group, field.group);
        return builder.isEquals();
    }

    @Override
    public int hashCode() {
        final HashCodeBuilder builder = new HashCodeBuilder();
        builder.append(name);
        builder.append(expression);
        builder.append(sort);
        builder.append(filter);
        builder.append(format);
        builder.append(group);
        return builder.toHashCode();
    }

    @Override
    public String toString() {
        final ToStringBuilder builder = new ToStringBuilder();
        builder.append("name", name);
        builder.append("expression", expression);
        builder.append("sort", sort);
        builder.append("filter", filter);
        builder.append("format", format);
        builder.append("group", group);
        return builder.toString();
    }

    public Field copy() {
        final Field field = new Field();
        field.name = name;
        field.expression = expression;
        field.sort = sort;
        field.filter = filter;
        field.format = format;
        field.group = group;
        field.width = width;
        field.visible = visible;

        return field;
    }
}
