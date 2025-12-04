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

package stroom.receive.common;

import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.OutputStreamProvider;
import stroom.data.store.api.Store;
import stroom.data.store.api.Target;
import stroom.data.zip.StroomZipEntries;
import stroom.data.zip.StroomZipEntry;
import stroom.data.zip.StroomZipFileType;
import stroom.feed.api.FeedProperties;
import stroom.feed.api.VolumeGroupNameProvider;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.MetaProperties;
import stroom.meta.api.StandardHeaderArguments;
import stroom.meta.shared.Meta;
import stroom.meta.statistics.api.MetaStatistics;
import stroom.util.io.CloseableUtil;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Type of {@link StreamHandler} that store the entries in the stream store.
 * There are some special rules about how this works.
 * <p>
 * This is fine if all the meta data indicates they belong to the same feed
 * 001.meta, 002.meta, 001.dat, 002.dat
 * <p>
 * This is also fine if 001.meta indicates 001 belongs to feed X and 002.meta
 * indicates 001 belongs to feed Y 001.meta, 002.meta, 001.dat, 002.dat
 * <p>
 * However if the global header map indicates feed Z and the files are send in
 * the following order 001.dat, 002.dat, 001.meta, 002.meta this is invalid ....
 * I.E. as soon as we add non header stream for a feed if the header turns out
 * to be different we must throw an exception.
 */
