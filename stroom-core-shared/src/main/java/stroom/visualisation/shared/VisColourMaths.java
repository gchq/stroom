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

package stroom.visualisation.shared;

/// Colour arithmetic for visualisations' colours, written to give exactly what the content pack's
/// `src/shared/colour.js` gives, so that a visualisation colours a value the same whether Stroom
/// chooses the colour or (on an older Stroom) it chooses one itself. Colours are `#rrggbb`.
public final class VisColourMaths {

    private VisColourMaths() {
        // Static utility
    }

    /// @param colour A colour, `#rrggbb`.
    /// @return Its relative luminance, as WCAG defines it, from 0 (black) to 1 (white).
    public static double luminance(final String colour) {
        final double r = linear(channel(colour, 0));
        final double g = linear(channel(colour, 1));
        final double b = linear(channel(colour, 2));
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    /// @param a A colour.
    /// @param b Another colour.
    /// @return Their contrast ratio, as WCAG defines it, from 1 to 21.
    public static double contrast(final String a, final String b) {
        final double la = luminance(a);
        final double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    /// Mixes a colour towards another.
    ///
    /// @param colour  The colour.
    /// @param towards The colour to mix in, e.g. white to lighten.
    /// @param amount  How much of `towards` to mix in, from 0 to 1.
    /// @return The mixed colour.
    public static String mix(final String colour, final String towards, final double amount) {
        final StringBuilder sb = new StringBuilder("#");
        for (int i = 0; i < 3; i++) {
            final int from = channel(colour, i);
            final int to = channel(towards, i);
            final long value = Math.round(from + (to - from) * amount);
            final String hex = Integer.toHexString((int) Math.max(0, Math.min(255, value)));
            if (hex.length() < 2) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }

    /// @param a A colour.
    /// @param b Another colour.
    /// @return How far apart they are in RGB, from 0 to about 441.
    public static double distance(final String a, final String b) {
        double sum = 0;
        for (int i = 0; i < 3; i++) {
            final double d = channel(a, i) - channel(b, i);
            sum += d * d;
        }
        return Math.sqrt(sum);
    }

    /// A 32-bit FNV-1a hash of a string's UTF-16 code units, which the content pack's JavaScript
    /// computes the same way. The multiplication by the FNV prime is done with shifts and adds, so
    /// that it is exact in JavaScript (and GWT) as well as Java.
    ///
    /// @param text The text.
    /// @return The hash, from 0 to 2^32 - 1.
    public static long hash(final String text) {
        int h = 0x811c9dc5;
        for (int i = 0; i < text.length(); i++) {
            h ^= text.charAt(i);
            h += (h << 1) + (h << 4) + (h << 7) + (h << 8) + (h << 24);
        }
        return h & 0xffffffffL;
    }

    private static int channel(final String colour, final int index) {
        final int start = 1 + index * 2;
        return Integer.parseInt(colour.substring(start, start + 2), 16);
    }

    private static double linear(final int channel) {
        final double c = channel / 255.0;
        return c <= 0.03928
                ? c / 12.92
                : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
