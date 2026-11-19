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

package stroom.dashboard.client.vis;

import stroom.util.client.JSONUtil;
import stroom.visualisation.shared.VisInput;
import stroom.visualisation.shared.VisInputType;

import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// What a visualisation's field control offers and says, given the input the visualisation declares
/// for it (its typed `inputs`, see [VisInput]): only the columns of a type it takes, and what is
/// wrong when the chosen column isn't one.
public final class VisInputChoices {

    private VisInputChoices() {
        // Static utility
    }

    /// The columns a field control offers: none (the empty string, to leave it unset) and the
    /// columns its input takes, in the order given. A column that is chosen but that the input
    /// doesn't take is offered too, so that opening the settings doesn't lose it.
    ///
    /// @param input    The input of the control, or null if the visualisation declares none.
    /// @param options  The table's columns.
    /// @param selected The column chosen, or null or empty for none.
    /// @return The names of the columns to offer.
    public static List<String> choices(final VisInput input,
                                       final List<VisFieldOption> options,
                                       final String selected) {
        final List<String> choices = new ArrayList<>();
        choices.add("");
        for (final VisFieldOption option : options) {
            if ((input == null || input.accepts(option.getType()) || option.getName().equals(selected))
                && !choices.contains(option.getName())) {
                choices.add(option.getName());
            }
        }
        return choices;
    }

    /// What is wrong with the column chosen for a field control.
    ///
    /// @param input    The input of the control, or null if the visualisation declares none.
    /// @param options  The table's columns.
    /// @param selected The column chosen, or null or empty for none.
    /// @return E.g. `Status is text, but X axis takes numbers or dates`, or null if nothing is.
    public static String problem(final VisInput input,
                                 final List<VisFieldOption> options,
                                 final String selected) {
        if (input == null || selected == null || selected.isEmpty()) {
            return null;
        }
        for (final VisFieldOption option : options) {
            if (option.getName().equals(selected) && !input.accepts(option.getType())) {
                return selected + " is " + option.getType().getDisplayValue().toLowerCase()
                       + ", but " + label(input) + " takes " + typeNames(input);
            }
        }
        return null;
    }

    /// The help a field control shows for its input: whether it must be chosen, what it is for and
    /// the types it takes.
    ///
    /// @param input The input of the control.
    /// @return E.g. `Required. The height of each bar. Takes numbers.`
    public static String helpText(final VisInput input) {
        final StringBuilder sb = new StringBuilder();
        if (input.isRequired()) {
            sb.append("Required. ");
        }
        if (input.getDescription() != null && !input.getDescription().trim().isEmpty()) {
            final String description = input.getDescription().trim();
            sb.append(description);
            if (!description.endsWith(".")) {
                sb.append(".");
            }
            sb.append(" ");
        }
        if (!input.getInputTypes().isEmpty()) {
            sb.append("Takes ").append(typeNames(input)).append(".");
        }
        return sb.toString().trim();
    }

    /// Reads the inputs of a visualisation's settings.
    ///
    /// @param settings The visualisation's settings, may be null.
    /// @return Its inputs by id, in the order declared; empty if it declares none.
    public static Map<String, VisInput> parseInputs(final JSONObject settings) {
        final Map<String, VisInput> inputs = new LinkedHashMap<>();
        final JSONArray array = settings == null
                ? null
                : JSONUtil.getArray(settings.get("inputs"));
        if (array != null) {
            for (int i = 0; i < array.size(); i++) {
                final JSONObject json = JSONUtil.getObject(array.get(i));
                final String id = json == null
                        ? null
                        : JSONUtil.getString(json.get("id"));
                if (id != null) {
                    final String[] types = JSONUtil.getStrings(json.get("types"));
                    inputs.put(id, new VisInput(id,
                            JSONUtil.getString(json.get("label")),
                            JSONUtil.getString(json.get("description")),
                            types == null
                                    ? null
                                    : Arrays.asList(types),
                            json.get("required") != null && json.get("required").isBoolean() != null
                                    ? json.get("required").isBoolean().booleanValue()
                                    : null,
                            json.get("multiple") != null && json.get("multiple").isBoolean() != null
                                    ? json.get("multiple").isBoolean().booleanValue()
                                    : null));
                }
            }
        }
        return inputs;
    }

    private static String label(final VisInput input) {
        return input.getLabel() == null || input.getLabel().isEmpty()
                ? input.getId()
                : input.getLabel();
    }

    // The types an input takes, for people to read, e.g. 'numbers or dates'
    private static String typeNames(final VisInput input) {
        final List<String> names = new ArrayList<>();
        for (final VisInputType type : input.getInputTypes()) {
            names.add(switch (type) {
                case TEXT -> "text";
                case NUMBER -> "numbers";
                case DATE_TIME -> "dates";
            });
        }
        return String.join(" or ", names);
    }
}
