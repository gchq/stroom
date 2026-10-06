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

package stroom.gwt.workbench.client.widgets;

import stroom.gwt.workbench.framework.client.args.Args;
import stroom.svg.shared.SvgImage;

/// Converts story args (which can be set to anything in the URL or the Controls addon) to the
/// values that Stroom's GWT widgets take, shared by the widget stories.
///
/// Pure Java, so it is unit tested on the JVM.
public final class StoryArgs {

    private StoryArgs() {
        // Static utility
    }

    /// Converts an icon arg to an [SvgImage].
    ///
    /// @param name The name of an [SvgImage] constant, e.g. `ADD`. Args can be set in the URL so
    ///             this may be any value, or null.
    /// @return The image, or null, so no icon is shown, if there is no image with that name.
    public static SvgImage toSvgImage(final String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        for (final SvgImage image : SvgImage.values()) {
            if (image.name().equals(name)) {
                return image;
            }
        }
        return null;
    }

    /// Gets a boolean arg that defaults to true, e.g. React's `enabled = true` or `visible = true`
    /// props.
    ///
    /// @param args The story's args.
    /// @param name The name of the arg.
    /// @param defaultValue The value if the arg is undefined.
    /// @return The arg's value, or the default if it is undefined.
    public static boolean getBoolean(final Args args, final String name, final boolean defaultValue) {
        return args.has(name)
                ? args.getBoolean(name)
                : defaultValue;
    }

    /// Gets a whole number arg, e.g. for a `long` or `int` property of a GWT widget. Number args
    /// are held as doubles; a fraction is truncated towards zero.
    ///
    /// @param args The story's args.
    /// @param name The name of the arg.
    /// @return The arg's value, or null if it is undefined or not a number.
    public static Long getLong(final Args args, final String name) {
        final double value = args.getNumber(name, Double.NaN);
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return null;
        }
        return (long) value;
    }
}
