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

import stroom.util.shared.HasDisplayValue;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;
import java.util.Objects;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "field", propOrder = {"id", "name", "expression", "sort", "filter", "format", "group", "width", "visible", "special"})
public class Field implements Serializable, HasDisplayValue {
    private static final long serialVersionUID = 7327802315955158337L;

    @XmlElement(name = "id")
    private String id;
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
    @XmlElement(name = "special")
    private boolean special = false;

    public Field() {
        // Default constructor necessary for GWT serialisation.
    }

    public Field(final String name) {
        this.name = name;
    }

    public Field(final String id,
                 final String name,
                 final String expression,
                 final Sort sort,
                 final Filter filter,
                 final Format format,
                 final Integer group,
                 final int width,
                 final boolean visible) {
        this.id = id;
        this.name = name;
        this.expression = expression;
        this.sort = sort;
        this.filter = filter;
        this.format = format;
        this.group = group;
        this.width = width;
        this.visible = visible;
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
        field.sort = sort;
        field.filter = filter;
        field.format = format;
        field.group = group;
        field.width = width;
        field.visible = visible;
        field.special = special;

        return field;
    }
}
