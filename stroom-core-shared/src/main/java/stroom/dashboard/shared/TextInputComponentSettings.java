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

@JsonPropertyOrder({
        "key",
        "value"
})
@JsonInclude(Include.NON_NULL)
public final class TextInputComponentSettings implements ComponentSettings {

    @JsonProperty
    private final String key;
    @JsonProperty
    private final String value;

    @JsonCreator
    public TextInputComponentSettings(@JsonProperty("key") final String key,
                                      @JsonProperty("value") final String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TextInputComponentSettings that = (TextInputComponentSettings) o;
        return Objects.equals(key, that.key) &&
                Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }

    @Override
    public String toString() {
        return "DropDownInputComponentSettings{" +
                "key='" + key + '\'' +
                ", value='" + value + '\'' +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder extends ComponentSettings
            .AbstractBuilder<TextInputComponentSettings, TextInputComponentSettings.Builder> {

        private String key;
        private String value;

        private Builder() {
        }

        private Builder(final TextInputComponentSettings keyValueInputComponentSettings) {
            this.key = keyValueInputComponentSettings.key;
            this.value = keyValueInputComponentSettings.value;
        }

        public Builder key(final String key) {
            this.key = key;
            return self();
        }

        public Builder value(final String value) {
            this.value = value;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public TextInputComponentSettings build() {
            return new TextInputComponentSettings(
                    key,
                    value
            );
        }
    }
}
