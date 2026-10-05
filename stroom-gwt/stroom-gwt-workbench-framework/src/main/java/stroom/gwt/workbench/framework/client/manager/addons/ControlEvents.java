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

package stroom.gwt.workbench.framework.client.manager.addons;

/// Decides which DOM event the Controls addon handles for each kind of control, so that each
/// change is applied once. Browsers fire both `input` and `change` for selects, radios and
/// check boxes, while text fields and sliders fire `input` as the user types or drags.
public final class ControlEvents {

    private ControlEvents() {
        // Static utility
    }

    /// @param tagName   The tag name of the control's element, e.g. `INPUT` or `SELECT`.
    /// @param inputType The `type` of an `input` element, e.g. `radio`, or null for other
    ///                  elements.
    /// @return True if the control is handled on its `change` event, false if on its `input`
    /// event.
    public static boolean isHandledOnChange(final String tagName, final String inputType) {
        if ("SELECT".equalsIgnoreCase(tagName)) {
            return true;
        }
        if ("INPUT".equalsIgnoreCase(tagName)) {
            return "radio".equalsIgnoreCase(inputType) || "checkbox".equalsIgnoreCase(inputType);
        }
        return false;
    }
}
