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

import stroom.docref.HasDisplayValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/// The palettes that visualisations colour categories (e.g. series) from, the user choosing one in
/// their preferences. Each has a version for each theme, every colour of which has at least the 3:1
/// contrast against the theme's background that graphics need (WCAG 1.4.11). The same palettes
/// are in the content pack's `src/shared/colour.js`, for a visualisation shown by an older Stroom.
public enum VisPalette implements HasDisplayValue {

    /// Bright, saturated colours.
    VIVID("Vivid",
            Arrays.asList("#1f6fd6", "#c25400", "#0b8460", "#d0305f", "#7a52e0",
                    "#8f6a00", "#0a7fa3", "#b0369a", "#4a7f12", "#d23b3b"),
            Arrays.asList("#3d8bfd", "#ff8c42", "#20c997", "#f0507a", "#9b7bff",
                    "#e9b434", "#38c6f4", "#e86bd1", "#8bd450", "#ff6b6b")),
    /// Colours that stay distinct for people with the common kinds of colour blindness (protanopia,
    /// deuteranopia and tritanopia): Paul Tol's on the light theme and Okabe and Ito's on the dark.
    COLOUR_BLIND_SAFE("Colour-blind safe",
            Arrays.asList("#0077bb", "#cc3311", "#117733", "#882255", "#332288", "#aa4499", "#888888"),
            Arrays.asList("#56b4e9", "#e69f00", "#009e73", "#cc79a7", "#f0e442", "#d55e00", "#bbbbbb",
                    "#7f8ef0"));

    /// The background of a dashboard in the light theme.
    public static final String LIGHT_BACKGROUND = "#ffffff";
    /// The background of a dashboard in the dark theme.
    public static final String DARK_BACKGROUND = "#1a1d22";

    // How far a shade is mixed towards white (positive) or black (negative), in the order shades
    // are added once the palette's own colours have all been used
    private static final double[] SHADE_STEPS = {0.3, -0.3, 0.5, -0.5};
    // How far apart (in RGB) a shade must be from every colour before it
    private static final double MIN_SHADE_DISTANCE = 32;
    private static final double MIN_CONTRAST = 3;

    private final String displayValue;
    private final List<String> light;
    private final List<String> dark;
    private List<String> lightWithShades;
    private List<String> darkWithShades;

    VisPalette(final String displayValue, final List<String> light, final List<String> dark) {
        this.displayValue = displayValue;
        this.light = Collections.unmodifiableList(light);
        this.dark = Collections.unmodifiableList(dark);
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }

    /// @param darkTheme Whether the dashboard has the dark theme.
    /// @return The palette's own colours for the theme.
    public List<String> getBaseColours(final boolean darkTheme) {
        return darkTheme
                ? dark
                : light;
    }

    /// The palette's colours for a theme, followed by lighter and darker shades of them: each shade
    /// that still has 3:1 contrast against the background and is distinct from every colour before
    /// it. These are given out once the palette's own colours have all been used, before any
    /// colour is given twice.
    ///
    /// @param darkTheme Whether the dashboard has the dark theme.
    /// @return The colours, the palette's own first.
    public List<String> getColours(final boolean darkTheme) {
        if (darkTheme) {
            if (darkWithShades == null) {
                darkWithShades = withShades(dark, DARK_BACKGROUND);
            }
            return darkWithShades;
        }
        if (lightWithShades == null) {
            lightWithShades = withShades(light, LIGHT_BACKGROUND);
        }
        return lightWithShades;
    }

    /// @param displayValue A palette's name, as the user preferences hold it, e.g. `Vivid`.
    /// @return The palette, or [#VIVID] (the default) if the name isn't one.
    public static VisPalette fromDisplayValue(final String displayValue) {
        for (final VisPalette palette : values()) {
            if (palette.displayValue.equals(displayValue)) {
                return palette;
            }
        }
        return VIVID;
    }

    static List<String> withShades(final List<String> base, final String background) {
        final List<String> colours = new ArrayList<>(base);
        for (final double step : SHADE_STEPS) {
            for (final String colour : base) {
                final String shade = step > 0
                        ? VisColourMaths.mix(colour, "#ffffff", step)
                        : VisColourMaths.mix(colour, "#000000", -step);
                if (VisColourMaths.contrast(shade, background) >= MIN_CONTRAST && isDistinct(shade, colours)) {
                    colours.add(shade);
                }
            }
        }
        return Collections.unmodifiableList(colours);
    }

    private static boolean isDistinct(final String colour, final List<String> others) {
        for (final String other : others) {
            if (VisColourMaths.distance(colour, other) < MIN_SHADE_DISTANCE) {
                return false;
            }
        }
        return true;
    }
}
