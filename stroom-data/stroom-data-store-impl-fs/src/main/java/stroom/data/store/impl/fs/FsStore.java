/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.data.store.impl.fs;


import stroom.data.store.api.DataException;
import stroom.data.store.api.Source;
import stroom.data.store.api.Store;
import stroom.data.store.api.Target;
import stroom.data.store.impl.AttributeMapFactory;
import stroom.data.store.impl.fs.DataVolumeDao.DataVolume;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.datasource.api.v2.AbstractField;
import stroom.meta.api.MetaProperties;
import stroom.meta.api.MetaService;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.util.io.PathCreator;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * A file system stream store.
 * Stores streams in the stream store indexed by some metadata.
 */
@Singleton
class FsStore implements Store, AttributeMapFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FsStore.class);

    private final FsPathHelper fileSystemStreamPathHelper;
    private final MetaService metaService;
    private final FsVolumeService volumeService;
    private final DataVolumeService dataVolumeService;
    private final PathCreator pathCreator;
    private final TempDirProvider tempDirProvider;

    @Inject
    FsStore(final FsPathHelper fileSystemStreamPathHelper,
            final MetaService metaService,
            final FsVolumeService volumeService,
            final DataVolumeService dataVolumeService,
            final PathCreator pathCreator,
            final TempDirProvider tempDirProvider) {
        this.fileSystemStreamPathHelper = fileSystemStreamPathHelper;
        this.metaService = metaService;
        this.volumeService = volumeService;
        this.dataVolumeService = dataVolumeService;
        this.pathCreator = pathCreator;
        this.tempDirProvider = tempDirProvider;
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
                final String streamType = meta.getTypeName();
                final S3Manager s3Manager = new S3Manager(volume.getS3ClientConfig());
                final Path tempDir = createTempPath(meta.getId());
                final S3Target s3Target = S3Target.create(
                        metaService,
                        s3Manager,
                        new S3PathHelper(),
                        meta,
                        streamType,
                        tempDir);
                // Force Creation of the files
                s3Target.getOutputStream();
                target = s3Target;
            }
        }

        return target;
    }

    private Path createTempPath(final Long metaId) {
        try {
            final Path path = tempDirProvider.get().resolve("s3_" + metaId + "__" + UUID.randomUUID());
            Files.createDirectories(path);
            return path;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Target deleteTarget(final Target target) {
        // Make sure the stream is closed.
        try {
            if (target instanceof final FsTarget fsTarget) {
                fsTarget.delete();
            } else if (target instanceof S3Target s3Target) {
                s3Target.delete();
            }
        } catch (final RuntimeException e) {
            LOGGER.error(() -> "Unable to delete stream target! " + e.getMessage(), e);
        }
        return target;
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
                case S3 -> {
                    final S3Manager s3Manager = new S3Manager(dataVolume.getVolume().getS3ClientConfig());
                    final Path tempDir = createTempPath(meta.getId());
                    source = S3Source.create(s3Manager, new S3PathHelper(), meta, meta.getTypeName(), tempDir);
                }
            }

            return source;
        } catch (final DataException e) {
            LOGGER.debug(e::getMessage, e);
            throw e;
        }
    }

    private void syncAttributes(final Meta meta, final Target target) {
        updateAttribute(target, MetaFields.ID, String.valueOf(meta.getId()));

        if (meta.getParentMetaId() != null) {
            updateAttribute(target, MetaFields.PARENT_ID,
                    String.valueOf(meta.getParentMetaId()));
        }

        updateAttribute(target, MetaFields.FEED, meta.getFeedName());
        updateAttribute(target, MetaFields.TYPE, meta.getTypeName());
        updateAttribute(target, MetaFields.CREATE_TIME, String.valueOf(meta.getCreateMs()));
        if (meta.getEffectiveMs() != null) {
            updateAttribute(target, MetaFields.EFFECTIVE_TIME, String.valueOf(meta.getEffectiveMs()));
        }
    }

    private void updateAttribute(final Target target, final AbstractField key, final String value) {
        if (!target.getAttributes().containsKey(key.getName())) {
            target.getAttributes().put(key.getName(), value);
        }
    }

    @Override
    public Map<String, String> getAttributes(final Meta meta) {
        try (final Source source = openSource(meta.getId(), true)) {
            return source != null
                    ? source.getAttributes()
                    : Collections.emptyMap();
        } catch (final IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
