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

package edu.ycp.cs.dh.acegwt.client.ace;

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

    AMBIANCE("ambiance", AceThemeType.DARK),
    CHAOS("chaos", AceThemeType.DARK),
    CHROME("chrome", AceThemeType.LIGHT),
    CLOUDS("clouds", AceThemeType.LIGHT),
    CLOUDS_MIDNIGHT("clouds_midnight", AceThemeType.DARK),
    COBALT("cobalt", AceThemeType.DARK),
    CRIMSON_EDITOR("crimson_editor", AceThemeType.LIGHT),
    DAWN("dawn", AceThemeType.LIGHT),
    DRACULA("dracula", AceThemeType.DARK),
    DREAMWEAVER("dreamweaver", AceThemeType.LIGHT),
    ECLIPSE("eclipse", AceThemeType.LIGHT),
    GITHUB("github", AceThemeType.LIGHT),
    GOB("gob", AceThemeType.DARK),
    GRUVBOX("gruvbox", AceThemeType.DARK),
    IDLE_FINGERS("idle_fingers", AceThemeType.DARK),
    IPLASTIC("iplastic", AceThemeType.LIGHT),
    KATZENMILCH("katzenmilch", AceThemeType.LIGHT),
    KR_THEME("kr_theme", AceThemeType.DARK),
    KUROIR("kuroir", AceThemeType.LIGHT),
    MERBIVORE("merbivore", AceThemeType.DARK),
    MERBIVORE_SOFT("merbivore_soft", AceThemeType.DARK),
    MONO_INDUSTRIAL("mono_industrial", AceThemeType.DARK),
    MONOKAI("monokai", AceThemeType.DARK),
    NORD_DARK("nord_dark", AceThemeType.DARK),
    PASTEL_ON_DARK("pastel_on_dark", AceThemeType.DARK),
    SOLARIZED_DARK("solarized_dark", AceThemeType.DARK),
    SOLARIZED_LIGHT("solarized_light", AceThemeType.LIGHT),
    SQLSERVER("sqlserver", AceThemeType.LIGHT),
    TERMINAL("terminal", AceThemeType.DARK),
    TEXTMATE("textmate", AceThemeType.LIGHT),
    TOMORROW("tomorrow", AceThemeType.LIGHT),
    TOMORROW_NIGHT("tomorrow_night", AceThemeType.DARK),
    TOMORROW_NIGHT_BLUE("tomorrow_night_blue", AceThemeType.DARK),
    TOMORROW_NIGHT_BRIGHT("tomorrow_night_bright", AceThemeType.DARK),
    TOMORROW_NIGHT_EIGHTIES("tomorrow_night_eighties", AceThemeType.DARK),
    TWILIGHT("twilight", AceThemeType.DARK),
    VIBRANT_INK("vibrant_ink", AceThemeType.DARK),
    XCODE("xcode", AceThemeType.LIGHT);

    public static final AceEditorTheme DEFAULT_LIGHT_THEME = AceEditorTheme.CHROME;
    public static final AceEditorTheme DEFAULT_DARK_THEME = AceEditorTheme.TOMORROW_NIGHT;

    private final String name;
    private final AceThemeType aceThemeType;
    private static final EnumMap<AceThemeType, Set<AceEditorTheme>> TYPE_TO_THEME_MAP = new EnumMap<>(
            AceThemeType.class);
    private static Map<String, AceEditorTheme> NAME_TO_THEME_MAP = Arrays.stream(AceEditorTheme.values())
            .collect(Collectors.toMap(AceEditorTheme::getName, Function.identity()));

    static {
        for (final AceThemeType aceThemeType : AceThemeType.values()) {
            TYPE_TO_THEME_MAP.put(
                    aceThemeType,
                    Arrays.stream(AceEditorTheme.values())
                            .filter(theme -> aceThemeType.equals(theme.aceThemeType))
                            .collect(Collectors.toSet()));
        }
    }

    AceEditorTheme(String name, final AceThemeType aceThemeType) {
        this.name = Objects.requireNonNull(name);
        this.aceThemeType = Objects.requireNonNull(aceThemeType);
    }

    /**
     * @return the theme name (e.g., "eclipse")
     */
    public String getName() {
        return name;
    }

    public AceThemeType getThemeType() {
        return aceThemeType;
    }

    public boolean isDark() {
        return aceThemeType.isDark();
    }

    public boolean isLight() {
        return aceThemeType.isLight();
    }

    public static AceEditorTheme fromName(final String name) {
        if (name == null) {
            return null;
        } else {
            return NAME_TO_THEME_MAP.get(name);
        }
    }

    public static boolean matchesThemeType(final AceEditorTheme theme, AceThemeType themeType) {
        Objects.requireNonNull(theme);
        Objects.requireNonNull(themeType);
        return TYPE_TO_THEME_MAP.get(themeType).contains(theme);
    }

    public static boolean matchesThemeType(final String themeName, AceThemeType themeType) {
        Objects.requireNonNull(themeName);
        Objects.requireNonNull(themeType);
        return TYPE_TO_THEME_MAP.get(themeType)
                .contains(AceEditorTheme.fromName(themeName));
    }

    public static boolean isValidThemeName(final String themeName) {
        if (themeName == null) {
            return false;
        } else {
            return NAME_TO_THEME_MAP.containsKey(themeName);
        }
    }

    public static List<AceEditorTheme> getLightThemes() {
        return TYPE_TO_THEME_MAP.get(AceThemeType.LIGHT)
                .stream()
                .sorted(Comparator.comparing(AceEditorTheme::getName))
                .collect(Collectors.toList());
    }

    public static List<AceEditorTheme> getDarkThemes() {
        return TYPE_TO_THEME_MAP.get(AceThemeType.DARK)
                .stream()
                .sorted(Comparator.comparing(AceEditorTheme::getName))
                .collect(Collectors.toList());
    }


    // --------------------------------------------------------------------------------


    public enum AceThemeType {
        LIGHT,
        DARK;

        public boolean isDark() {
            return DARK.equals(this);
        }

        public boolean isLight() {
            return LIGHT.equals(this);
        }
    }
}
