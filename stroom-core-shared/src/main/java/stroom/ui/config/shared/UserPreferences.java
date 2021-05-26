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

package stroom.ui.config.shared;

import stroom.query.api.v2.DateTimeFormatSettings;
import stroom.query.api.v2.TimeZone;
import stroom.query.api.v2.TimeZone.Use;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;
import javax.inject.Singleton;

@Singleton
@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class UserPreferences {

    @JsonProperty
    @JsonPropertyDescription("The theme to use, e.g. `light`, `dark`")
    private final String theme;

    @JsonProperty
    @JsonPropertyDescription("The font to use, e.g. `Roboto`")
    private final String font;

    @JsonProperty
    @JsonPropertyDescription("The font size to use, e.g. `small`, `medium`, `large`")
    private final String fontSize;

    @JsonProperty
    @JsonPropertyDescription("How to display dates and times in the UI")
    private final DateTimeFormatSettings dateTimeFormat;

    @JsonCreator
    public UserPreferences(@JsonProperty("theme") final String theme,
                           @JsonProperty("font") final String font,
                           @JsonProperty("fontSize") final String fontSize,
                           @JsonProperty("dateTimeFormat") final DateTimeFormatSettings dateTimeFormat) {
        this.theme = theme;
        this.font = font;
        this.fontSize = fontSize;
        this.dateTimeFormat = dateTimeFormat;
    }

    public String getTheme() {
        return theme;
    }

    public String getFont() {
        return font;
    }

    public String getFontSize() {
        return fontSize;
    }

    public DateTimeFormatSettings getDateTimeFormat() {
        return dateTimeFormat;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserPreferences)) {
            return false;
        }
        final UserPreferences that = (UserPreferences) o;
        return Objects.equals(theme, that.theme) && Objects.equals(font,
                that.font) && Objects.equals(fontSize, that.fontSize) && Objects.equals(dateTimeFormat,
                that.dateTimeFormat);
    }

    @Override
    public int hashCode() {
        return Objects.hash(theme, font, fontSize, dateTimeFormat);
    }

    @Override
    public String toString() {
        return "UserPreferences{" +
                "theme='" + theme + '\'' +
                ", font='" + font + '\'' +
                ", fontSize='" + fontSize + '\'' +
                ", dateTimeFormat=" + dateTimeFormat +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {

        private String theme;
        private String font;
        private String fontSize;
        private DateTimeFormatSettings dateTimeFormat;

        private Builder() {
            theme = "Dark";
            font = "Roboto";
            fontSize = "Medium";
            dateTimeFormat = DateTimeFormatSettings.builder()
                    .pattern("yyyy-MM-dd'T'HH:mm:ss.SSSXX")
                    .timeZone(
                            TimeZone.builder().use(Use.UTC).build())
                    .build();
        }

        private Builder(final UserPreferences userPreferences) {
            this.theme = userPreferences.theme;
            this.font = userPreferences.font;
            this.fontSize = userPreferences.fontSize;
            this.dateTimeFormat = userPreferences.dateTimeFormat;
        }

        public Builder theme(final String theme) {
            this.theme = theme;
            return this;
        }

        public Builder font(final String font) {
            this.font = font;
            return this;
        }

        public Builder fontSize(final String fontSize) {
            this.fontSize = fontSize;
            return this;
        }

        public Builder dateTimeFormat(final DateTimeFormatSettings dateTimeFormat) {
            this.dateTimeFormat = dateTimeFormat;
            return this;
        }

        public UserPreferences build() {
            return new UserPreferences(theme, font, fontSize, dateTimeFormat);
        }
    }
}
