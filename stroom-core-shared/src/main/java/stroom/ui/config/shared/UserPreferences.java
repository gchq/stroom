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

import stroom.query.api.v2.TimeZone;
import stroom.query.api.v2.TimeZone.Use;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorTheme;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import javax.inject.Singleton;

@Singleton
@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class UserPreferences {

    @JsonProperty
    @JsonPropertyDescription("The theme to use, e.g. `light`, `dark`")
    private final String theme;

    @JsonProperty
    @JsonPropertyDescription("Theme to apply to the Ace text editor")
    private final AceEditorTheme editorTheme;

    @JsonProperty
    @JsonPropertyDescription("The font to use, e.g. `Roboto`")
    private final String font;

    @JsonProperty
    @JsonPropertyDescription("The font size to use, e.g. `small`, `medium`, `large`")
    private final String fontSize;

    @Schema(description = "A date time formatting pattern string conforming to the specification of " +
            "java.time.format.DateTimeFormatter")
    @JsonProperty
    private final String dateTimePattern;

    @JsonProperty
    private final TimeZone timeZone;

    @JsonCreator
    public UserPreferences(@JsonProperty("theme") final String theme,
                           @JsonProperty("editorTheme") final AceEditorTheme editorTheme,
                           @JsonProperty("font") final String font,
                           @JsonProperty("fontSize") final String fontSize,
                           @JsonProperty("dateTimePattern") final String dateTimePattern,
                           @JsonProperty("timeZone") final TimeZone timeZone) {
        this.theme = theme;
        this.editorTheme = editorTheme;
        this.font = font;
        this.fontSize = fontSize;
        this.dateTimePattern = dateTimePattern;
        this.timeZone = timeZone;
    }

    public String getTheme() {
        return theme;
    }

    public AceEditorTheme getEditorTheme() {
        return editorTheme;
    }

    public String getFont() {
        return font;
    }

    public String getFontSize() {
        return fontSize;
    }

    public String getDateTimePattern() {
        return dateTimePattern;
    }

    public TimeZone getTimeZone() {
        return timeZone;
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
        return Objects.equals(theme, that.theme) && Objects.equals(editorTheme, that.editorTheme) &&
                Objects.equals(font, that.font) && Objects.equals(fontSize, that.fontSize) &&
                Objects.equals(dateTimePattern, that.dateTimePattern) && Objects.equals(timeZone, that.timeZone);
    }

    @Override
    public int hashCode() {
        return Objects.hash(theme, editorTheme, font, fontSize, dateTimePattern, timeZone);
    }

    @Override
    public String toString() {
        return "UserPreferences{" +
                "theme='" + theme + '\'' +
                ", editorTheme='" + editorTheme + '\'' +
                ", font='" + font + '\'' +
                ", fontSize='" + fontSize + '\'' +
                ", dateTimePattern='" + dateTimePattern + '\'' +
                ", timeZone=" + timeZone +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {

        public static final AceEditorTheme DEFAULT_EDITOR_THEME = AceEditorTheme.CHROME;
        public static final AceEditorTheme DEFAULT_EDITOR_THEME_DARK = AceEditorTheme.TOMORROW_NIGHT;
        private static final String DEFAULT_DATE_TIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSSXX";

        private String theme;
        private AceEditorTheme editorTheme;
        private String font;
        private String fontSize;
        private String dateTimePattern;
        private TimeZone timeZone;

        private Builder() {
            theme = "Dark";
            editorTheme = DEFAULT_EDITOR_THEME_DARK;
            font = "Roboto";
            fontSize = "Medium";
            dateTimePattern = DEFAULT_DATE_TIME_PATTERN;
            timeZone = TimeZone.builder().use(Use.UTC).build();
        }

        private Builder(final UserPreferences userPreferences) {
            this.theme = userPreferences.theme;
            this.editorTheme = userPreferences.editorTheme;
            this.font = userPreferences.font;
            this.fontSize = userPreferences.fontSize;
            this.dateTimePattern = userPreferences.dateTimePattern;
            this.timeZone = userPreferences.timeZone;
        }

        public Builder theme(final String theme) {
            this.theme = theme;
            return this;
        }

        public Builder editorTheme(final String editorTheme) {
            // Find the first `AceEditorTheme` enum that matches the provided theme name
            final Optional<AceEditorTheme> editorThemeEnum = Arrays.stream(AceEditorTheme.values())
                    .filter(t -> t.getName().equals(editorTheme))
                    .findFirst();

            this.editorTheme = editorThemeEnum.orElse(null);
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

        public Builder dateTimePattern(final String dateTimePattern) {
            this.dateTimePattern = dateTimePattern;
            return this;
        }

        public Builder timeZone(final TimeZone timeZone) {
            this.timeZone = timeZone;
            return this;
        }

        public UserPreferences build() {
            return new UserPreferences(theme, editorTheme, font, fontSize, dateTimePattern, timeZone);
        }
    }
}
