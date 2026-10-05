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

/// The preset viewport sizes, the same as React Storybook's default (minimal) viewports.
public enum ViewportPreset {
    SMALL_MOBILE("mobile1", "Small mobile", 320, 568, "wbm-icon-mobile"),
    LARGE_MOBILE("mobile2", "Large mobile", 414, 896, "wbm-icon-mobile"),
    TABLET("tablet", "Tablet", 834, 1112, "wbm-icon-tablet"),
    DESKTOP("desktop", "Desktop", 1280, 1024, "wbm-icon-browser");

    private final String id;
    private final String label;
    private final int width;
    private final int height;
    private final String icon;

    ViewportPreset(final String id,
                   final String label,
                   final int width,
                   final int height,
                   final String icon) {
        this.id = id;
        this.label = label;
        this.width = width;
        this.height = height;
        this.icon = icon;
    }

    /// @return The id Storybook uses for the viewport.
    public String getId() {
        return id;
    }

    /// @return The name shown in the menu.
    public String getLabel() {
        return label;
    }

    /// @return The width in pixels.
    public int getWidth() {
        return width;
    }

    /// @return The height in pixels.
    public int getHeight() {
        return height;
    }

    /// @return The id of the icon's SVG symbol.
    public String getIcon() {
        return icon;
    }

    /// Parses a viewport width or height typed by the user, e.g. `320` or `320px`.
    ///
    /// @param text The text, may be null.
    /// @return The size in pixels, or null if the text isn't a whole number of up to five digits.
    public static Integer parseSize(final String text) {
        if (text == null) {
            return null;
        }
        final String trimmed = text.trim();
        final String digits = (trimmed.endsWith("px")
                ? trimmed.substring(0, trimmed.length() - 2)
                : trimmed).trim();
        if (digits.isEmpty() || digits.length() > 5) {
            return null;
        }
        for (int i = 0; i < digits.length(); i++) {
            final char c = digits.charAt(i);
            if (c < '0' || c > '9') {
                return null;
            }
        }
        return Integer.parseInt(digits);
    }

    /// @param id The id of a viewport.
    /// @return The viewport with the id, or null if there isn't one.
    public static ViewportPreset fromId(final String id) {
        for (final ViewportPreset preset : values()) {
            if (preset.id.equals(id)) {
                return preset;
            }
        }
        return null;
    }
}
