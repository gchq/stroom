/*
 * Copyright 2026 Crown Copyright
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

package stroom.gwt.workbench.framework.client.tools;

/// The zoom levels of the preview, as percentages, with the same steps as React Storybook's
/// zoom tool.
public final class ZoomLevels {

    /// The default zoom level.
    public static final int DEFAULT = 100;
    /// The smallest zoom level.
    public static final int MIN = 25;
    /// The largest zoom level.
    public static final int MAX = 800;

    private static final int[] LEVELS = {25, 50, 75, 90, 100, 110, 125, 150, 200, 300, 400, 800};

    private ZoomLevels() {
        // Static utility
    }

    /// @param current The current zoom percentage, which needn't be one of the levels.
    /// @return The next larger level, or [#MAX] if already at or above it.
    public static int zoomIn(final int current) {
        for (final int level : LEVELS) {
            if (level > current) {
                return level;
            }
        }
        return MAX;
    }

    /// @param current The current zoom percentage, which needn't be one of the levels.
    /// @return The next smaller level, or [#MIN] if already at or below it.
    public static int zoomOut(final int current) {
        for (int i = LEVELS.length - 1; i >= 0; i--) {
            if (LEVELS[i] < current) {
                return LEVELS[i];
            }
        }
        return MIN;
    }

    /// @param percentage A zoom percentage typed by the user.
    /// @return The percentage limited to between [#MIN] and [#MAX].
    public static int clamp(final int percentage) {
        return Math.max(MIN, Math.min(MAX, percentage));
    }

    /// Parses a zoom percentage typed by the user, e.g. `150` or `150%`.
    ///
    /// @param text The text.
    /// @return The percentage limited to between [#MIN] and [#MAX], or null if the text isn't a
    /// number.
    public static Integer parse(final String text) {
        if (text == null) {
            return null;
        }
        final String trimmed = text.trim().replace("%", "").trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            // Only ASCII digits, as Character.isDigit() also accepts e.g. Arabic-Indic digits
            final char c = trimmed.charAt(i);
            if (c < '0' || c > '9') {
                return null;
            }
        }
        if (trimmed.length() > 6) {
            return MAX;
        }
        return clamp(Integer.parseInt(trimmed));
    }
}
