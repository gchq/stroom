/*
 * Copyright 2024 Crown Copyright
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

import stroom.ui.config.shared.UserPreferences.ToolbarDisplay;
import stroom.util.shared.NullSafe;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.stream.Collectors;

public class ThemeCssUtil {

    private static final Map<String, String> densityMap = new HashMap<>();
    private static final Map<String, String> fontMap = new HashMap<>();
    private static final Map<String, String> fontSizeMap = new HashMap<>();

    static {
        densityMap.put("Comfortable", "stroom-density-comfortable");
        densityMap.put("Compact", "stroom-density-compact");

        fontMap.put("Arial", "stroom-font-arial");
        fontMap.put("Open Sans", "stroom-font-open-sans");
        fontMap.put("Roboto", "stroom-font-roboto");
        fontMap.put("Tahoma", "stroom-font-tahoma");
        fontMap.put("Verdana", "stroom-font-verdana");

        fontSizeMap.put("Small", "stroom-font-size-small");
        fontSizeMap.put("Medium", "stroom-font-size-medium");
        fontSizeMap.put("Large", "stroom-font-size-large");
    }

    private ThemeCssUtil() {
        // Utility class.
    }

    /**
     * @return A space delimited list of css classes for theme, density, font and font size.
     */
    public static String getCurrentPreferenceClasses(final UserPreferences currentUserPreferences) {
        final StringJoiner classJoiner = new StringJoiner(" ")
                .add("stroom");

        if (currentUserPreferences != null) {
            NullSafe.consume(currentUserPreferences.getTheme(), theme ->
                    classJoiner.add(Theme.getClassName(theme)));

            if (Objects.requireNonNullElse(currentUserPreferences.getEnableTransparency(), true)) {
                classJoiner.add("transparency");
            }

            Optional.ofNullable(currentUserPreferences.getDensity())
                    .map(densityMap::get)
                    .ifPresent(classJoiner::add);

            Optional.ofNullable(currentUserPreferences.getFont())
                    .map(fontMap::get)
                    .ifPresent(classJoiner::add);

            Optional.ofNullable(currentUserPreferences.getFontSize())
                    .map(fontSizeMap::get)
                    .ifPresent(classJoiner::add);

            NullSafe.consume(getToolbarDisplayClass(currentUserPreferences.getToolbarDisplay()), classJoiner::add);
        }
        return classJoiner.toString();
    }

    /// The page class that says how toolbar buttons are shown (`toolbar-button.css`).
    ///
    /// @param toolbarDisplay How toolbar buttons are shown, or null for the default.
    /// @return `toolbar-display--icons-and-text` or `toolbar-display--text`, or null for icons
    ///         only, which is how toolbars look with no class.
    public static String getToolbarDisplayClass(final ToolbarDisplay toolbarDisplay) {
        if (toolbarDisplay == null) {
            return null;
        }
        return switch (toolbarDisplay) {
            case ICONS_AND_TEXT -> "toolbar-display--icons-and-text";
            case TEXT -> "toolbar-display--text";
            case ICONS -> null;
        };
    }

    public static List<String> getThemes() {
        return Theme.getThemeNames();
    }

    public static List<String> getFonts() {
        return fontMap.keySet()
                .stream()
                .sorted()
                .collect(Collectors.toList());
    }
}
