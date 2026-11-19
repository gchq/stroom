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

package stroom.widget.util.client;

/// Changes to an icon's SVG markup (e.g. [stroom.svg.shared.SvgImage#getSvg()]) made before it is
/// written into a page as HTML.
public final class SvgMarkup {

    private static final String SVG_START = "<svg";
    private static final String CLASS_START = "class=\"";

    private SvgMarkup() {
        // Static utility
    }

    /// Makes an icon decoration: adds classes to its `<svg>` element (to those it has) and hides it
    /// from screen readers (`aria-hidden="true"`), e.g. for an icon in a button that is named by its
    /// title.
    ///
    /// @param svg        The icon's SVG markup.
    /// @param classNames The classes to add, e.g. `toolbar-button__icon`.
    /// @return The changed markup, or the markup unchanged if it has no `<svg>` element.
    public static String decorative(final String svg, final String classNames) {
        final int start = svg.indexOf(SVG_START);
        if (start == -1) {
            return svg;
        }
        final int end = svg.indexOf('>', start);
        final int classStart = svg.indexOf(CLASS_START, start);
        final String withClasses;
        if (classStart != -1 && classStart < end) {
            final int insertAt = classStart + CLASS_START.length();
            withClasses = svg.substring(0, insertAt) + classNames + " " + svg.substring(insertAt);
        } else {
            final int insertAt = start + SVG_START.length();
            withClasses = svg.substring(0, insertAt) + " class=\"" + classNames + "\"" + svg.substring(insertAt);
        }
        final int insertAt = start + SVG_START.length();
        return withClasses.substring(0, insertAt) + " aria-hidden=\"true\"" + withClasses.substring(insertAt);
    }
}
