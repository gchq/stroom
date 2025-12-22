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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({"type", "id", "name", "settings"})
@JsonInclude(Include.NON_NULL)
public class ComponentConfig {

    @JsonProperty("type")
    private final String type;
    @JsonProperty("id")
    private final String id;
    @JsonProperty("name")
    private final String name;
    @JsonProperty("settings")
    private final ComponentSettings settings;

    @JsonCreator
    public ComponentConfig(@JsonProperty("type") final String type,
                           @JsonProperty("id") final String id,
                           @JsonProperty("name") final String name,
                           @JsonProperty("settings") final ComponentSettings settings) {
        this.type = type;
        this.id = id;
        this.name = name;
        this.settings = settings;
    }

    public String getType() {
        return type;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public ComponentSettings getSettings() {
        return settings;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ComponentConfig that = (ComponentConfig) o;
        return Objects.equals(type, that.type) &&
                Objects.equals(id, that.id) &&
                Objects.equals(name, that.name) &&
                Objects.equals(settings, that.settings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, id, name, settings);
    }

    @Override
    public String toString() {
        return "ComponentConfig{" +
                "type='" + type + '\'' +
                ", id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", settings=" + settings +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {

        private String type;
        private String id;
        private String name;
        private ComponentSettings settings;

        private Builder() {
        }

        private Builder(final ComponentConfig componentConfig) {
            this.type = componentConfig.type;
            this.id = componentConfig.id;
            this.name = componentConfig.name;
            this.settings = componentConfig.settings == null
                    ? null
                    : componentConfig.settings.copy().build();
        }

        public Builder type(final String type) {
            this.type = type;
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

        public Builder settings(final ComponentSettings settings) {
            this.settings = settings;
            return this;
        }

        public ComponentConfig build() {
            return new ComponentConfig(type, id, name, settings);
        }
    }
}
