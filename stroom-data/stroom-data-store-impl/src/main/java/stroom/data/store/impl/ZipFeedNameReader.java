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

package stroom.data.store.impl;

import stroom.data.zip.StroomZipFile;
import stroom.data.zip.StroomZipFileType;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/// Reads the names of the feeds that the streams in an uploaded zip will be written to
/// when the zip's own `.meta` and `.manifest` entries decide the feed.
///
/// This follows the same rules as `StreamTargetStreamHandler`, so the names can be
/// checked before anything is written.
final class ZipFeedNameReader {

    private ZipFeedNameReader() {
        // Utility class.
    }

    /// Reads the feed name for every stream in a zip.
    ///
    /// For each stream (i.e. each base name in the zip), the feed is the one named in its `.meta`
    /// entry, otherwise the one named in its `.manifest` entry, otherwise `defaultFeedName`.
    /// A stream that has neither a `.meta` nor a `.manifest` entry goes into the same feed as the
    /// stream before it, as `StreamTargetStreamHandler` only changes feed when it reads one of
    /// those entries.
    ///
    /// @param zipFile         The zip file to read.
    /// @param defaultFeedName The feed used for streams that don't name a feed. Cannot be null.
    /// @return The distinct feed names, sorted.
    /// @throws IOException If the zip can't be read.
    static Set<String> readFeedNames(final Path zipFile,
                                     final String defaultFeedName) throws IOException {
        Objects.requireNonNull(defaultFeedName);
        final Set<String> feedNames = new TreeSet<>();

        try (final StroomZipFile stroomZipFile = new StroomZipFile(zipFile)) {
            String currentFeedName = defaultFeedName;
            for (final String baseName : stroomZipFile.getBaseNames()) {
                final boolean hasMeta = stroomZipFile.containsEntry(baseName, StroomZipFileType.META);
                final boolean hasManifest = stroomZipFile.containsEntry(baseName, StroomZipFileType.MANIFEST);

                if (hasMeta || hasManifest) {
                    final String metaFeedName = readFeedName(stroomZipFile, baseName, StroomZipFileType.META);
                    final String manifestFeedName = readFeedName(
                            stroomZipFile,
                            baseName,
                            StroomZipFileType.MANIFEST);

                    if (metaFeedName != null) {
                        currentFeedName = metaFeedName;
                    } else if (manifestFeedName != null) {
                        currentFeedName = manifestFeedName;
                    } else {
                        currentFeedName = defaultFeedName;
                    }
                }
                feedNames.add(currentFeedName);
            }
        }
        return feedNames;
    }

    private static String readFeedName(final StroomZipFile stroomZipFile,
                                       final String baseName,
                                       final StroomZipFileType stroomZipFileType) throws IOException {
        try (final InputStream inputStream = stroomZipFile.getInputStream(baseName, stroomZipFileType)) {
            if (inputStream == null) {
                return null;
            }
            final AttributeMap attributeMap = new AttributeMap();
            AttributeMapUtil.read(inputStream, attributeMap);
            return attributeMap.get(StandardHeaderArguments.FEED);
        }
    }
}
