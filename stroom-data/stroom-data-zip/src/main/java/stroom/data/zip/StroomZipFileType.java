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

package stroom.data.zip;

import stroom.util.shared.NullSafe;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum StroomZipFileType {
    // index values MUST be zero based and contiguous as they are used as array positions
    MANIFEST(0, "mf", "manifest"),
    META(1, "meta", "hdr", "header", "met"),
    CONTEXT(2, "ctx", "context"),
    DATA(3, "dat");

    /**
     * Map from the canonical extension or any extensionAlias to the StroomZipFileType
     */
    private static final Map<String, StroomZipFileType> EXTENSION_MAP = Arrays.stream(StroomZipFileType.values())
            .flatMap(stroomZipFileType ->
                    Stream.concat(
                                    Stream.of(stroomZipFileType.extension),
                                    stroomZipFileType.extensionAliases.stream())
                            .map(ext -> Map.entry(ext, stroomZipFileType)))
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue));

    /**
     * Map from the canonical extension to the StroomZipFileType, but NOT from an extensionAlias
     */
    private static final Map<String, StroomZipFileType> CANONICAL_EXTENSION_MAP =
            Arrays.stream(StroomZipFileType.values())
                    .collect(Collectors.toMap(StroomZipFileType::getExtension, Function.identity()));

    private final int index;
    private final String extension;
    private final Set<String> extensionAliases;

    StroomZipFileType(final int index,
                      final String extension,
                      final String... extensionAliases) {
        this.index = index;
        this.extension = extension;
        this.extensionAliases = NullSafe.asSet(extensionAliases);
    }

    public int getIndex() {
        return index;
    }

    /**
     * The official extension for the file type, e.g. 'dat'.
     *
     * @return The official extension for the file type.
     */
    public String getExtension() {
        return extension;
    }

    /**
     * Other known and possibly legacy extension(s) for this file type, e.g. 'hdr'.
     */
    public Set<String> getExtensionAliases() {
        return extensionAliases;
    }

    /**
     * Convenience method to return the extension with a `.` in front of it, e.g. '.dat'.
     */
    public String getDotExtension() {
        return "." + extension;
    }

    /**
     * @return True if path ends with this extension (or one of the aliases).
     */
    public boolean hasExtension(final Path path) {
        return path != null && hasExtension(path.getFileName().toString());
    }

    /**
     * @return True if fileName ends with this extension (or one of the aliases).
     */
    public boolean hasExtension(final String fileName) {
        return fileName != null
               && (fileName.endsWith(getDotExtension())
                   || getExtensionAliases().stream().anyMatch(ext -> fileName.endsWith("." + ext)));
    }

    /**
     * @return True if fileName ends with the official extension for this type, but NOT an alias extension
     */
    public boolean hasOfficialExtension(final String fileName) {
        return fileName != null
               && fileName.endsWith(getDotExtension());
    }

    /**
     * Map loosely from the canonical extension or any extensionAlias to the StroomZipFileType.
     * Case-insensitive.
     * If no match is found will return {@link StroomZipFileType#DATA}
     */
    public static StroomZipFileType fromExtension(final String extension) {
        StroomZipFileType stroomZipFileType = null;
        if (NullSafe.isNonEmptyString(extension)) {
            stroomZipFileType = EXTENSION_MAP.get(extension.toLowerCase(Locale.ROOT));
        }
        return Objects.requireNonNullElse(stroomZipFileType, StroomZipFileType.DATA);
    }

    /**
     * Map strictly from the canonical extension to the StroomZipFileType, but NOT from an extensionAlias.
     * Case-sensitive.
     * If no match is found will return null.
     */
    public static StroomZipFileType fromCanonicalExtension(final String canonicalExtension) {
        return NullSafe.isNonEmptyString(canonicalExtension)
                ? CANONICAL_EXTENSION_MAP.get(canonicalExtension)
                : null;
    }

    /**
     * @return True if the passed extension is one known to stroom
     */
    public static boolean isKnownExtension(final String extension) {
        if (NullSafe.isEmptyString(extension)) {
            return false;
        } else {
            return EXTENSION_MAP.containsKey(extension.toLowerCase(Locale.ROOT));
        }
    }
}
