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

import stroom.openai.shared.McpToolPolicy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/// What is known about an MCP tool offered to the model, for deciding whether a call to it needs the user's
/// approval. Annotations are only hints from the server, so they only count if the server is trusted.
///
/// @param prefixedName    The name the model sees.
/// @param serverKey       The server's name.
/// @param toolName        The tool's own name.
/// @param trusted         True if an administrator has marked the server as trusted.
/// @param readOnlyHint    The tool's readOnlyHint annotation, or null.
/// @param destructiveHint The tool's destructiveHint annotation, or null.
/// @param openWorldHint   The tool's openWorldHint annotation, or null.
/// @param confirms        True if the tool says (as `_meta.confirms`) that it asks the user itself, through a
///                        form, before doing anything.
/// @param policy          The tool's policy from the server's config, or null.
/// @param hash            A hash of the server's endpoint and trust, and the tool's name, description and input
///                        schema, so approvals of a tool are asked again if it, or its server, changes.
/// @param serverHash      A hash of the server's endpoint and trust, for approvals covering the server.
public record McpToolInfo(String prefixedName,
                          String serverKey,
                          String toolName,
                          boolean trusted,
                          Boolean readOnlyHint,
                          Boolean destructiveHint,
                          Boolean openWorldHint,
                          boolean confirms,
                          McpToolPolicy policy,
                          String hash,
                          String serverHash) {

    /// @return True if the tool says it cannot change anything (whether or not the server is trusted).
    public boolean claimsReadOnly() {
        return Boolean.TRUE.equals(readOnlyHint)
               && !Boolean.TRUE.equals(destructiveHint)
               && !Boolean.TRUE.equals(openWorldHint);
    }

    /// @return True if the tool can destroy things or reach outside Stroom, by its annotations on a trusted
    /// server, or if its server is not trusted (so nothing it says can be relied on).
    public boolean isRisky() {
        return !trusted || Boolean.TRUE.equals(destructiveHint) || Boolean.TRUE.equals(openWorldHint);
    }

    /// @param parts Text to hash.
    /// @return The SHA-256 hash of the parts, as hex.
    public static String hash(final String... parts) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (final String part : parts) {
                digest.update(String.valueOf(part).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
