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

package stroom.ai.impl.vis;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/// A style setting of a generated chart, shown as a control in the visualisation's settings. Several constants
/// share an id where kinds want a different default, e.g. labels are off for a bar chart but on for a pie chart.
public enum StyleSetting {

    // No title: a dashboard shows the vis component's name above it, which is the chart's title.
    X_AXIS_NAME("xAxisName", "X Axis Name", Control.TEXT, "", List.of(), null, null,
            "the x axis's name; the column's name if blank"),
    Y_AXIS_NAME("yAxisName", "Y Axis Name", Control.TEXT, "", List.of(), null, null,
            "the y axis's name; the column's name if blank"),
    LEGEND("legend", "Legend", Control.SELECTION, "top", List.of("top", "bottom", "right", "none"), null, null,
            "where the legend goes, shown only when there are several series or groups"),
    SORT("sort", "Sort Bars", Control.SELECTION, "", List.of("", "Ascending", "Descending"), null, null,
            "sorts the categories by their total; blank keeps the table's order"),
    HORIZONTAL("horizontal", "Horizontal", Control.BOOLEAN, "false", List.of(), null, null,
            "draws the bars across instead of up"),
    STACK("stack", "Stack Series", Control.BOOLEAN, "false", List.of(), null, null,
            "stacks the series instead of drawing them side by side"),
    SMOOTH("smooth", "Smooth Lines", Control.BOOLEAN, "false", List.of(), null, null,
            "draws curves instead of straight segments"),
    AREA("area", "Fill Area", Control.BOOLEAN, "false", List.of(), null, null,
            "shades the area under each line"),
    DOUGHNUT("doughnut", "Doughnut", Control.BOOLEAN, "false", List.of(), null, null,
            "draws a ring instead of a full pie"),
    DIRECTED("directed", "Directed", Control.BOOLEAN, "false", List.of(), null, null,
            "draws an arrow from source to target"),
    NODE_COLOURS("nodeColours", "Node Colours", Control.SELECTION, "group", List.of("group", "node"), null, null,
            "group colours nodes by their sourceGroup and targetGroup columns (one colour if neither is mapped), "
            + "with a legend; node gives each node its own colour"),
    COLOUR_SCALE("colourScale", "Colour Scale", Control.SELECTION, "blue",
            List.of("blue", "green", "red", "purple", "orange", "heat"), null, null,
            "the colours from the smallest value to the largest; heat runs from yellow to red"),
    BINS("bins", "Bins", Control.NUMBER, "0", List.of(), 0, 200,
            "how many ranges the values are counted in; 0 chooses from how many rows there are"),
    ORIENT("orient", "Direction", Control.SELECTION, "horizontal", List.of("horizontal", "vertical"), null, null,
            "flows run left to right (horizontal) or top to bottom (vertical)"),
    SHOW_LABELS_OFF("showLabels", "Show Labels", Control.BOOLEAN, "false", List.of(), null, null,
            "shows each value on the chart"),
    SHOW_LABELS_ON("showLabels", "Show Labels", Control.BOOLEAN, "true", List.of(), null, null,
            "shows each slice's name"),
    SHOW_LABELS_AUTO("showLabels", "Show Labels", Control.SELECTION, "auto", List.of("auto", "true", "false"), null,
            null, "shows node names: auto shows them all for up to 60 nodes, else the most connected tenth"),
    MAX_SERIES("maxSeries", "Max Series", Control.NUMBER, "20", List.of(), 1, 500,
            "the most series drawn; those with the smallest totals are left out"),
    MAX_SLICES("maxSlices", "Max Slices", Control.NUMBER, "12", List.of(), 1, 100,
            "the most slices; the smallest are combined as Other"),
    MAX_NODES("maxNodes", "Max Nodes", Control.NUMBER, "300", List.of(), 2, 5000,
            "the most nodes drawn; the least connected are left out"),
    MAX_VALUES("maxValues", "Max Rows", Control.NUMBER, "5000", List.of(), 1, 100_000,
            "the most table rows the chart is given");

    private final String id;
    private final String label;
    private final Control control;
    private final String defaultValue;
    private final List<String> choices;
    private final Integer min;
    private final Integer max;
    private final String description;

    StyleSetting(final String id,
                 final String label,
                 final Control control,
                 final String defaultValue,
                 final List<String> choices,
                 final Integer min,
                 final Integer max,
                 final String description) {
        this.id = id;
        this.label = label;
        this.control = control;
        this.defaultValue = defaultValue;
        this.choices = choices;
        this.min = min;
        this.max = max;
        this.description = description;
    }

    /// @return The id of its control in the visualisation's settings.
    public String id() {
        return id;
    }

    /// @return Its label in the visualisation's settings.
    public String label() {
        return label;
    }

    /// @return The kind of control it is.
    public Control control() {
        return control;
    }

    /// @return Its value when the user hasn't set one.
    public String defaultValue() {
        return defaultValue;
    }

    /// @return The values a selection takes, else none.
    public List<String> choices() {
        return choices;
    }

    /// @return A number's least value, else null.
    public Integer min() {
        return min;
    }

    /// @return A number's greatest value, else null.
    public Integer max() {
        return max;
    }

    /// @return What it does, for the agent.
    public String description() {
        return description;
    }

    /// @return True for the settings that live on the Limits tab rather than the Style tab.
    public boolean isLimit() {
        return control == Control.NUMBER;
    }

    /// Checks a value for this setting and puts it in the form the visualisation takes.
    ///
    /// @param value The value.
    /// @return The value as the visualisation takes it, or empty if it isn't one this setting takes.
    public Optional<String> normalise(final String value) {
        if (value == null) {
            return Optional.empty();
        }
        final String trimmed = value.strip();
        return switch (control) {
            case TEXT -> trimmed.length() <= 200
                    ? Optional.of(trimmed)
                    : Optional.empty();
            case BOOLEAN -> switch (trimmed.toLowerCase(Locale.ROOT)) {
                case "true", "yes", "on" -> Optional.of("true");
                case "false", "no", "off" -> Optional.of("false");
                default -> Optional.empty();
            };
            case SELECTION -> choices.stream()
                    .filter(choice -> choice.equalsIgnoreCase(trimmed))
                    .findFirst();
            case NUMBER -> {
                try {
                    final int number = Integer.parseInt(trimmed);
                    yield number >= min && number <= max
                            ? Optional.of(Integer.toString(number))
                            : Optional.empty();
                } catch (final NumberFormatException e) {
                    yield Optional.empty();
                }
            }
        };
    }

    /// @return The values it takes, for a message saying a value wasn't one of them.
    public String describeValues() {
        return switch (control) {
            case TEXT -> "text of up to 200 characters";
            case BOOLEAN -> "true or false";
            case SELECTION -> "one of " + String.join(", ", choices.stream()
                    .map(choice -> choice.isEmpty()
                            ? "'' (blank)"
                            : choice)
                    .toList());
            case NUMBER -> "a whole number from " + min + " to " + max;
        };
    }

    /// The kinds of control a setting can be, as Stroom's visualisation settings name them.
    public enum Control {
        TEXT("text"),
        BOOLEAN("boolean"),
        SELECTION("selection"),
        NUMBER("number");

        private final String type;

        Control(final String type) {
            this.type = type;
        }

        /// @return The control's type in the visualisation's settings JSON.
        public String type() {
            return type;
        }
    }
}
