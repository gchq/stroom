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

import java.util.Objects;

@JsonPropertyOrder({"id", "name", "expression", "sort", "filter", "format", "group", "width", "visible", "special"})
@JsonInclude(Include.NON_NULL)
public class Field implements HasDisplayValue {
    @JsonProperty
    private String id;
    @JsonProperty
    private String name;
    @JsonProperty
    private String expression;
    @JsonProperty
    private Sort sort;
    @JsonProperty
    private Filter filter;
    @JsonProperty
    private Format format;
    @JsonProperty
    private Integer group;
    @JsonProperty
    private int width;
    @JsonProperty
    private boolean visible;
    @JsonProperty
    private boolean special;

    public Field() {
    }

    @JsonCreator
    public Field(@JsonProperty("id") final String id,
                 @JsonProperty("name") final String name,
                 @JsonProperty("expression") final String expression,
                 @JsonProperty("sort") final Sort sort,
                 @JsonProperty("filter") final Filter filter,
                 @JsonProperty("format") final Format format,
                 @JsonProperty("group") final Integer group,
                 @JsonProperty("width") final Integer width,
                 @JsonProperty("visible") final Boolean visible,
                 @JsonProperty("special") final boolean special) {
        this.id = id;
        this.name = name;
        this.expression = expression;
        this.sort = sort;
        this.filter = filter;
        this.format = format;
        this.group = group;
        if (width != null) {
            this.width = width;
        } else {
            this.width = 200;
        }
        if (visible != null) {
            this.visible = visible;
        } else {
            this.visible = true;
        }
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

    public static boolean equalsId(final Field lhs, final Field rhs) {
        if (lhs == null && rhs == null) {
            return true;
        }
        if (lhs != null && rhs != null) {
            return Objects.equals(lhs.id, rhs.id);
        }
        return false;
    }

    public static class Builder {
        private String id;
        private String name;
        private String expression;
        private Sort sort;
        private Filter filter;
        private Format format;
        private Integer group;
        private int width = 200;
        private boolean visible = true;
        private boolean special;

        public Builder copy(final Field field) {
            this.id = field.id;
            this.name = field.name;
            this.expression = field.expression;
            this.sort = field.sort;
            this.filter = field.filter;
            this.format = field.format;
            this.group = field.group;
            this.width = field.width;
            this.visible = field.visible;
            this.special = field.special;
            return this;
        }

        public Builder id(final String id) {
            this.id = id;
            return this;
        }

        public Builder name(final String name) {
            this.name = name;
            return this;
        }

        public Builder expression(final String expression) {
            this.expression = expression;
            return this;
        }

        public Builder sort(final Sort sort) {
            this.sort = sort;
            return this;
        }

        public Builder filter(final Filter filter) {
            this.filter = filter;
            return this;
        }

        public Builder format(final Format format) {
            this.format = format;
            return this;
        }

        public Builder group(final Integer group) {
            this.group = group;
            return this;
        }

        public Builder width(final int width) {
            this.width = width;
            return this;
        }

        public Builder visible(final boolean visible) {
            this.visible = visible;
            return this;
        }

        public Builder special(final boolean special) {
            this.special = special;
            return this;
        }

        public Field build() {
            return new Field(id, name, expression, sort, filter, format, group, width, visible, special);
        }
    }
}
