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

package stroom.widget.datagrid.client.model;

/// Column widths and the text size preference. A width a screen gives a column, or a user saves
/// (e.g. in a dashboard table's settings), is a width at the medium text size; the grid draws it
/// multiplied by the preference's scale (`--text-scale`, `font.css`), so the column holds the same
/// text at any size, and gives a width the user drags back divided by it.
public final class TextScale {

    /// The scale of the medium text size, and of a page without the preference.
    public static final double MEDIUM = 1;

    private TextScale() {
        // Static methods only
    }

    /// @param width A width at the medium text size, in pixels.
    /// @param scale The text size's scale.
    /// @return The width drawn at that text size, in whole pixels.
    public static int toDrawn(final int width, final double scale) {
        return (int) Math.round(width * valid(scale));
    }

    /// @param width A width drawn at a text size, in pixels.
    /// @param scale The text size's scale.
    /// @return The width at the medium text size, in whole pixels.
    public static int toMedium(final int width, final double scale) {
        return (int) Math.round(width / valid(scale));
    }

    /// @param value The value of `--text-scale` (e.g. ` 1.26`), or `null` or blank if it isn't set.
    /// @return The scale it gives, or [#MEDIUM] if it isn't a positive number.
    public static double parse(final String value) {
        if (value == null || value.isBlank()) {
            return MEDIUM;
        }
        try {
            return valid(Double.parseDouble(value.trim()));
        } catch (final NumberFormatException e) {
            return MEDIUM;
        }
    }

    private static double valid(final double scale) {
        return scale > 0 && Double.isFinite(scale)
                ? scale
                : MEDIUM;
    }
}
