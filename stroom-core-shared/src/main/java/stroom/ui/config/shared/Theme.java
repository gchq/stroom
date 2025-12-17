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

package stroom.ui.config.shared;

import stroom.util.shared.NullSafe;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Stroom UI themes
 */
public enum Theme {

    LIGHT("Light", "stroom-theme-light", ThemeType.LIGHT),
    DARK("Dark", "stroom-theme-dark", ThemeType.DARK),
    ;

    static final Theme DEFAULT_THEME = DARK;
    static final ThemeType DEFAULT_THEME_TYPE;

    private static final Map<String, Theme> THEME_NAME_TO_THEME_MAP;

    static {
        THEME_NAME_TO_THEME_MAP = Arrays.stream(Theme.values())
                .collect(Collectors.toMap(
                        Theme::getThemeName,
                        Function.identity()));

        DEFAULT_THEME_TYPE = THEME_NAME_TO_THEME_MAP.get(DEFAULT_THEME.getThemeName()).getThemeType();

        for (final ThemeType themeType : ThemeType.values()) {
            final boolean isFound = Arrays.stream(Theme.values())
                    .anyMatch(theme -> theme.getThemeType().equals(themeType));
            if (!isFound) {
                throw new RuntimeException("No Theme found with themeType: " + themeType);
            }
        }
    }

    private final String themeName;
    private final String cssClass;
    private final ThemeType themeType;

    Theme(final String themeName, final String cssClass, final ThemeType themeType) {
        this.themeName = themeName;
        this.cssClass = cssClass;
        this.themeType = themeType;
    }

    public String getThemeName() {
        return themeName;
    }

    public ThemeType getThemeType() {
        return themeType;
    }

    public String getCssClass() {
        return cssClass;
    }

    public static Theme fromName(final String themeName) {
        final Theme theme = THEME_NAME_TO_THEME_MAP.get(themeName);
        return NullSafe.requireNonNullElse(theme, DEFAULT_THEME);
    }

    public static List<String> getThemeNames() {
        return THEME_NAME_TO_THEME_MAP.keySet()
                .stream()
                .sorted()
                .collect(Collectors.toList());
    }

    public static ThemeType getThemeType(final String themeName) {
        final ThemeType themeType = NullSafe.get(
                themeName,
                THEME_NAME_TO_THEME_MAP::get,
                Theme::getThemeType);
        Objects.requireNonNull(themeType);
        return themeType;
    }

    public static String getClassName(final String theme) {
        return NullSafe.getOrElse(
                theme,
                THEME_NAME_TO_THEME_MAP::get,
                Theme::getCssClass,
                "");
    }

    public static boolean isValidTheme(final String themeName) {
        return themeName != null && THEME_NAME_TO_THEME_MAP.containsKey(themeName);
    }
}
