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

package stroom.gwt.workbench.framework.client.args;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/// Describes one of a component's args for the Controls addon, the equivalent of an entry in
/// React Storybook's `argTypes`, e.g.
/// ```
/// ArgType.radio("variant", "default", "contained-primary").description("Visual variant.")
/// ```
public final class ArgType {

    private final String name;
    private final ControlType control;
    private final List<String> options;
    private String description;
    private String defaultSummary;
    private String typeName;
    private double min = 0;
    private double max = 100;
    private double step = 1;

    private ArgType(final String name, final ControlType control, final List<String> options) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("An arg type needs a name");
        }
        this.name = name;
        this.control = Objects.requireNonNull(control);
        this.options = Collections.unmodifiableList(options);
        if (control.hasOptions() && options.isEmpty()) {
            throw new IllegalArgumentException("Arg '" + name + "' needs options");
        }
    }

    /// @param name The name of the arg.
    /// @return A text arg.
    public static ArgType text(final String name) {
        return new ArgType(name, ControlType.TEXT, Collections.emptyList());
    }

    /// @param name The name of the arg.
    /// @return A boolean arg.
    public static ArgType bool(final String name) {
        return new ArgType(name, ControlType.BOOLEAN, Collections.emptyList());
    }

    /// @param name The name of the arg.
    /// @return A number arg.
    public static ArgType number(final String name) {
        return new ArgType(name, ControlType.NUMBER, Collections.emptyList());
    }

    /// @param name The name of the arg.
    /// @param min  The smallest value.
    /// @param max  The largest value.
    /// @param step The step between values.
    /// @return A number arg shown as a slider.
    public static ArgType range(final String name, final double min, final double max, final double step) {
        final ArgType argType = new ArgType(name, ControlType.RANGE, Collections.emptyList());
        argType.min = min;
        argType.max = max;
        argType.step = step;
        return argType;
    }

    /// @param name    The name of the arg.
    /// @param options The values to choose from.
    /// @return An arg shown as radio buttons.
    public static ArgType radio(final String name, final String... options) {
        return new ArgType(name, ControlType.RADIO, Arrays.asList(options));
    }

    /// @param name    The name of the arg.
    /// @param options The values to choose from.
    /// @return An arg shown as radio buttons on one line.
    public static ArgType inlineRadio(final String name, final String... options) {
        return new ArgType(name, ControlType.INLINE_RADIO, Arrays.asList(options));
    }

    /// @param name    The name of the arg.
    /// @param options The values to choose from.
    /// @return An arg shown as a drop down list.
    public static ArgType select(final String name, final String... options) {
        return new ArgType(name, ControlType.SELECT, Arrays.asList(options));
    }

    /// @param name    The name of the arg.
    /// @param options The values to choose from.
    /// @return An arg shown as check boxes, whose value is a list.
    public static ArgType check(final String name, final String... options) {
        return new ArgType(name, ControlType.CHECK, Arrays.asList(options));
    }

    /// @param name    The name of the arg.
    /// @param options The values to choose from.
    /// @return An arg shown as check boxes on one line, whose value is a list.
    public static ArgType inlineCheck(final String name, final String... options) {
        return new ArgType(name, ControlType.INLINE_CHECK, Arrays.asList(options));
    }

    /// @param name The name of the arg.
    /// @return A colour arg, e.g. `#ff4785`.
    public static ArgType color(final String name) {
        return new ArgType(name, ControlType.COLOR, Collections.emptyList());
    }

    /// @param name The name of the arg.
    /// @return A date arg, whose value is the date's ISO 8601 string.
    public static ArgType date(final String name) {
        return new ArgType(name, ControlType.DATE, Collections.emptyList());
    }

    /// @param name The name of the arg.
    /// @return An arg whose value is JSON text.
    public static ArgType object(final String name) {
        return new ArgType(name, ControlType.OBJECT, Collections.emptyList());
    }

    /// @param name The name of the callback, e.g. `onClick`.
    /// @return An arg that logs to the Actions addon when the story calls it.
    public static ArgType action(final String name) {
        return new ArgType(name, ControlType.ACTION, Collections.emptyList());
    }

    /// @param description The description shown in the Controls addon.
    /// @return This arg type.
    public ArgType description(final String description) {
        this.description = description;
        return this;
    }

    /// @param defaultSummary The default value shown in the Default column, e.g. `'Close'`.
    /// @return This arg type.
    public ArgType defaultSummary(final String defaultSummary) {
        this.defaultSummary = defaultSummary;
        return this;
    }

    /// @param typeName The type shown in the Description column, if not the control's default.
    /// @return This arg type.
    public ArgType typeName(final String typeName) {
        this.typeName = typeName;
        return this;
    }

    /// @return The name of the arg.
    public String getName() {
        return name;
    }

    /// @return The control used to change the arg.
    public ControlType getControl() {
        return control;
    }

    /// @return The values to choose from, for controls with options.
    public List<String> getOptions() {
        return options;
    }

    /// @return The description, or null.
    public String getDescription() {
        return description;
    }

    /// @return The default value to show, or null.
    public String getDefaultSummary() {
        return defaultSummary;
    }

    /// @return The type shown in the Description column, e.g. `string`.
    public String getTypeName() {
        return typeName != null
                ? typeName
                : control.getTypeName();
    }

    /// @return The smallest value of a range.
    public double getMin() {
        return min;
    }

    /// @return The largest value of a range.
    public double getMax() {
        return max;
    }

    /// @return The step of a range.
    public double getStep() {
        return step;
    }

    /// Converts a value from a URL or a control into this arg's type.
    ///
    /// @param raw The value, e.g. `"5"`.
    /// @return The typed value, e.g. `5.0`, or null if it can't be converted.
    public Object convert(final Object raw) {
        if (raw == null) {
            return null;
        }
        switch (control) {
            case BOOLEAN:
                if (raw instanceof Boolean) {
                    return raw;
                }
                return "true".equals(String.valueOf(raw));
            case NUMBER:
            case RANGE:
                if (raw instanceof Double) {
                    return raw;
                }
                try {
                    return Double.parseDouble(String.valueOf(raw));
                } catch (final NumberFormatException e) {
                    return null;
                }
            case CHECK:
            case INLINE_CHECK:
                if (raw instanceof List) {
                    return raw;
                }
                return Collections.singletonList(String.valueOf(raw));
            case ACTION:
                return null;
            default:
                return String.valueOf(raw);
        }
    }
}
