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

/// The kinds of control the Controls addon can show for an arg, the same as React Storybook's.
public enum ControlType {
    /// A multi-line text box.
    TEXT("text", "string"),
    /// A false/true toggle.
    BOOLEAN("boolean", "boolean"),
    /// A number box.
    NUMBER("number", "number"),
    /// A slider.
    RANGE("range", "number"),
    /// Radio buttons, one per line.
    RADIO("radio", "union"),
    /// Radio buttons on one line.
    INLINE_RADIO("inline-radio", "union"),
    /// A drop down list.
    SELECT("select", "union"),
    /// Check boxes, one per line, allowing several values.
    CHECK("check", "union"),
    /// Check boxes on one line.
    INLINE_CHECK("inline-check", "union"),
    /// A colour picker.
    COLOR("color", "string"),
    /// A date and time picker.
    DATE("date", "Date"),
    /// A JSON value.
    OBJECT("object", "object"),
    /// A callback that logs to the Actions addon rather than having a control.
    ACTION("action", "function");

    private final String id;
    private final String typeName;

    ControlType(final String id, final String typeName) {
        this.id = id;
        this.typeName = typeName;
    }

    /// @return The name Storybook uses for the control.
    public String getId() {
        return id;
    }

    /// @return The type shown in the Description column, e.g. `string`.
    public String getTypeName() {
        return typeName;
    }

    /// @return True if the control lets the user pick from a list of options.
    public boolean hasOptions() {
        return this == RADIO || this == INLINE_RADIO || this == SELECT || this == CHECK || this == INLINE_CHECK;
    }

    /// @return True if the control allows more than one value to be picked.
    public boolean isMulti() {
        return this == CHECK || this == INLINE_CHECK;
    }
}
