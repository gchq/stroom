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

package stroom.widget.tab.client;

import java.util.Arrays;

/// How wide tabs' labels may be so that the tabs fit their bar: the widest labels are cut first,
/// down to the next widest, then all of them alike, as a browser's tabs shrink.
///
/// For example, labels 400 and 200 wide in 500: the 400 is cut to 300 (300 + 200). In 300: the 400
/// is cut to 200, then both to 150 (150 + 150).
///
/// The narrowest a label may be cut to is the stylesheet's (`tab-bar.css`, in characters, so it
/// follows the text's size); past it the tabs that don't fit overflow, as the bar measures them
/// once they are cut.
final class TabLabelCap {

    /// No cap: the labels all fit as they are.
    static final double NONE = Double.POSITIVE_INFINITY;

    private TabLabelCap() {
        // Static utility
    }

    /// @param labelWidths How wide each tab's label is, uncut.
    /// @param available   The room the labels have: the bar's, less the rest of each tab (its icon,
    ///                    close cross and padding) and the gaps between them.
    /// @return The widest a label may be, or [#NONE] if they all fit; 0 if there is no room.
    static double cap(final double[] labelWidths, final double available) {
        final double total = Arrays.stream(labelWidths).sum();
        if (total <= available) {
            return NONE;
        }
        // The labels narrower than the cap keep their width; the rest share what is left alike
        final double[] sorted = Arrays.copyOf(labelWidths, labelWidths.length);
        Arrays.sort(sorted);
        double remaining = available;
        for (int i = 0; i < sorted.length; i++) {
            final double share = remaining / (sorted.length - i);
            if (sorted[i] > share) {
                return Math.max(share, 0);
            }
            remaining -= sorted[i];
        }
        // Not reached, as the labels don't all fit
        return Math.max(remaining, 0);
    }
}
