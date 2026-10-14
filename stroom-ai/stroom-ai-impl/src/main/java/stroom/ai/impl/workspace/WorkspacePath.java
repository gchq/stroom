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

package stroom.ai.impl.workspace;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/// A path in a chat's workspace, e.g. {@code /outputs/errors/by-host.csv}. Paths are virtual: they name rows in
/// the database, never files on disk, and they can only reach the chat's own workspace.
///
/// A path is checked strictly when it is parsed, rather than being cleaned up: it is rejected if it has any part
/// that is empty, {@code .} or {@code ..}, a backslash, a NUL or other control character, a percent sign (so
/// nothing is ever decoded), or any character outside a small ASCII set (so look-alike Unicode characters cannot
/// pass as dots or slashes). Relative paths are taken as being from the workspace root.
///
/// @param area     The top-level area, or null for the workspace root.
/// @param segments The parts of the path below the area.
public record WorkspacePath(Area area, List<String> segments) {

    public static final WorkspacePath ROOT = new WorkspacePath(null, List.of());

    static final int MAX_PATH_LENGTH = 512;
    static final int MAX_DEPTH = 8;
    private static final Pattern SEGMENT_PATTERN = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9._ -]{0,127}");

    public WorkspacePath {
        segments = List.copyOf(segments);
    }

    /// @param raw A path given by the model.
    /// @return The path.
    /// @throws WorkspaceException if it is not a valid workspace path.
    public static WorkspacePath parse(final String raw) {
        if (raw == null || raw.isBlank()) {
            throw new WorkspaceException("Give a workspace path, e.g. /outputs/result.csv.");
        }
        if (raw.length() > MAX_PATH_LENGTH) {
            throw new WorkspaceException("The path is too long (at most " + MAX_PATH_LENGTH + " characters).");
        }
        for (int i = 0; i < raw.length(); i++) {
            final char c = raw.charAt(i);
            if (c == '\\' || c == '%' || c < 0x20 || c > 0x7e) {
                throw invalid(raw);
            }
        }
        final String trimmed = raw.strip();
        final String withoutRoot = trimmed.startsWith("/")
                ? trimmed.substring(1)
                : trimmed;
        if (withoutRoot.isEmpty()) {
            return ROOT;
        }
        // A trailing slash is allowed, to name a directory.
        final String body = withoutRoot.endsWith("/")
                ? withoutRoot.substring(0, withoutRoot.length() - 1)
                : withoutRoot;
        final String[] parts = body.split("/", -1);
        if (parts.length > MAX_DEPTH + 1) {
            throw new WorkspaceException("The path is too deep (at most " + MAX_DEPTH + " levels below an area).");
        }
        final Area area = Area.fromName(parts[0]);
        if (area == null) {
            throw new WorkspaceException("'" + raw + "' is not in the workspace. Paths start with "
                                         + Area.describeAll() + ".");
        }
        final List<String> segments = new ArrayList<>();
        for (int i = 1; i < parts.length; i++) {
            final String part = parts[i];
            if (part.equals(".") || part.equals("..") || !SEGMENT_PATTERN.matcher(part).matches()
                || part.endsWith(" ") || part.endsWith(".")) {
                throw invalid(raw);
            }
            segments.add(part);
        }
        return new WorkspacePath(area, segments);
    }

    private static WorkspaceException invalid(final String raw) {
        return new WorkspaceException("'" + raw + "' is not a valid workspace path. Each part of a path may only "
                                      + "use letters, digits, '.', '_', '-' and spaces, may not start with '.' or "
                                      + "end with '.' or a space, and '..' is not allowed.");
    }

    /// @return The path as a string, e.g. {@code /outputs/a.csv}.
    public String path() {
        if (area == null) {
            return "/";
        }
        final StringBuilder sb = new StringBuilder("/").append(area.getName());
        for (final String segment : segments) {
            sb.append('/').append(segment);
        }
        return sb.toString();
    }

    /// @return True if this names a file rather than the root or an area.
    public boolean isFile() {
        return area != null && !segments.isEmpty();
    }

    /// @param other Another path.
    /// @return True if the other path is this one, or is below it.
    public boolean contains(final WorkspacePath other) {
        if (area == null) {
            return true;
        }
        return area == other.area
               && other.segments.size() >= segments.size()
               && other.segments.subList(0, segments.size()).equals(segments);
    }

    @Override
    public String toString() {
        return path();
    }

    /// The top-level areas of a workspace, and what may be done in each.
    public enum Area {
        /// Tables the user attached. Read-only.
        ATTACHMENTS("attachments", false),
        /// Large tool results, and files written by tools and the agent.
        OUTPUTS("outputs", true),
        /// Memory, which is held separately (see AiMemoryService).
        MEMORIES("memories", true);

        private final String name;
        private final boolean writable;

        Area(final String name, final boolean writable) {
            this.name = name;
            this.writable = writable;
        }

        public String getName() {
            return name;
        }

        public boolean isWritable() {
            return writable;
        }

        static Area fromName(final String name) {
            for (final Area area : values()) {
                if (area.name.equals(name)) {
                    return area;
                }
            }
            return null;
        }

        static String describeAll() {
            return "/attachments/ (attached tables, read-only), /outputs/ (results and your own files) or "
                   + "/memories/ (memory)";
        }
    }
}
