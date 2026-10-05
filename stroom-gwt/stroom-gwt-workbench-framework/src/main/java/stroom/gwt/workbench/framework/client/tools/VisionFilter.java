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

/// The vision deficiencies the preview can simulate, as in React Storybook's accessibility addon.
/// The `url(...)` filters refer to the SVG filters in vision-filters.svg.
public enum VisionFilter {
    BLURRED("blurred", "Blurred vision", "blur(2px)", "22.9"),
    DEUTERANOMALY("deuteranomaly", "Deuteranomaly", url("deuteranomaly"), "2.7"),
    DEUTERANOPIA("deuteranopia", "Deuteranopia", url("deuteranopia"), "0.56"),
    PROTANOMALY("protanomaly", "Protanomaly", url("protanomaly"), "0.66"),
    PROTANOPIA("protanopia", "Protanopia", url("protanopia"), "0.59"),
    TRITANOMALY("tritanomaly", "Tritanomaly", url("tritanomaly"), "0.01"),
    TRITANOPIA("tritanopia", "Tritanopia", url("tritanopia"), "0.016"),
    ACHROMATOPSIA("achromatopsia", "Achromatopsia", url("achromatopsia"), "0.0001"),
    // The id and CSS function are Storybook's and CSS's names so keep their spelling
    GREYSCALE("grayscale", "Greyscale", "grayscale(100%)", null);

    private final String id;
    private final String label;
    private final String cssFilter;
    private final String percentage;

    VisionFilter(final String id, final String label, final String cssFilter, final String percentage) {
        this.id = id;
        this.label = label;
        this.cssFilter = cssFilter;
        this.percentage = percentage;
    }

    private static String url(final String name) {
        return "url(\"#workbench-a11y-vision-" + name + "\")";
    }

    /// @return The id Storybook uses for the filter.
    public String getId() {
        return id;
    }

    /// @return The name shown in the menu.
    public String getLabel() {
        return label;
    }

    /// @return The value of the CSS `filter` property that simulates it.
    public String getCssFilter() {
        return cssFilter;
    }

    /// @return The description shown in the menu, e.g. `2.7% of users`, or null.
    public String getDescription() {
        return percentage != null
                ? percentage + "% of users"
                : null;
    }

    /// @param id The id of a filter.
    /// @return The filter with the id, or null if there isn't one.
    public static VisionFilter fromId(final String id) {
        for (final VisionFilter filter : values()) {
            if (filter.id.equals(id)) {
                return filter;
            }
        }
        return null;
    }
}
