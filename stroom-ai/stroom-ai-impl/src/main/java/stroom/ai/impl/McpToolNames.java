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

package stroom.ai.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

/// Builds the names the model sees for MCP tools. Each tool name is prefixed with its server, e.g.
/// `stroom_mcp__find_documents`, so that tools from different servers cannot clash with each other or with
/// Stroom's built-in tools, and so that a server cannot pass its tool off as another one's.
///
/// Names are limited to what model APIs accept: letters, digits, `_` and `-`, and at most 64 characters.
final class McpToolNames {

    static final int MAX_NAME_LENGTH = 64;
    static final String SEPARATOR = "__";
    private static final int MAX_SERVER_PART_LENGTH = 24;
    private static final int HASH_LENGTH = 8;

    private McpToolNames() {
        // Static only.
    }

    /// @param serverLabel The server's name.
    /// @return The server part of its tools' names: lower case letters, digits and single underscores.
    static String serverPrefix(final String serverLabel) {
        final String slug = sanitise(serverLabel == null
                ? ""
                : serverLabel.toLowerCase(Locale.ROOT))
                .replaceAll("[-_]+", "_")
                .replaceAll("^_+|_+$", "");
        final String trimmed = slug.length() > MAX_SERVER_PART_LENGTH
                ? slug.substring(0, MAX_SERVER_PART_LENGTH).replaceAll("_+$", "")
                : slug;
        return trimmed.isEmpty()
                ? "mcp"
                : trimmed;
    }

    /// @param serverPrefix The server part, from [#serverPrefix(String)].
    /// @param toolName     The tool's name, as given by its server.
    /// @param usedNames    Names already given out in this toolset. The returned name is not one of them.
    /// @return The name to show the model for the tool.
    static String prefixedName(final String serverPrefix, final String toolName, final Set<String> usedNames) {
        final String candidate = fit(serverPrefix + SEPARATOR + sanitise(toolName), toolName);
        String name = candidate;
        int suffix = 2;
        while (usedNames.contains(name)) {
            final String ending = "_" + suffix++;
            name = (candidate.length() + ending.length() > MAX_NAME_LENGTH
                    ? candidate.substring(0, MAX_NAME_LENGTH - ending.length())
                    : candidate) + ending;
        }
        return name;
    }

    private static String fit(final String name, final String original) {
        if (name.length() <= MAX_NAME_LENGTH) {
            return name;
        }
        // Too long: keep the start and add a short hash of the original name so different tools stay apart.
        final String hash = "_" + hash(original);
        return name.substring(0, MAX_NAME_LENGTH - hash.length()) + hash;
    }

    private static String sanitise(final String name) {
        final StringBuilder sb = new StringBuilder();
        for (final char chr : (name == null
                ? ""
                : name).toCharArray()) {
            final boolean ok = (chr >= 'a' && chr <= 'z')
                               || (chr >= 'A' && chr <= 'Z')
                               || (chr >= '0' && chr <= '9')
                               || chr == '_'
                               || chr == '-';
            sb.append(ok
                    ? chr
                    : '_');
        }
        return sb.toString();
    }

    private static String hash(final String value) {
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, HASH_LENGTH);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
