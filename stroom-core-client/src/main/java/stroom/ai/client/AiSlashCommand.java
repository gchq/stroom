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

package stroom.ai.client;

/// A command the user can type in the chat's input box, starting with `/`, e.g. `/compact`.
public final class AiSlashCommand {

    private final String name;
    private final String arguments;
    private final String description;

    /// @param name        The command, with its `/`, e.g. `/compact`.
    /// @param arguments   What may follow it, e.g. `[focus]`, or an empty string.
    /// @param description What it does.
    public AiSlashCommand(final String name, final String arguments, final String description) {
        this.name = name;
        this.arguments = arguments;
        this.description = description;
    }

    /// @return The command, with its `/`.
    public String getName() {
        return name;
    }

    /// @return What may follow the command, or an empty string.
    public String getArguments() {
        return arguments;
    }

    /// @return What the command does.
    public String getDescription() {
        return description;
    }
}
