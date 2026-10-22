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

package stroom.mcp.impl.tools;

import stroom.util.shared.NullSafe;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/// XSLT code as the tools compare and digest it, as the Stroom MCP server did (its `utils/xsltversion.strip`,
/// `utils/mappingstore.normalise_xslt` and `digest`), so a digest either server recorded matches the other's.
final class XsltCode {

    // The version history comment XSLTs carried before (stroom-mcp 0.16.43 to 0.16.45): not code.
    private static final Pattern HISTORY_BLOCK = Pattern.compile(
            "[ \\t]*<!-- stroom-mcp version history\\n(.*?)\\nend of version history -->[ \\t]*\\n?", Pattern.DOTALL);
    private static final Pattern DECLARATION = Pattern.compile("^\\s*<\\?xml[^>]*\\?>",
            Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern BETWEEN_TAGS = Pattern.compile(">\\s+<", Pattern.UNICODE_CHARACTER_CLASS);

    private XsltCode() {
        // Static methods only.
    }

    /// @param code XSLT code, or null.
    /// @return It without the version history comment an earlier version of the Stroom MCP server put in it.
    static String strip(final String code) {
        return HISTORY_BLOCK.matcher(NullSafe.string(code)).replaceFirst("");
    }

    /// @param code XSLT code, or null.
    /// @return It with the differences that are not edits removed: the declaration, whitespace between tags, the
    /// version history comment.
    static String normalise(final String code) {
        final String withoutDeclaration = DECLARATION.matcher(strip(code)).replaceFirst("");
        return BETWEEN_TAGS.matcher(withoutDeclaration).replaceAll("><").strip();
    }

    /// @param parts Texts, e.g. a mapping's JSON and an XSLT's normalised code.
    /// @return A short digest of them (16 hex characters), as the Stroom MCP server's `mappingstore.digest`.
    static String digest(final String... parts) {
        return sha256(String.join("\n", parts)).substring(0, 16);
    }

    /// @param text Some text.
    /// @return The SHA-256 of its UTF-8 bytes, in lower-case hex.
    static String sha256(final String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(NullSafe.string(text).getBytes(StandardCharsets.UTF_8)));
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
