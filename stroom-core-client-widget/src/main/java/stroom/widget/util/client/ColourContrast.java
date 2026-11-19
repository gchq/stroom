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

import java.util.List;

/// The contrast of text on a background, as WCAG measures it (1.4.3, Contrast (Minimum)), from colours
/// as the browser gives them computed (`rgb(r, g, b)` or `rgba(r, g, b, a)`), e.g. to warn of a
/// colour choice that makes text hard to read.
public final class ColourContrast {

    /// The least contrast WCAG asks of normal text (level AA).
    public static final double MINIMUM = 4.5;

    private ColourContrast() {
        // Static utility
    }

    /// The contrast of text on a background that may be made of layers, some see-through.
    ///
    /// @param text        The text's computed colour.
    /// @param backgrounds The computed background colours from the text's element outwards, ending
    ///                    with one that is opaque (e.g. the element's, its parent's, ...).
    /// @return The contrast ratio, from 1 to 21, or null if a colour can't be read or no background is
    /// opaque.
    public static Double ratio(final String text, final List<String> backgrounds) {
        final double[] background = background(backgrounds);
        final double[] textColour = parse(text);
        if (background == null || textColour == null) {
            return null;
        }
        return ratio(over(textColour, background), background);
    }

    /// A contrast ratio as WCAG writes it, rounded down to one decimal place so that a colour short
    /// of a minimum never shows it, e.g. `4.4:1` for 4.48.
    ///
    /// @param ratio The ratio, from 1 to 21.
    /// @return The ratio written, e.g. `12.6:1`.
    public static String format(final double ratio) {
        final int tenths = (int) Math.floor(ratio * 10 + 1e-9);
        return (tenths / 10) + "." + (tenths % 10) + ":1";
    }

    /// The contrast ratio of two opaque colours.
    ///
    /// @param first  One colour, as {red, green, blue} from 0 to 255.
    /// @param second The other.
    /// @return The ratio, from 1 to 21.
    static double ratio(final double[] first, final double[] second) {
        final double a = luminance(first);
        final double b = luminance(second);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    /// WCAG's relative luminance of an opaque colour.
    ///
    /// @param rgb The colour, as {red, green, blue} from 0 to 255.
    /// @return The luminance, from 0 (black) to 1 (white).
    static double luminance(final double[] rgb) {
        return 0.2126 * linear(rgb[0]) + 0.7152 * linear(rgb[1]) + 0.0722 * linear(rgb[2]);
    }

    /// Reads a computed colour.
    ///
    /// @param colour The colour, e.g. `rgb(255, 0, 0)`, `rgba(0, 0, 0, 0.5)` or `rgb(0 0 0 / 50%)`.
    /// @return The colour as {red, green, blue, alpha}, red, green and blue from 0 to 255 and alpha
    /// from 0 to 1, or null if it can't be read.
    static double[] parse(final String colour) {
        if (colour == null) {
            return null;
        }
        final String trimmed = colour.trim().toLowerCase();
        if (trimmed.equals("transparent")) {
            return new double[]{0, 0, 0, 0};
        }
        final int open = trimmed.indexOf('(');
        if (!trimmed.endsWith(")") || open < 0) {
            return null;
        }
        final String function = trimmed.substring(0, open).trim();
        if (!function.equals("rgb") && !function.equals("rgba")) {
            return null;
        }
        final String[] parts = trimmed.substring(open + 1, trimmed.length() - 1)
                .replace('/', ' ')
                .replace(',', ' ')
                .trim()
                .split("\\s+");
        if (parts.length != 3 && parts.length != 4) {
            return null;
        }
        try {
            final double[] rgba = new double[]{
                    channel(parts[0]),
                    channel(parts[1]),
                    channel(parts[2]),
                    parts.length == 4
                            ? alpha(parts[3])
                            : 1};
            return rgba;
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    // The background the layers make, laid over one another from the last (opaque) in, or null
    private static double[] background(final List<String> backgrounds) {
        if (backgrounds == null || backgrounds.isEmpty()) {
            return null;
        }
        final double[] last = parse(backgrounds.get(backgrounds.size() - 1));
        if (last == null || last[3] < 1) {
            return null;
        }
        double[] result = last;
        for (int i = backgrounds.size() - 2; i >= 0; i--) {
            final double[] layer = parse(backgrounds.get(i));
            if (layer == null) {
                return null;
            }
            result = over(layer, result);
        }
        return result;
    }

    // A colour, maybe see-through, laid over an opaque one
    private static double[] over(final double[] top, final double[] below) {
        final double alpha = top[3];
        return new double[]{
                top[0] * alpha + below[0] * (1 - alpha),
                top[1] * alpha + below[1] * (1 - alpha),
                top[2] * alpha + below[2] * (1 - alpha),
                1};
    }

    // A channel, from 0 to 255, as a number or a percentage
    private static double channel(final String part) {
        final double value = part.endsWith("%")
                ? Double.parseDouble(part.substring(0, part.length() - 1)) / 100 * 255
                : Double.parseDouble(part);
        return Math.max(0, Math.min(255, value));
    }

    // An alpha, from 0 to 1, as a number or a percentage
    private static double alpha(final String part) {
        final double value = part.endsWith("%")
                ? Double.parseDouble(part.substring(0, part.length() - 1)) / 100
                : Double.parseDouble(part);
        return Math.max(0, Math.min(1, value));
    }

    // A channel's linear value, as WCAG's relative luminance has it
    private static double linear(final double channel) {
        final double c = channel / 255;
        return c <= 0.04045
                ? c / 12.92
                : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
