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
        "text"
})
@JsonInclude(Include.NON_NULL)
public final class KeyValueInputComponentSettings implements ComponentSettings {

    @JsonProperty
    private final String text;

    @JsonCreator
    public KeyValueInputComponentSettings(@JsonProperty("text") final String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final KeyValueInputComponentSettings that = (KeyValueInputComponentSettings) o;
        return Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text);
    }

    @Override
    public String toString() {
        return text;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder extends ComponentSettings
            .AbstractBuilder<KeyValueInputComponentSettings, KeyValueInputComponentSettings.Builder> {

        private String text;

        private Builder() {
        }

        private Builder(final KeyValueInputComponentSettings keyValueInputComponentSettings) {
            this.text = keyValueInputComponentSettings.text;
        }

        public Builder text(final String text) {
            this.text = text;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public KeyValueInputComponentSettings build() {
            return new KeyValueInputComponentSettings(
                    text
            );
        }
    }
}
