/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.util.io;

import java.util.regex.Pattern;

/**
 * Central place for making a single identifier (a feed name, document type, UUID, file name, etc.) safe to use
 * as <b>one</b> segment of a file path or URL.
 * <p>
 * The default policy is deliberately minimal: it only removes what could let a value break out of its own
 * segment - the path separators {@code /} and {@code \} (removing {@code /} also prevents an absolute-path
 * escape via {@link java.nio.file.Path#resolve(String)}), the NUL and other control characters, and the
 * pure-traversal segments {@code .} and {@code ..}. Everything else (spaces, dots within the value, case,
 * non-ASCII letters) is preserved so the value stays human-readable.
 * <p>
 * This operates on a <b>single segment</b>, never a whole path - callers that assemble a path from a template
 * (e.g. {@link PathCreator}) must normalise each substituted value, not the finished path, otherwise the
 * template's own separators would be stripped.
 */
public final class PathSegmentUtil {

    private static final char REPLACEMENT_CHAR = '_';

    // Legacy naming policies. These produce names that are persisted (they form on-disk directory names that
    // are also recorded in path-mapping tables), so the exact mapping must stay stable over time - they are
    // NOT the minimal policy above and must not be changed.
    private static final Pattern LEGACY_UPPER_CASE_UNSAFE_CHARS = Pattern.compile("[^A-Z0-9_-]");
    private static final Pattern LEGACY_MIXED_CASE_UNSAFE_CHARS = Pattern.compile("[^a-zA-Z0-9_-]");

    private PathSegmentUtil() {
        // Utility class.
    }

    /**
     * @return true if {@code segment} can be used verbatim as a single path/URL segment with no risk of
     * escaping it.
     */
    public static boolean isSafeSegment(final String segment) {
        if (segment == null || segment.isEmpty()) {
            return false;
        }
        if (isDotOrDotDot(segment)) {
            // "." (current dir) or ".." (parent dir) - the only pure-traversal segments.
            return false;
        }
        for (int i = 0; i < segment.length(); i++) {
            if (isUnsafeChar(segment.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Fail-closed check for use at trust boundaries (e.g. parsing an imported document's UUID/type, or a
     * segment derived from data received from another node). Returns the segment unchanged when it is safe,
     * otherwise throws - a value that needs altering here indicates corrupt or malicious input.
     *
     * @throws IllegalArgumentException if the segment is not safe to use as a single path/URL segment.
     */
    public static String requireSafeSegment(final String segment) {
        if (!isSafeSegment(segment)) {
            throw new IllegalArgumentException("Unsafe path segment: '" + segment + "'");
        }
        return segment;
    }

    /**
     * Lenient normalisation for use where a value should be made safe rather than rejected (e.g. building a
     * file name for download, or substituting a feed name into a path template). Removes only the characters
     * that could escape the segment and preserves the rest. A value that is empty or consists solely of dots
     * after cleaning is replaced with a single {@code _} so it can never act as a traversal segment.
     */
    public static String cleanSegment(final String segment) {
        if (segment == null) {
            return null;
        }

        final StringBuilder sb = new StringBuilder(segment.length());
        for (int i = 0; i < segment.length(); i++) {
            final char c = segment.charAt(i);
            if (!isUnsafeChar(c)) {
                sb.append(c);
            }
        }

        final String cleaned = sb.toString();
        if (cleaned.isEmpty() || isDotOrDotDot(cleaned)) {
            return String.valueOf(REPLACEMENT_CHAR);
        }
        return cleaned;
    }

    /**
     * Legacy naming policy: upper-cases {@code name} and replaces every character other than {@code A-Z},
     * {@code 0-9}, {@code _} and {@code -} with {@code _}. Used for on-disk directory names in the file-system
     * data store and the reference-data store, where the mapping is persisted and so must remain stable.
     */
    public static String toLegacyUpperCaseName(final String name) {
        return LEGACY_UPPER_CASE_UNSAFE_CHARS.matcher(name.toUpperCase()).replaceAll("_");
    }

    /**
     * Legacy naming policy: replaces every character other than {@code a-z}, {@code A-Z}, {@code 0-9},
     * {@code _} and {@code -} with {@code _} (case preserved). Used for on-disk directory names in the proxy,
     * where the mapping is persisted and so must remain stable.
     */
    public static String toLegacyMixedCaseName(final String name) {
        return LEGACY_MIXED_CASE_UNSAFE_CHARS.matcher(name).replaceAll("_");
    }

    private static boolean isUnsafeChar(final char c) {
        // Path separators (both platforms) plus NUL and other control characters. Removing '/' also stops an
        // absolute-path escape, since Path.resolve on an absolute argument would discard the base directory.
        return c == '/' || c == '\\' || c < 0x20 || c == 0x7f;
    }

    private static boolean isDotOrDotDot(final String value) {
        return ".".equals(value) || "..".equals(value);
    }
}
