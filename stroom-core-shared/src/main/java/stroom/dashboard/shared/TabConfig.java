/*
 * Copyright 2016-2025 Crown Copyright
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

@JsonPropertyOrder({"id", "visible", "settings"})
@JsonInclude(Include.NON_NULL)
public class TabConfig {

    @JsonProperty("id")
    private String id;

    @JsonProperty("visible")
    private Boolean visible;

    @JsonIgnore
    private transient TabLayoutConfig parent;

    public TabConfig() {
    }

    @JsonCreator
    public TabConfig(@JsonProperty("id") final String id,
                     @JsonProperty("visible") final Boolean visible) {
        this.id = id;
        this.visible = visible;
    }

    public String getId() {
        return id;
    }

    public Boolean getVisible() {
        return visible;
    }

    public void setVisible(final Boolean visible) {
        if (visible == null || Boolean.TRUE.equals(visible)) {
            this.visible = null;
        } else {
            this.visible = visible;
        }
    }

    public boolean visible() {
        return visible == null || visible;
    }

    @JsonIgnore
    public TabLayoutConfig getParent() {
        return parent;
    }

    @JsonIgnore
    public void setParent(final TabLayoutConfig parent) {
        this.parent = parent;
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String id;
        private Boolean visible;

        private Builder() {
        }

        private Builder(final TabConfig tabConfig) {
            this.id = tabConfig.id;
            this.visible = tabConfig.visible;
        }

        public Builder id(final String value) {
            this.id = value;
            return this;
        }

        public Builder visible(final Boolean visible) {
            this.visible = visible;
            return this;
        }

        public TabConfig build() {
            return new TabConfig(id, visible);
        }
    }
}
