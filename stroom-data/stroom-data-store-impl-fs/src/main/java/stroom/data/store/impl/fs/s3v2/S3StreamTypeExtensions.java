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

package stroom.data.store.impl.fs.s3v2;

import stroom.data.shared.StreamTypeNames;
import stroom.data.store.impl.fs.FsVolumeConfig;
import stroom.data.store.impl.fs.standard.InternalStreamTypeNames;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

class S3StreamTypeExtensions {

    private static final Logger LOGGER = LoggerFactory.getLogger(S3StreamTypeExtensions.class);

    public static final String ZSTD_EXTENSION = "zst";

    private static final Map<String, String> EXTENSION_MAP = new HashMap<>();
    private static final Map<String, String> CHILD_TYPES_REVERSE_MAP = new HashMap<>();

    static {
        // Child types
        putEntry(InternalStreamTypeNames.MANIFEST, "mf");
        putEntry(StreamTypeNames.META, "meta");
        putEntry(StreamTypeNames.CONTEXT, "ctx");
    }

    private static void putEntry(final String streamTypeName, final String extension) {
        EXTENSION_MAP.put(streamTypeName, extension);
        CHILD_TYPES_REVERSE_MAP.put(extension, streamTypeName);
    }

    private final Provider<FsVolumeConfig> fsVolumeConfigProvider;

    @Inject
    S3StreamTypeExtensions(final Provider<FsVolumeConfig> fsVolumeConfigProvider) {
        this.fsVolumeConfigProvider = fsVolumeConfigProvider;
    }

    /**
     * e.g. ".revt.zst", ".evt.meta.zst"
     */
    String getExtension(final String streamType, final String childStreamType) {
        Objects.requireNonNull(streamType, "streamType must not be null");
        final List<String> parts = new ArrayList<>(3);
        final String streamTypeExt = fsVolumeConfigProvider.get().getMetaTypeExtension(streamType)
                .orElseGet(() -> {
                    LOGGER.debug("Unknown stream type '{}' using extension 'dat'", streamType);
                    return "dat";
                });
        parts.add(streamTypeExt);
        if (childStreamType != null) {
            final String childStreamTypeExt = EXTENSION_MAP.get(childStreamType);
            Objects.requireNonNull(childStreamTypeExt, () -> "Unknown child stream type '" + childStreamType + "'");
            parts.add(childStreamTypeExt);
        }
        parts.add(ZSTD_EXTENSION);
        final String extension = "." + String.join(".", parts);
        LOGGER.debug("getExtension() - streamType: {}, streamType, childStreamType: {}, returning: {}",
                streamType, streamTypeExt, childStreamType);
        return extension;
    }

    String getChildType(String extension) {
        if (NullSafe.isNonBlankString(extension)) {
            if (extension.startsWith(".")) {
                extension = extension.substring(1);
            }
            final String[] parts = extension.split("\\.");
            final int len = parts.length;
            final String childStreamType;
            if (!ZSTD_EXTENSION.equals(parts[len - 1])) {
                throw new RuntimeException("Extension '" + extension + "' doesn't end with " + ZSTD_EXTENSION);
            }
            if (len == 3) {
                final String ext = parts[1];
                childStreamType = CHILD_TYPES_REVERSE_MAP.get(ext);
                if (childStreamType == null) {
                    throw new RuntimeException("No child stream type mapped to extension '" + extension + "'");
                }
            } else {
                if (len != 2) {
                    throw new RuntimeException("Unknown extension '" + extension + "'");
                }
                childStreamType = null;
            }
            LOGGER.debug("getChildType() - extension: {}, returning: {}", extension, childStreamType);
            return childStreamType;
        } else {
            throw new IllegalArgumentException("Invalid extension: '" + extension + "'");
        }
    }
}
