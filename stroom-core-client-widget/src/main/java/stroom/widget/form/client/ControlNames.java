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

package stroom.widget.form.client;

import java.util.ArrayList;
import java.util.List;

/// Reads a [FormGroup]'s `controlNames`: the names of the controls in a group that has several,
/// separated by commas, e.g. `Amount, Unit`.
final class ControlNames {

    private ControlNames() {
        // Static utility
    }

    /// @param controlNames The names separated by commas, or null or blank for none.
    /// @return The names, trimmed, in order; a blank name is kept as empty, so the names after it
    /// still match their controls.
    static List<String> parse(final String controlNames) {
        final List<String> names = new ArrayList<>();
        if (controlNames != null && !controlNames.trim().isEmpty()) {
            for (final String name : controlNames.split(",", -1)) {
                names.add(name.trim());
            }
        }
        return names;
    }
}
