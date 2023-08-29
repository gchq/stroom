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
import stroom.ui.config.shared.Themes.ThemeType;
import stroom.util.shared.GwtNullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;
import javax.inject.Singleton;

@Singleton
@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class UserPreferences {

    public static final String DEFAULT_THEME = Themes.THEME_NAME_DARK;

    public static final EditorKeyBindings DEFAULT_EDITOR_KEY_BINDINGS = EditorKeyBindings.STANDARD;
    public static final Toggle DEFAULT_EDITOR_LIVE_AUTO_COMPLETION = Toggle.OFF;
    public static final String DEFAULT_EDITOR_THEME_LIGHT = "chrome";
    public static final String DEFAULT_EDITOR_THEME_DARK = "tomorrow_night";
    public static final String DEFAULT_DATE_TIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSSXX";

    @JsonProperty
    @JsonPropertyDescription("The theme to use, e.g. `light`, `dark`")
    private final String theme;

    @JsonProperty
    @JsonPropertyDescription("Theme to apply to the Ace text editor")
    private final String editorTheme;

    @JsonProperty
    @JsonPropertyDescription("The key bindings to use in the Ace text editor")
    private final EditorKeyBindings editorKeyBindings;

    @JsonProperty
    @JsonPropertyDescription("Enabled state of live editor auto completion")
    private final Toggle editorLiveAutoCompletion;

    @JsonProperty
    @JsonPropertyDescription("Layout density to use")
    private final String density;

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

    @JsonProperty
    private final Boolean enableTransparency;

    @JsonCreator
    public UserPreferences(@JsonProperty("theme") final String theme,
                           @JsonProperty("editorTheme") final String editorTheme,
                           @JsonProperty("editorKeyBindings") final EditorKeyBindings editorKeyBindings,
                           @JsonProperty("editorLiveAutoCompletion") final Toggle editorLiveAutoCompletion,
                           @JsonProperty("density") final String density,
                           @JsonProperty("font") final String font,
                           @JsonProperty("fontSize") final String fontSize,
                           @JsonProperty("dateTimePattern") final String dateTimePattern,
                           @JsonProperty("timeZone") final TimeZone timeZone,
                           @JsonProperty("enableTransparency") final Boolean enableTransparency) {
        this.theme = theme;
        this.editorTheme = editorTheme;
        this.editorKeyBindings = GwtNullSafe.requireNonNullElse(
                editorKeyBindings, DEFAULT_EDITOR_KEY_BINDINGS);
        this.editorLiveAutoCompletion = GwtNullSafe.requireNonNullElse(
                editorLiveAutoCompletion, DEFAULT_EDITOR_LIVE_AUTO_COMPLETION);
        this.density = density;
        this.font = font;
        this.fontSize = fontSize;
        this.dateTimePattern = dateTimePattern;
        this.timeZone = timeZone;
        this.enableTransparency = GwtNullSafe.requireNonNullElse(enableTransparency, true);
    }

    public String getTheme() {
        return theme;
    }

    public String getEditorTheme() {
        return editorTheme;
    }

    public EditorKeyBindings getEditorKeyBindings() {
        return editorKeyBindings;
    }

    public Toggle getEditorLiveAutoCompletion() {
        return editorLiveAutoCompletion;
    }

    public String getDensity() {
        return density;
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

    public Boolean getEnableTransparency() {
        return enableTransparency;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final UserPreferences that = (UserPreferences) o;
        return Objects.equals(theme, that.theme)
                && Objects.equals(editorTheme, that.editorTheme)
                && Objects.equals(editorKeyBindings, that.editorKeyBindings)
                && Objects.equals(editorLiveAutoCompletion, that.editorLiveAutoCompletion)
                && Objects.equals(density, that.density)
                && Objects.equals(font, that.font)
                && Objects.equals(fontSize, that.fontSize)
                && Objects.equals(dateTimePattern, that.dateTimePattern)
                && Objects.equals(timeZone, that.timeZone)
                && Objects.equals(enableTransparency, that.enableTransparency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(theme,
                editorTheme,
                editorKeyBindings,
                editorLiveAutoCompletion,
                density,
                font,
                fontSize,
                dateTimePattern,
                timeZone,
                enableTransparency);
    }

    @Override
    public String toString() {
        return "UserPreferences{" +
                "theme='" + theme + '\'' +
                ", editorTheme='" + editorTheme + '\'' +
                ", editorKeyBindings='" + editorKeyBindings + '\'' +
                ", editorLiveAutoCompletion='" + editorLiveAutoCompletion + '\'' +
                ", density='" + density + '\'' +
                ", font='" + font + '\'' +
                ", fontSize='" + fontSize + '\'' +
                ", dateTimePattern='" + dateTimePattern + '\'' +
                ", timeZone=" + timeZone +
                ", enableTransparency=" + enableTransparency +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static String getDefaultEditorTheme(final String themeName) {
        final ThemeType themeType = Themes.getThemeType(themeName);
        switch (themeType) {
            case DARK:
                return DEFAULT_EDITOR_THEME_DARK;
            case LIGHT:
                return DEFAULT_EDITOR_THEME_LIGHT;
            default:
                throw new RuntimeException("Unknown theme name '" + themeName + "'");
        }
    }


    //--------------------------------------------------------------------------------


    public enum EditorKeyBindings {
        STANDARD("Standard"),
        VIM("Vim");

        private final String displayValue;

        EditorKeyBindings(final String displayValue) {
            this.displayValue = displayValue;
        }

        public String getDisplayValue() {
            return displayValue;
        }

        public static EditorKeyBindings fromDisplayValue(final String displayValue) {
            if (VIM.displayValue.equalsIgnoreCase(displayValue)) {
                return VIM;
            } else {
                return STANDARD;
            }
        }
    }


    //--------------------------------------------------------------------------------


    public static final class Builder {

        private String theme;
        private String editorTheme;
        private EditorKeyBindings editorKeyBindings;
        private Toggle editorLiveAutoCompletion;
        private String density;
        private String font;
        private String fontSize;
        private String dateTimePattern;
        private TimeZone timeZone;
        private Boolean enableTransparency;

        private Builder() {
            theme = DEFAULT_THEME;
            editorTheme = getDefaultEditorTheme(DEFAULT_THEME);
            editorKeyBindings = DEFAULT_EDITOR_KEY_BINDINGS;
            editorLiveAutoCompletion = DEFAULT_EDITOR_LIVE_AUTO_COMPLETION;
            density = "Compact";
            font = "Roboto";
            fontSize = "Medium";
            dateTimePattern = DEFAULT_DATE_TIME_PATTERN;
            timeZone = TimeZone.builder().use(Use.UTC).build();
            enableTransparency = true;
        }

        private Builder(final UserPreferences userPreferences) {
            this.theme = userPreferences.theme;
            this.editorTheme = userPreferences.editorTheme;
            this.editorKeyBindings = userPreferences.editorKeyBindings;
            this.editorLiveAutoCompletion = userPreferences.editorLiveAutoCompletion;
            this.density = userPreferences.density;
            this.font = userPreferences.font;
            this.fontSize = userPreferences.fontSize;
            this.dateTimePattern = userPreferences.dateTimePattern;
            this.timeZone = userPreferences.timeZone;
            this.enableTransparency = userPreferences.enableTransparency;
        }

        public Builder theme(final String theme) {
            this.theme = theme;
            return this;
        }

        public Builder editorTheme(final String editorTheme) {
            this.editorTheme = editorTheme;
            return this;
        }

        public Builder editorKeyBindings(final EditorKeyBindings editorKeyBindings) {
            this.editorKeyBindings = editorKeyBindings;
            return this;
        }

        public Builder editorLiveAutoCompletion(final Toggle editorLiveAutoCompletion) {
            this.editorLiveAutoCompletion = editorLiveAutoCompletion;
            return this;
        }

        public Builder density(final String density) {
            this.density = density;
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

        public Builder enableTransparency(final Boolean enableTransparency) {
            this.enableTransparency = enableTransparency;
            return this;
        }

        public UserPreferences build() {
            return new UserPreferences(
                    theme,
                    editorTheme,
                    editorKeyBindings,
                    editorLiveAutoCompletion,
                    density,
                    font,
                    fontSize,
                    dateTimePattern,
                    timeZone,
                    enableTransparency);
        }
    }


    // --------------------------------------------------------------------------------


    public enum Toggle {
        ON("On", true),
        OFF("Off", false);

        private final String displayValue;
        private final boolean isOn;

        Toggle(final String displayValue, final boolean isOn) {
            this.displayValue = displayValue;
            this.isOn = isOn;
        }

        public static Toggle fromDisplayValue(final String displayValue) {
            Objects.requireNonNull(displayValue);
            return "on".equalsIgnoreCase(displayValue)
                    ? ON
                    : OFF;
        }

        public String getDisplayValue() {
            return displayValue;
        }

        public boolean isOn() {
            return isOn;
        }
    }
}
