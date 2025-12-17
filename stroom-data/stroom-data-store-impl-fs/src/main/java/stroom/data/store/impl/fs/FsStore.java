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
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.MetaProperties;
import stroom.meta.api.MetaService;
import stroom.meta.shared.Meta;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

/**
 * A file system stream store.
 * Stores streams in the stream store indexed by some metadata.
 */
@Singleton
class FsStore implements Store, AttributeMapFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FsStore.class);

    private static final int MINIMUM_BYTE_COUNT = 10;

    private final FsPathHelper fileSystemStreamPathHelper;
    private final MetaService metaService;
    private final FsVolumeService volumeService;
    private final DataVolumeService dataVolumeService;
    private final PathCreator pathCreator;
    private final S3Store s3Store;

    @Inject
    FsStore(final FsPathHelper fileSystemStreamPathHelper,
            final MetaService metaService,
            final FsVolumeService volumeService,
            final DataVolumeService dataVolumeService,
            final PathCreator pathCreator,
            final S3Store s3Store) {
        this.fileSystemStreamPathHelper = fileSystemStreamPathHelper;
        this.metaService = metaService;
        this.volumeService = volumeService;
        this.dataVolumeService = dataVolumeService;
        this.pathCreator = pathCreator;
        this.s3Store = s3Store;
    }

    @Override
    public Target openTarget(final MetaProperties metaProperties) throws DataException {
        return openTarget(metaProperties, null);
    }

    @Override
    public Target openTarget(final MetaProperties metaProperties, final String volumeGroup) {
        LOGGER.debug(() -> "openTarget() " + metaProperties);

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
        Target target = null;
        switch (dataVolume.getVolume().getVolumeType()) {
            case STANDARD -> {
                final Path volumePath = pathCreator.toAppPath(dataVolume.getVolume().getPath());
                final String streamType = meta.getTypeName();
                final FsTarget fsTarget = FsTarget.create(metaService,
                        fileSystemStreamPathHelper,
                        meta,
                        volumePath,
                        streamType);
                // Force Creation of the files
                fsTarget.getOutputStream();
                target = fsTarget;
            }
            case S3 -> {
                return s3Store.getTarget(dataVolume, meta);
            }
        }

        return target;
    }

    @Override
    public void deleteTarget(final Target target) {
        // Make sure the stream is closed.
        try {
            if (target instanceof final FsTarget fsTarget) {
                fsTarget.delete();
            } else if (target instanceof final S3Target s3Target) {
                s3Target.delete();
            }
        } catch (final RuntimeException e) {
            LOGGER.error(() -> "Unable to delete stream target! " + e.getMessage(), e);
        }
    }

    /**
     * <p>
     * Open a existing stream source.
     * </p>
     *
     * @param streamId the id of the stream to open.
     * @return The stream source if the stream can be found.
     * @throws DataException in case of a IO error or stream volume not visible or nonexistent.
     */
    @Override
    public Source openSource(final long streamId) throws DataException {
        return openSource(streamId, false);
    }

    /**
     * <p>
     * Open a existing stream source.
     * </p>
     *
     * @param streamId  The stream id to open a stream source for.
     * @param anyStatus Used to specify if this method will return stream sources that
     *                  are logically deleted or locked. If false only unlocked stream
     *                  sources will be returned, null otherwise.
     * @return The loaded stream source if it exists (has not been physically
     * deleted) else null. Also returns null if one exists but is
     * logically deleted or locked unless <code>anyStatus</code> is
     * true.
     * @throws DataException Could be thrown if no volume
     */
    @Override
    public Source openSource(final long streamId, final boolean anyStatus) throws DataException {
        try {
            LOGGER.debug(() -> "openSource() " + streamId);

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

            Source source = null;
            switch (dataVolume.getVolume().getVolumeType()) {
                case STANDARD -> {
                    final Path volumePath = pathCreator.toAppPath(dataVolume.getVolume().getPath());
                    source = FsSource.create(fileSystemStreamPathHelper, meta, volumePath, meta.getTypeName());
                }
                case S3 -> source = s3Store.getSource(dataVolume, meta);
            }

            return source;
        } catch (final DataException e) {
            LOGGER.debug(e::getMessage, e);
            throw e;
        }
    }

    @Override
    public Map<String, String> getAttributes(final long metaId) {
        try (final Source source = openSource(metaId, true)) {
            return source != null
                    ? source.getAttributes()
                    : Collections.emptyMap();
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

}
