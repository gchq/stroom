/*
 * Copyright 2019 Crown Copyright
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
        "value",
        "format",
        "pattern"
})
@JsonInclude(Include.NON_NULL)
public final class TextInputComponentSettings implements ComponentSettings {

    @JsonProperty
    private final String key;
    @JsonProperty
    private final String value;
    @JsonProperty
    private final TextInputFormat format;
    @JsonProperty
    private final String pattern;

    @JsonCreator
    public TextInputComponentSettings(@JsonProperty("key") final String key,
                                      @JsonProperty("value") final String value,
                                      @JsonProperty("format") final TextInputFormat format,
                                      @JsonProperty("pattern") final String pattern) {
        this.key = key;
        this.value = value;
        this.format = Objects.requireNonNullElse(format, TextInputFormat.TEXT);
        this.pattern = pattern;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    /// @return The format the entered value must conform to. Never `null`.
    public TextInputFormat getFormat() {
        return format;
    }

    /// @return The regular expression the entered value must fully match when the format is
    /// [TextInputFormat#PATTERN].
    public String getPattern() {
        return pattern;
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

//        // TODO : REMOVE - GWT DEBUG
//        final boolean b1 = Objects.equals(key, that.key);
//        final boolean b2 = Objects.equals(value, that.value);

        return Objects.equals(key, that.key) &&
                Objects.equals(value, that.value) &&
                format == that.format &&
                Objects.equals(pattern, that.pattern);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value, format, pattern);
    }

    @Override
    public String toString() {
        return "DropDownInputComponentSettings{" +
                "key='" + key + '\'' +
                ", value='" + value + '\'' +
                ", format=" + format +
                ", pattern='" + pattern + '\'' +
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
        private TextInputFormat format;
        private String pattern;

        private Builder() {
        }

        private Builder(final TextInputComponentSettings keyValueInputComponentSettings) {
            this.key = keyValueInputComponentSettings.key;
            this.value = keyValueInputComponentSettings.value;
            this.format = keyValueInputComponentSettings.format;
            this.pattern = keyValueInputComponentSettings.pattern;
        }

        public Builder key(final String key) {
            this.key = key;
            return self();
        }

        public Builder value(final String value) {
            this.value = value;
            return self();
        }

        /// @param format The format the entered value must conform to. `null` means
        ///               [TextInputFormat#TEXT].
        /// @return This builder.
        public Builder format(final TextInputFormat format) {
            this.format = format;
            return self();
        }

        /// @param pattern The regular expression the entered value must fully match when the
        ///                format is [TextInputFormat#PATTERN].
        /// @return This builder.
        public Builder pattern(final String pattern) {
            this.pattern = pattern;
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
                    value,
                    format,
                    pattern
            );
        }
    }
}
