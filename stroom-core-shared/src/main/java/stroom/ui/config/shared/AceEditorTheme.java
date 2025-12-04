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

// Copyright (c) 2011 David H. Hovemeyer <david.hovemeyer@gmail.com>
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
// THE SOFTWARE.

package stroom.ui.config.shared;

import stroom.util.shared.NullSafe;

import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Enumeration for ACE editor themes.
 * Note that the corresponding .js file must be loaded
 * before a theme can be set.
 */
public enum AceEditorTheme {

    /*
    ------- IMPORTANT!!! -------
    Keep these in line with any changes to the ace editor .js files.
    See the theme-*.js files in
    stroom-core-client-widget/src/main/java/edu/ycp/cs/dh/acegwt/public/ace
    ------- IMPORTANT!!! -------
    */

    AMBIANCE("ambiance", ThemeType.DARK),
    CHAOS("chaos", ThemeType.DARK),
    CHROME("chrome", ThemeType.LIGHT),
    CLOUDS("clouds", ThemeType.LIGHT),
    CLOUDS_MIDNIGHT("clouds_midnight", ThemeType.DARK),
    COBALT("cobalt", ThemeType.DARK),
    CRIMSON_EDITOR("crimson_editor", ThemeType.LIGHT),
    DAWN("dawn", ThemeType.LIGHT),
    DRACULA("dracula", ThemeType.DARK),
    DREAMWEAVER("dreamweaver", ThemeType.LIGHT),
    ECLIPSE("eclipse", ThemeType.LIGHT),
    GITHUB("github", ThemeType.LIGHT),
    GOB("gob", ThemeType.DARK),
    GRUVBOX("gruvbox", ThemeType.DARK),
    IDLE_FINGERS("idle_fingers", ThemeType.DARK),
    IPLASTIC("iplastic", ThemeType.LIGHT),
    KATZENMILCH("katzenmilch", ThemeType.LIGHT),
    KR_THEME("kr_theme", ThemeType.DARK),
    KUROIR("kuroir", ThemeType.LIGHT),
    MERBIVORE("merbivore", ThemeType.DARK),
    MERBIVORE_SOFT("merbivore_soft", ThemeType.DARK),
    MONO_INDUSTRIAL("mono_industrial", ThemeType.DARK),
    MONOKAI("monokai", ThemeType.DARK),
    NORD_DARK("nord_dark", ThemeType.DARK),
    PASTEL_ON_DARK("pastel_on_dark", ThemeType.DARK),
    SOLARIZED_DARK("solarized_dark", ThemeType.DARK),
    SOLARIZED_LIGHT("solarized_light", ThemeType.LIGHT),
    SQLSERVER("sqlserver", ThemeType.LIGHT),
    TERMINAL("terminal", ThemeType.DARK),
    TEXTMATE("textmate", ThemeType.LIGHT),
    TOMORROW("tomorrow", ThemeType.LIGHT),
    TOMORROW_NIGHT("tomorrow_night", ThemeType.DARK),
    TOMORROW_NIGHT_BLUE("tomorrow_night_blue", ThemeType.DARK),
    TOMORROW_NIGHT_BRIGHT("tomorrow_night_bright", ThemeType.DARK),
    TOMORROW_NIGHT_EIGHTIES("tomorrow_night_eighties", ThemeType.DARK),
    TWILIGHT("twilight", ThemeType.DARK),
    VIBRANT_INK("vibrant_ink", ThemeType.DARK),
    XCODE("xcode", ThemeType.LIGHT);

    public static final AceEditorTheme DEFAULT_LIGHT_THEME = AceEditorTheme.CHROME;
    public static final AceEditorTheme DEFAULT_DARK_THEME = AceEditorTheme.TOMORROW_NIGHT;

    private static final EnumMap<ThemeType, Set<AceEditorTheme>> TYPE_TO_THEME_MAP = new EnumMap<>(
            ThemeType.class);
    private static final EnumMap<ThemeType, AceEditorTheme> TYPE_TO_DEFAULT_THEME_MAP = new EnumMap<>(
            ThemeType.class);

    private static final Map<String, AceEditorTheme> NAME_TO_THEME_MAP = Arrays.stream(AceEditorTheme.values())
            .collect(Collectors.toMap(AceEditorTheme::getName, Function.identity()));

    static {
        for (final ThemeType themeType : ThemeType.values()) {
            final boolean isFound = Arrays.stream(AceEditorTheme.values())
                    .anyMatch(theme -> theme.getThemeType().equals(themeType));
            if (!isFound) {
                throw new RuntimeException("No AceEditorTheme found with themeType: " + themeType);
            }
        }

        for (final ThemeType themeType : ThemeType.values()) {
            TYPE_TO_THEME_MAP.put(
                    themeType,
                    Arrays.stream(AceEditorTheme.values())
                            .filter(theme -> themeType.equals(theme.themeType))
                            .collect(Collectors.toSet()));
        }

        TYPE_TO_DEFAULT_THEME_MAP.put(ThemeType.DARK, DEFAULT_DARK_THEME);
        TYPE_TO_DEFAULT_THEME_MAP.put(ThemeType.LIGHT, DEFAULT_LIGHT_THEME);

        for (final ThemeType themeType : ThemeType.values()) {
            if (!TYPE_TO_DEFAULT_THEME_MAP.containsKey(themeType)) {
                throw new RuntimeException("No default theme for themeType " + themeType);
            }
        }
    }

    private final String name;
    private final ThemeType themeType;

    AceEditorTheme(final String name, final ThemeType themeType) {
        this.name = Objects.requireNonNull(name);
        this.themeType = Objects.requireNonNull(themeType);
    }

    /**
     * @return the theme name (e.g., "eclipse")
     */
    public String getName() {
        return name;
    }

    public ThemeType getThemeType() {
        return themeType;
    }

    public boolean isDark() {
        return themeType.isDark();
    }

    public boolean isLight() {
        return themeType.isLight();
    }

    public static AceEditorTheme fromName(final String name) {
        if (name == null) {
            return null;
        } else {
            return NAME_TO_THEME_MAP.get(name);
        }
    }

    public static boolean matchesThemeType(final String themeName, final ThemeType themeType) {
        Objects.requireNonNull(themeName);
        Objects.requireNonNull(themeType);
        return TYPE_TO_THEME_MAP.get(themeType)
                .contains(AceEditorTheme.fromName(themeName));
    }

    public static boolean isValidThemeName(final String themeName) {
        return themeName != null && NAME_TO_THEME_MAP.containsKey(themeName);
    }

    public static List<AceEditorTheme> getThemesByType(final ThemeType themeType) {
        return TYPE_TO_THEME_MAP.get(themeType)
                .stream()
                .sorted(Comparator.comparing(AceEditorTheme::getName))
                .collect(Collectors.toList());
    }

    public static AceEditorTheme getDefaultEditorTheme(final ThemeType themeType) {
        return TYPE_TO_DEFAULT_THEME_MAP.get(
                NullSafe.requireNonNullElse(themeType, Theme.DEFAULT_THEME_TYPE));
    }
}
