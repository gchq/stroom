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

package stroom.ai.impl.tools;

import stroom.ai.impl.workspace.WorkspacePath;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/// A workspace file that a tool call wrote or points to, as part of its [ToolResult]: the text names it by path,
/// and this lets a caller other than the chat (e.g. an MCP client, later) be given it as a resource link.
///
/// @param path        The file's workspace path, e.g. `/outputs/events.csv`.
/// @param mediaType   Its media type, or null if it isn't known.
/// @param description A short description of what it holds, or null.
public record ToolResource(String path, String mediaType, String description) {

    // Media types by file extension, for the files tools write.
    private static final Map<String, String> MEDIA_TYPES = Map.of(
            "csv", "text/csv",
            "json", "application/json",
            "md", "text/markdown",
            "txt", "text/plain",
            "log", "text/plain",
            "xml", "application/xml");

    public ToolResource {
        Objects.requireNonNull(path, "path");
    }

    /// @param path        A workspace file.
    /// @param description A short description of what it holds, or null.
    /// @return It as a resource, with its media type taken from its extension where that is a common one.
    public static ToolResource workspaceFile(final WorkspacePath path, final String description) {
        return workspaceFile(path.path(), description);
    }

    /// @param path        A workspace file's path, e.g. `/outputs/events.csv`.
    /// @param description A short description of what it holds, or null.
    /// @return It as a resource, with its media type taken from its extension where that is a common one.
    public static ToolResource workspaceFile(final String path, final String description) {
        return new ToolResource(path, mediaType(path), description);
    }

    /// @param path A file's path.
    /// @return Its media type, from its extension, or null if that isn't one of the common ones.
    static String mediaType(final String path) {
        final int slash = path.lastIndexOf('/');
        final int dot = path.lastIndexOf('.');
        if (dot <= slash + 1 || dot == path.length() - 1) {
            return null;
        }
        return MEDIA_TYPES.get(path.substring(dot + 1).toLowerCase(Locale.ROOT));
    }
}
