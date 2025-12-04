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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

            if (NullSafe.requireNonNullElse(currentUserPreferences.getEnableTransparency(), true)) {
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
        }
        return classJoiner.toString();
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
