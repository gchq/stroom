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

package stroom.data.store.impl.fs;

import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.AttributeMapFactory;
import stroom.data.store.api.DataException;
import stroom.data.store.api.InputStreamProvider;
import stroom.data.store.api.SizeAwareInputStream;
import stroom.data.store.api.Source;
import stroom.data.store.api.Store;
import stroom.data.store.api.Target;
import stroom.data.store.impl.fs.DataVolumeDao.DataVolume;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.data.store.impl.fs.shared.FsVolumeType;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.MetaProperties;
import stroom.meta.api.MetaService;
import stroom.meta.shared.Meta;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import org.jspecify.annotations.NullMarked;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * This is the front door for all stream store implementations. It delegates down to
 * a {@link StreamStore} implementation depending on the volume type of the stream.
 */
@NullMarked
public class StoreImpl implements Store, AttributeMapFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StoreImpl.class);

    private static final int MINIMUM_BYTE_COUNT = 10;

    private final Map<FsVolumeType, StreamStore> streamStoreMap;
    private final MetaService metaService;
    private final FsVolumeService volumeService;
    private final DataVolumeService dataVolumeService;

    @Inject
    public StoreImpl(final Map<FsVolumeType, StreamStore> streamStoreMap,
                     final MetaService metaService,
                     final FsVolumeService volumeService,
                     final DataVolumeService dataVolumeService) {
        this.streamStoreMap = streamStoreMap;
        this.metaService = metaService;
        this.volumeService = volumeService;
        this.dataVolumeService = dataVolumeService;
    }

    @Override
    public Target openTarget(final MetaProperties metaProperties, final String volumeGroup) throws DataException {
        Objects.requireNonNull(metaProperties);
        Objects.requireNonNull(metaProperties);
        LOGGER.debug("openTarget() - metaProperties: {}, volumeGroup: {}", metaProperties, volumeGroup);

        try {
            final FsVolume volume = volumeService.getVolume(volumeGroup);
            if (volume == null) {
                throw new DataException("""
                        Failed to get lock as no writable volumes. This may be because there are no active \
                        volumes configured or the active volumes (or the filesystem(s) they sit on) are full \
                        or near full.""");
            }

            // First time call (no file yet exists)
            final Meta meta = metaService.create(metaProperties);
            final DataVolume dataVolume = dataVolumeService.createDataVolume(meta.getId(), volume);
            final StreamStore streamStore = getStreamStore(dataVolume.volume().getVolumeType());
            // Delegate to the appropriate store
            final Target target = streamStore.openTarget(meta, dataVolume);

            LOGGER.debug(() -> LogUtil.message("openTarget() - returning target: {}, volumeId: {}, meta: {}",
                    target.getClass().getSimpleName(), dataVolume.volume().getId(), meta));
            return target;
        } catch (final DataException e) {
            LOGGER.debug(e::getMessage, e);
            throw e;
        }
    }

    @Override
    public Source openSource(final long streamId, final boolean anyStatus) throws DataException {
        LOGGER.debug("openSource() - streamId: {}, anyStatus: {}", streamId, anyStatus);

        try {
            final Meta meta = metaService.getMeta(streamId, anyStatus);
            if (meta == null) {
                if (anyStatus) {
                    throw new DataException("Unable to find meta data for id=" + streamId + " with any status");
                } else {
                    throw new DataException("Unable to find meta data for id=" + streamId + " with valid status");
                }
            }

            final DataVolume dataVolume = dataVolumeService.findDataVolume(meta.getId());
            if (dataVolume == null) {
                throw new DataException("Unable to find any volume for " + meta);
            }
            final FsVolume volume = dataVolume.volume();
            final StreamStore streamStore = getStreamStore(volume.getVolumeType());
            // Delegate to the appropriate store
            final Source source = streamStore.openSource(meta, dataVolume);
            LOGGER.debug(() -> LogUtil.message("openSource() - returning source: {}, volumeId: {}, meta: {}",
                    source.getClass().getSimpleName(), dataVolume.volume().getId(), meta));
            return source;
        } catch (final DataException e) {
            LOGGER.debug(e::getMessage, e);
            throw e;
        }
    }

    @Override
    public void logicallyDeleteTarget(final Target target) {
        if (target != null) {
            try {
                LOGGER.debug(() -> LogUtil.message("logicallyDeleteTarget() - target: {}",
                        LogUtil.typedValue(target)));
                target.logicallyDelete();
            } catch (final Exception e) {
                LOGGER.error(() -> LogUtil.message("Unable to delete stream {} {}! - {}",
                        LogUtil.getSimpleClassName(target), target, e.getMessage()), e);
            }
        }
    }

    @Override
    public void physicallyDelete(final Collection<Long> metaIds) {
        LOGGER.debug(() -> LogUtil.message("physicallyDelete() - metaIds ({}) sample: {}",
                metaIds.size(),
                LogUtil.getSample(metaIds, 20)));
        final List<DataVolume> allDataVolumes = dataVolumeService.findDataVolumes(metaIds);
        final Map<FsVolumeType, List<DataVolume>> dataVolumesByType = allDataVolumes.stream()
                .collect(Collectors.groupingBy(DataVolume::getVolumeType));
        dataVolumesByType.forEach((fsVolumeType, dataVolumes) -> {
            final StreamStore streamStore = getStreamStore(fsVolumeType);
            streamStore.physicallyDelete(dataVolumes);
        });
    }

    @Override
    public Map<String, String> getAttributes(final long metaId) {
        try (final Source source = openSource(metaId, true)) {
            return NullSafe.getOrElseGet(source, Source::getAttributes, Collections::emptyMap);
        } catch (final IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public AttributeMap getAttributeMapForPart(final long streamId, final long partNo) {
        try (final Source source = openSource(streamId)) {
            final AttributeMap attributeMap = new AttributeMap();

            // Setup meta data.
            final InputStreamProvider provider = source.get(partNo);
            if (provider != null) {
                // Get the input stream.
                final SizeAwareInputStream inputStream = provider.get(StreamTypeNames.META);

                // Make sure we got an input stream.
                if (inputStream != null) {
                    // Only use meta data if we actually have some.
                    final long byteCount = inputStream.size();
                    if (byteCount > MINIMUM_BYTE_COUNT) {
                        AttributeMapUtil.read(inputStream, attributeMap);
                    }
                }
            }
            return attributeMap;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private StreamStore getStreamStore(final FsVolumeType fsVolumeType) {
        Objects.requireNonNull(fsVolumeType, "FsVolumeType must not be null");
        final StreamStore streamStore = streamStoreMap.get(fsVolumeType);
        LOGGER.debug(() -> LogUtil.message(
                "getStreamStore() - fsVolumeType: {}, streamStore: {}",
                fsVolumeType, LogUtil.typedValue(streamStore)));
        if (streamStore == null) {
            throw new IllegalArgumentException("No StreamStore for " + fsVolumeType);
        }
        return streamStore;
    }
}
