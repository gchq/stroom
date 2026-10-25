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

/// A table column a chart kind takes, e.g. a bar chart's value.
///
/// @param id          The id of its field control in the visualisation's settings.
/// @param label       Its label in the visualisation's settings.
/// @param required    True if the chart can't be drawn without it.
/// @param values      The values it needs.
/// @param description What it is for, for the agent.
public record ChartRole(String id,
                        String label,
                        boolean required,
                        Values values,
                        String description) {

    /// The values a role needs.
    public enum Values {
        /// Anything: text, numbers or dates.
        ANY("any values"),
        /// Numbers.
        NUMBER("numbers"),
        /// Numbers or dates.
        NUMBER_OR_DATE("numbers or dates");

        private final String description;

        Values(final String description) {
            this.description = description;
        }

        /// @return What a user would call these values.
        public String description() {
            return description;
        }
    }
}
