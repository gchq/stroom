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

package stroom.core.db.migration._V07_00_00.doc.dashboard;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.core.db.migration._V07_00_00.util.shared._V07_00_00_EqualsBuilder;
import stroom.core.db.migration._V07_00_00.util.shared._V07_00_00_HashCodeBuilder;
import stroom.core.db.migration._V07_00_00.util.shared._V07_00_00_ToStringBuilder;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@JsonPropertyOrder({"name", "expression", "sort", "filter", "format", "group", "width", "visible"})
@XmlRootElement(name = "field")
@XmlType(name = "Field", propOrder = {"name", "expression", "sort", "filter", "format", "group", "width", "visible"})
public class _V07_00_00_Field implements Serializable {
    private static final long serialVersionUID = 7327802315955158337L;

    @XmlElement(name = "name")
    private String name;
    @XmlElement(name = "expression")
    private String expression;
    @XmlElement(name = "sort")
    private _V07_00_00_Sort sort;
    @XmlElement(name = "filter")
    private _V07_00_00_Filter filter;
    @XmlElement(name = "format")
    private _V07_00_00_Format format;
    @XmlElement(name = "group")
    private Integer group;
    @XmlElement(name = "width")
    private int width = 200;
    @XmlElement(name = "visible")
    private boolean visible = true;

    public _V07_00_00_Field() {
        // Default constructor necessary for GWT serialisation.
    }

    public _V07_00_00_Field(final String name) {
        this.name = name;
    }

    public _V07_00_00_Field(String name, String expression, _V07_00_00_Sort sort, _V07_00_00_Filter filter, _V07_00_00_Format format, Integer group, int width, boolean visible) {
        this.name = name;
        this.expression = expression;
        this.sort = sort;
        this.filter = filter;
        this.format = format;
        this.group = group;
        this.width = width;
        this.visible = visible;
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

    public _V07_00_00_Sort getSort() {
        return sort;
    }

    public void setSort(final _V07_00_00_Sort sort) {
        this.sort = sort;
    }

    public _V07_00_00_Filter getFilter() {
        return filter;
    }

    public void setFilter(final _V07_00_00_Filter filter) {
        this.filter = filter;
    }

    public _V07_00_00_Format getFormat() {
        return format;
    }

    public void setFormat(final _V07_00_00_Format format) {
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
        } else if (!(o instanceof _V07_00_00_Field)) {
            return false;
        }

        final _V07_00_00_Field field = (_V07_00_00_Field) o;
        final _V07_00_00_EqualsBuilder builder = new _V07_00_00_EqualsBuilder();
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
        final _V07_00_00_HashCodeBuilder builder = new _V07_00_00_HashCodeBuilder();
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
        final _V07_00_00_ToStringBuilder builder = new _V07_00_00_ToStringBuilder();
        builder.append("name", name);
        builder.append("expression", expression);
        builder.append("sort", sort);
        builder.append("filter", filter);
        builder.append("format", format);
        builder.append("group", group);
        return builder.toString();
    }

    public _V07_00_00_Field copy() {
        final _V07_00_00_Field field = new _V07_00_00_Field();
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
