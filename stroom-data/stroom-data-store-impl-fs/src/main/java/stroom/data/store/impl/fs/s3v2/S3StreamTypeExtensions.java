/*
 * Copyright 2016-2026 Crown Copyright
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
import stroom.data.store.impl.fs.DataVolumeDao.DataVolume;
import stroom.data.store.impl.fs.FsVolumeConfig;
import stroom.data.store.impl.fs.standard.InternalStreamTypeNames;
import stroom.util.shared.NullSafe;
import stroom.util.string.StringUtil;

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
    public static final String DICTIONARY_EXTENSION = "zdict";
    public static final String DICTIONARY_PREFIX = "dict/";
    public static final int DICTIONARY_PREFIX_LENGTH = DICTIONARY_PREFIX.length();

    /**
     * streamTypeName => extension (without '.')
     */
    private static final Map<String, String> EXTENSION_MAP = new HashMap<>();
    /**
     * extension (without '.') => streamTypeName
     */
    private static final Map<String, String> CHILD_TYPES_REVERSE_MAP = new HashMap<>();

    static {
        // Child types
        putEntry(InternalStreamTypeNames.MANIFEST, "mf");
        putEntry(StreamTypeNames.META, "meta");
        putEntry(StreamTypeNames.CONTEXT, "ctx");
    }

    private final Provider<FsVolumeConfig> fsVolumeConfigProvider;

    @Inject
    S3StreamTypeExtensions(final Provider<FsVolumeConfig> fsVolumeConfigProvider) {
        this.fsVolumeConfigProvider = fsVolumeConfigProvider;
    }

    private static void putEntry(final String streamTypeName, final String extension) {
        EXTENSION_MAP.put(streamTypeName, extension);
        CHILD_TYPES_REVERSE_MAP.put(extension, streamTypeName);
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

    String getChildType(final String extension) {
        return getTypes(extension).childStreamType;
    }

    TypePair getTypes(String extension) {
        if (NullSafe.isNonBlankString(extension)) {
            if (extension.startsWith(".")) {
                extension = extension.substring(1);
            }
            final String[] parts = extension.split("\\.");
            final int len = parts.length;
            if (!ZSTD_EXTENSION.equals(parts[len - 1])) {
                throw new IllegalArgumentException("Extension '" + extension + "' doesn't end with " + ZSTD_EXTENSION);
            }

            if (!(len == 2 || len == 3)) {
                throw new IllegalArgumentException(
                        "Can't parse extension '" + extension + "', expecting 2 or 3 parts.");
            }

            String childStreamType = null;
            if (len == 3) {
                // e.g. revt.ctx.zst
                final String childTypeExt = parts[1];
                childStreamType = CHILD_TYPES_REVERSE_MAP.get(childTypeExt);
                if (childStreamType == null) {
                    throw new RuntimeException("No child stream type mapped to extension '" + extension + "'");
                }
            }

            // e.g. revt.zst or revt.ctx.zst
            final String typeExt = parts[0];
            final String streamTypeName = fsVolumeConfigProvider.get().getStreamType(typeExt)
                    .orElseThrow(() -> new RuntimeException(
                            "No stream type mapped to extension '" + typeExt + "'"));

            final TypePair typePair = new TypePair(streamTypeName, childStreamType);
            LOGGER.debug("getChildType() - extension: {}, returning: {}", extension, typePair);
            return typePair;
        } else {
            throw new IllegalArgumentException("Invalid extension: '" + extension + "'");
        }
    }

    /**
     * Converts {@link FileKey} into an S3 key of the form
     * <pre>
     * {@code <shard>/<metaId padded to 19 digits>/<metaId>.<strm type>[.<child type>].zst}
     * </pre>
     * <p>
     * {@code shard} is the last two digits of the padded metaId to provide more 'random-ness'
     * at the root of the key.
     * </p>
     * e.g. for metaId 123456, strm type Events and child type Context:
     * <pre>
     * {@code 89/0000000000123456789/123456789.evt.ctx.zst}
     * </pre>
     */
    public String getkey(final FileKey fileKey) {
        Objects.requireNonNull(fileKey);
        final long metaId = fileKey.metaId();
        final String extension = getExtension(fileKey.streamType(), fileKey.childStreamType());
        final String prefix = getPrefix(metaId);
        final String idStr = Long.toString(metaId);
        return String.join("/", prefix, idStr) + extension;
    }

    /**
     * Converts the metaId into an S3 key prefix for all items belonging to that meta record.
     * <pre>
     * {@code <shard>/<metaId padded to 19 digits>}
     * </pre>
     * </p>
     * e.g. for metaId 123456, strm type Events and child type Context:
     * <pre>
     * {@code 89/0000000000123456789}
     * </pre>
     */
    public static String getPrefix(final long metaId) {
        final String paddedId = StringUtil.zeroPad(metaId);
        // Add the last two digits to the root of the key to try to nudge S3 into putting consecutive streams
        // into separate s3 shards (not that we have any real control of that).
        final String shard = paddedId.substring(StringUtil.MAX_LONG_DIGITS - 2);
        return String.join("/", shard, paddedId);
    }

    public static String getDictkey(final String uuid) {
        Objects.requireNonNull(uuid);
        return DICTIONARY_PREFIX + uuid + "." + DICTIONARY_EXTENSION;
    }

    public FileKey getFileKey(final DataVolume dataVolume, final String key) {
        NullSafe.requireNonBlankString(key);
        final int firstDotIdx = key.indexOf(".");
        if (firstDotIdx == -1) {
            throw new IllegalArgumentException("Missing extension in key: " + key);
        }
        final String baseName = key.substring(0, firstDotIdx);
        final String[] baseNameParts = baseName.split("/");
        if (baseNameParts.length < 2) {
            throw new IllegalArgumentException("Invalid path parts in key: " + key);
        }
        final String metaIdPart = baseNameParts[baseNameParts.length - 1];
        final long metaId = StringUtil.dePadLong(metaIdPart);
        if (metaId == -1) {
            throw new IllegalArgumentException("Invalid metaIdPart in key: " + key);
        }
        final String extension = key.substring(firstDotIdx + 1);
        final TypePair typePair = getTypes(extension);
        return new FileKey(
                dataVolume.getVolumeId(),
                metaId,
                typePair.streamTypeName,
                typePair.childStreamType);
    }

    public static String getDictUuid(final String key) {
        NullSafe.requireNonBlankString(key);
        final String fileName = key.substring(DICTIONARY_PREFIX_LENGTH);
        final int firstDotIdx = fileName.indexOf(".");
        if (firstDotIdx == -1) {
            throw new IllegalArgumentException("Missing extension in key: " + key);
        }
        final String uuid = key.substring(0, firstDotIdx);
        LOGGER.debug("getDictUuid() - key: '{}', uuid: '{}'", key, uuid);
        return uuid;
    }


    // --------------------------------------------------------------------------------


    private record TypePair(String streamTypeName, String childStreamType) {

        private TypePair {
            Objects.requireNonNull(streamTypeName);
        }
    }
}