public class StreamTargetStreamHandler implements StreamHandler, Closeable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StreamTargetStreamHandler.class);

    private final byte[] buffer = new byte[StreamUtil.BUFFER_SIZE];
    private final Store store;
    private final FeedProperties feedProperties;
    private final MetaStatistics metaDataStatistics;
    private final VolumeGroupNameProvider volumeGroupNameProvider;
    private final String typeName;
    private final AttributeMap globalAttributeMap;
    private final Set<Meta> streamSet;
    private final StroomZipEntries stroomZipEntries;
    private final Map<String, Target> targetMap = new HashMap<>();
    private final ByteArrayOutputStream tempByteArrayOutputStream = new ByteArrayOutputStream();
    private String lastBaseName;
    private String currentFeedName;
    private AttributeMap manifestAttributeMap;
    private AttributeMap metaAttributeMap;

    private OutputStreamProvider currentOutputStreamProvider;
    private final Layer layer = new Layer();

    StreamTargetStreamHandler(final Store store,
                              final FeedProperties feedProperties,
                              final MetaStatistics metaDataStatistics,
                              final VolumeGroupNameProvider volumeGroupNameProvider,
                              final String feedName,
                              final String typeName,
                              final AttributeMap globalAttributeMap) {
        this.store = store;
        this.feedProperties = feedProperties;
        this.metaDataStatistics = metaDataStatistics;
        this.volumeGroupNameProvider = volumeGroupNameProvider;
        this.currentFeedName = feedName;
        this.typeName = typeName;
        this.streamSet = new HashSet<>();
        this.stroomZipEntries = new StroomZipEntries();

        if (globalAttributeMap == null) {
            this.globalAttributeMap = null;
        } else {
            this.globalAttributeMap = AttributeMapUtil.cloneAllowable(globalAttributeMap);
        }
    }

    @Override
    public long addEntry(final String entryName,
                         final InputStream inputStream,
                         final Consumer<Long> progressHandler) throws IOException {
        final long bytesWritten;
        LOGGER.debug(() -> "addEntry() - " + entryName);

        final StroomZipEntry entry = stroomZipEntries.addFile(entryName);
        final String baseName = entry.getBaseName();
        final StroomZipFileType stroomZipFileType = entry.getStroomZipFileType();

        // We don't want to aggregate reference feeds.
        final boolean singleEntry = feedProperties.isReference(currentFeedName);

        // If the base name changes then reset and we will treat this as a new layer.
        final boolean requiresNewLayer = layer.hasType(stroomZipFileType) || (lastBaseName != null &&
                                                                              !lastBaseName.equals(baseName));
        if (requiresNewLayer) {
            reset();
            if (singleEntry) {
                closeCurrentFeed();
            }
        }

        lastBaseName = baseName;
        // Tell the new layer that it will contain the requested type.
        layer.addType(stroomZipFileType);

        if (StroomZipFileType.MANIFEST.equals(stroomZipFileType)) {
            manifestAttributeMap = new AttributeMap();
            final byte[] bytes = readAttributes(inputStream, manifestAttributeMap, progressHandler);
            bytesWritten = bytes.length;
            putAll(globalAttributeMap, manifestAttributeMap);

            // Are we switching feed?
            final String feedName = getCurrentAttributeMap().get(StandardHeaderArguments.FEED);
            if (feedName != null && !feedName.equals(currentFeedName)) {
                if (layer.hasType(StroomZipFileType.DATA) || layer.hasType(StroomZipFileType.CONTEXT)) {
                    throw new IOException("Header and Data out of order for multiple feed data");
                }
                currentFeedName = feedName;
            }

        } else if (StroomZipFileType.META.equals(stroomZipFileType)) {
            metaAttributeMap = new AttributeMap();
            putAll(manifestAttributeMap, metaAttributeMap);
            final byte[] bytes = readAttributes(inputStream, metaAttributeMap, progressHandler);
            bytesWritten = bytes.length;
            putAll(globalAttributeMap, metaAttributeMap);

            if (metaDataStatistics != null) {
                metaDataStatistics.recordStatistics(metaAttributeMap);
            }

            // Are we switching feed?
            final String feedName = getCurrentAttributeMap().get(StandardHeaderArguments.FEED);
            if (feedName != null && !feedName.equals(currentFeedName)) {
                if (layer.hasType(StroomZipFileType.DATA) || layer.hasType(StroomZipFileType.CONTEXT)) {
                    throw new IOException("Header and Data out of order for multiple feed data");
                }
                currentFeedName = feedName;
            }

            final OutputStreamProvider outputStreamProvider = getOutputStreamProvider(currentFeedName, typeName);
            try (final OutputStream outputStream = outputStreamProvider.get(StreamTypeNames.META)) {
                AttributeMapUtil.write(metaAttributeMap, outputStream);
            }

        } else if (StroomZipFileType.CONTEXT.equals(stroomZipFileType)) {
            final OutputStreamProvider outputStreamProvider = getOutputStreamProvider(currentFeedName, typeName);
            try (final OutputStream currentOutputStream = outputStreamProvider.get(StreamTypeNames.CONTEXT)) {
                bytesWritten = StreamUtil.streamToStream(
                        inputStream,
                        currentOutputStream,
                        buffer,
                        progressHandler);
            }

        } else {
            final OutputStreamProvider outputStreamProvider = getOutputStreamProvider(currentFeedName, typeName);
            try (final OutputStream currentOutputStream = outputStreamProvider.get()) {
                bytesWritten = StreamUtil.streamToStream(
                        inputStream,
                        currentOutputStream,
                        buffer,
                        progressHandler);
            }
        }

        return bytesWritten;
    }

    private void putAll(final AttributeMap source,
                        final AttributeMap dest) {
        if (source != null && dest != null) {
            source.forEach((k, v) -> {
                if (source.isOverrideEmbeddedMeta()) {
                    dest.remove(k);
                    dest.put(k, v);
                } else {
                    dest.putIfAbsent(k, v);
                }
            });
        }
    }

    private byte[] readAttributes(final InputStream inputStream,
                                  final AttributeMap attributeMap,
                                  final Consumer<Long> progressHandler) {
        byte[] bytes = new byte[0];
        try {
            tempByteArrayOutputStream.reset();
            StreamUtil.streamToStream(
                    inputStream,
                    tempByteArrayOutputStream,
                    buffer,
                    progressHandler);
            bytes = tempByteArrayOutputStream.toByteArray();
            AttributeMapUtil.read(bytes, attributeMap);
        } catch (final IOException | RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }
        return bytes;
    }

    private void reset() {
        layer.clear();
        manifestAttributeMap = null;
        metaAttributeMap = null;
        currentOutputStreamProvider = null;
    }

    void error() {
        targetMap.values().forEach(store::deleteTarget);
        targetMap.clear();
    }

    public void closeDelete() {
        targetMap.values().forEach(store::deleteTarget);
        targetMap.clear();
    }

    @Override
    public void close() {
        targetMap.values().forEach(CloseableUtil::closeLogAndIgnoreException);
        targetMap.clear();
    }

    private void closeCurrentFeed() {
        LOGGER.debug(() -> "closeCurrentFeed() - " + currentFeedName);
        final Target target = targetMap.remove(currentFeedName);
        if (target != null) {
            CloseableUtil.closeLogAndIgnoreException(target);
        }
    }

    public Set<Meta> getStreamSet() {
        return Collections.unmodifiableSet(streamSet);
    }

    private AttributeMap getCurrentAttributeMap() {
        if (metaAttributeMap != null) {
            return metaAttributeMap;
        }
        if (manifestAttributeMap != null) {
            return manifestAttributeMap;
        }
        return globalAttributeMap;
    }

    private OutputStreamProvider getOutputStreamProvider(final String feedName, final String typeName) {
        // Check to see if we need to move to the next output and do so if necessary.
        if (currentOutputStreamProvider == null) {
            // Get a new output stream provider for the new layer.
            currentOutputStreamProvider = getTarget(feedName, typeName).next();
        }
        return currentOutputStreamProvider;
    }

    private Target getTarget(final String feedName, final String typeName) {
        return targetMap.computeIfAbsent(feedName, k -> {
            LOGGER.debug(() -> "getTarget() - open stream for " + feedName);

            // Get the effective time if one has been provided.
            final Long effectiveMs = StreamFactory.getReferenceEffectiveTime(getCurrentAttributeMap(), true);

            final MetaProperties metaProperties = MetaProperties.builder()
                    .feedName(feedName)
                    .typeName(typeName)
                    .effectiveMs(effectiveMs)
                    .build();

            final String volumeGroupName = volumeGroupNameProvider
                    .getVolumeGroupName(feedName, typeName, null);
            final Target streamTarget = store.openTarget(metaProperties, volumeGroupName);
            streamSet.add(streamTarget.getMeta());
            return streamTarget;
        });
    }


    // --------------------------------------------------------------------------------


    private static class Layer {

        private final Set<StroomZipFileType> types = new HashSet<>();

        boolean hasType(final StroomZipFileType type) {
            return types.contains(type);
        }

        void addType(final StroomZipFileType type) {
            types.add(type);
        }

        void clear() {
            types.clear();
        }
    }
}
