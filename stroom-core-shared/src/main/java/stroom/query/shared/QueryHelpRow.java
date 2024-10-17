/*
 * Copyright 2024 Crown Copyright
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

import stroom.svg.shared.SvgImage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class QueryHelpRow {

    @JsonProperty
    private final QueryHelpType type;
    @JsonProperty
    private final String id;
    @JsonProperty
    private final boolean hasChildren;
    @JsonProperty
    private final SvgImage icon;
    @JsonProperty
    private final String iconTooltip;
    @JsonProperty
    private final String title;
    @JsonProperty
    private final QueryHelpData data;

    @JsonCreator
    public QueryHelpRow(@JsonProperty("type") final QueryHelpType type,
                        @JsonProperty("id") final String id,
                        @JsonProperty("hasChildren") final boolean hasChildren,
                        @JsonProperty("icon") final SvgImage icon,
                        @JsonProperty("iconTooltip") final String iconTooltip,
                        @JsonProperty("title") final String title,
                        @JsonProperty("data") final QueryHelpData data) {
        this.type = type;
        this.id = id;
        this.hasChildren = hasChildren;
        this.icon = icon;
        this.iconTooltip = iconTooltip;
        this.title = title;
        this.data = data;
    }

    public QueryHelpType getType() {
        return type;
    }

    public String getId() {
        return id;
    }

    public boolean isHasChildren() {
        return hasChildren;
    }

    public String getTitle() {
        return title;
    }

    public SvgImage getIcon() {
        return icon;
    }

    public String getIconTooltip() {
        return iconTooltip;
    }

    public QueryHelpData getData() {
        return data;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof QueryHelpRow)) {
            return false;
        }
        return Objects.equals(id, ((QueryHelpRow) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }


    // --------------------------------------------------------------------------------


    public static class Builder {

        private QueryHelpType type = QueryHelpType.TITLE;
        private String id;
        private boolean hasChildren;
        private SvgImage icon;
        private String iconTooltip;
        private String title;
        private QueryHelpData data;

        public Builder() {
        }

        public Builder(final QueryHelpRow row) {
            this.type = row.type;
            this.id = row.id;
            this.hasChildren = row.hasChildren;
            this.icon = row.icon;
            this.iconTooltip = row.iconTooltip;
            this.title = row.title;
            this.data = row.data;
        }

        public Builder type(final QueryHelpType type) {
            this.type = type;
            return this;
        }


        public Builder id(final String id) {
            this.id = id;
            return this;
        }

        public Builder hasChildren(final boolean hasChildren) {
            this.hasChildren = hasChildren;
            return this;
        }

        public Builder icon(final SvgImage icon) {
            this.icon = icon;
            return this;
        }

        public Builder iconTooltip(final String iconTooltip) {
            this.iconTooltip = iconTooltip;
            return this;
        }

        public Builder title(final String title) {
            this.title = title;
            return this;
        }

        public Builder data(final QueryHelpData data) {
            this.data = data;
            return this;
        }

        public QueryHelpRow build() {
            return new QueryHelpRow(type, id, hasChildren, icon, iconTooltip, title, data);
        }
    }
}
