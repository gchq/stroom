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


import stroom.data.store.api.DataException;
import stroom.data.store.api.Source;
import stroom.data.store.api.Target;
import stroom.data.store.impl.fs.DataVolumeDao.DataVolume;
import stroom.data.store.impl.fs.standard.FsPathHelper;
import stroom.data.store.impl.fs.standard.FsSource;
import stroom.data.store.impl.fs.standard.FsTarget;
import stroom.meta.api.MetaService;
import stroom.meta.shared.Meta;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.NonNull;

import java.nio.file.Path;
import java.util.Collection;

/**
 * A file system stream store.
 * Stores streams in the stream store indexed by some metadata.
 */
@Singleton
class FsStore implements StreamStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FsStore.class);

    private final FsPathHelper fileSystemStreamPathHelper;
    private final MetaService metaService;
    //    private final FsVolumeService volumeService;
//    private final DataVolumeService dataVolumeService;
    private final PathCreator pathCreator;
//    private final S3Store s3Store;
//    private final S3ZstdStore s3ZstdStore;

    @Inject
    FsStore(final FsPathHelper fileSystemStreamPathHelper,
            final MetaService metaService,
            final PathCreator pathCreator) {

        this.fileSystemStreamPathHelper = fileSystemStreamPathHelper;
        this.metaService = metaService;
//        this.volumeService = volumeService;
//        this.dataVolumeService = dataVolumeService;
        this.pathCreator = pathCreator;
//        this.s3Store = s3Store;
//        this.s3ZstdStore = s3ZstdStore;
    }

//    @Override
//    public Target openTarget(final MetaProperties metaProperties, final String volumeGroup) {
//        LOGGER.debug("openTarget() - volumeGroup: {}, metaProperties: {}", volumeGroup, metaProperties);
//
//        final FsVolume volume = volumeService.getVolume(volumeGroup);
//        if (volume == null) {
//            throw new DataException("""
//                    Failed to get lock as no writable volumes. This may be because there are no active \
//                    volumes configured or the active volumes (or the filesystem(s) they sit on) are full \
//                    or near full.""");
//        }
//
//        // First time call (no file yet exists)
//        final Meta meta = metaService.create(metaProperties);
//        final DataVolume dataVolume = dataVolumeService.createDataVolume(meta.getId(), volume);
//        final FsVolumeType volumeType = dataVolume.volume().getVolumeType();
//        final Target target = switch (volumeType) {
//            case STANDARD -> createFsTarget(dataVolume, meta);
//            case S3_V1 -> s3Store.getTarget(dataVolume, meta);
//            case S3_V2 -> s3ZstdStore.getTarget(dataVolume, meta);
//            case null -> throw new UnsupportedOperationException(LogUtil.message(
//                    "Null volume type for metaId: {}, volumeId {}",
//                    dataVolume.metaId(), dataVolume.volume().getId()));
//        };
//        LOGGER.debug(() -> LogUtil.message("openTarget() - returning target: {}, volumeId: {}, meta: {}",
//                target.getClass(), dataVolume.volume().getId(), meta));
//        return target;
//    }

    private @NonNull Target createFsTarget(final DataVolume dataVolume, final Meta meta) {
        final Path volumePath = pathCreator.toAppPath(dataVolume.volume().getPath());
        final FsTarget fsTarget = FsTarget.create(
                metaService,
                fileSystemStreamPathHelper,
                meta,
                volumePath);
        // Force Creation of the files
        fsTarget.getOutputStream();
        return fsTarget;
    }

//    @Override
//    public void logicallyDeleteTarget(final Target target) {
//        LOGGER.debug(() -> LogUtil.message("deleteTarget() - target: {}", LogUtil.typedValue(target)));
//        // Make sure the stream is closed.
//        try {
//            target.logicallyDelete();
//        } catch (final RuntimeException e) {
//            LOGGER.error(() -> "Unable to delete stream target! " + e.getMessage(), e);
//        }
//    }

//    /**
//     * <p>
//     * Open a existing stream source.
//     * </p>
//     *
//     * @param streamId  The stream id to open a stream source for.
//     * @param anyStatus Used to specify if this method will return stream sources that
//     *                  are logically deleted or locked. If false only unlocked stream
//     *                  sources will be returned, null otherwise.
//     * @return The loaded stream source if it exists (has not been physically
//     * deleted) else null. Also returns null if one exists but is
//     * logically deleted or locked unless <code>anyStatus</code> is
//     * true.
//     * @throws DataException Could be thrown if no volume
//     */
//    @Override
//    public Source openSource(final long streamId, final boolean anyStatus) throws DataException {
//        try {
//            LOGGER.debug("openSource() - streamId: {}", streamId);
//
//            final Meta meta = metaService.getMeta(streamId, anyStatus);
//            if (meta == null) {
//                if (anyStatus) {
//                    throw new DataException("Unable to find meta data for id=" + streamId + " with any status");
//                } else {
//                    throw new DataException("Unable to find meta data for id=" + streamId + " with valid status");
//                }
//            }
//
//            final DataVolume dataVolume = dataVolumeService.findDataVolume(meta.getId());
//            if (dataVolume == null) {
//                throw new DataException("Unable to find any volume for " + meta);
//            }
//
//            final FsVolume volume = dataVolume.volume();
//            final FsVolumeType volumeType = volume.getVolumeType();
//            final Source source = switch (volumeType) {
//                case STANDARD -> {
//                    final Path volumePath = pathCreator.toAppPath(dataVolume.volume().getPath());
//                    yield FsSource.create(fileSystemStreamPathHelper, meta, volumePath, meta.getTypeName());
//                }
//                case S3_V1 -> s3Store.getSource(dataVolume, meta);
//                case S3_V2 -> s3ZstdStore.getSource(dataVolume, meta);
//                case null -> throw new UnsupportedOperationException("Null volume type for metaId: " + meta.getId());
//            };
//            LOGGER.debug(() -> LogUtil.message("openSource() - returning target: {}, volumeId: {}, meta: {}",
//                    source.getClass(), dataVolume.volume().getId(), meta));
//            return source;
//        } catch (final DataException e) {
//            LOGGER.debug(e::getMessage, e);
//            throw e;
//        }
//    }

    @Override
    public Target openTarget(final Meta meta, final DataVolume dataVolume) throws DataException {
        final Path volumePath = pathCreator.toAppPath(dataVolume.volume().getPath());
        final FsTarget fsTarget = FsTarget.create(
                metaService,
                fileSystemStreamPathHelper,
                meta,
                volumePath);
        // Force Creation of the files
        fsTarget.getOutputStream();
        return fsTarget;
    }

    @Override
    public void physicallyDelete(final Collection<DataVolume> dataVolumes) {
        // TODO Refactor code from PhysicalDeleteExecutor
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public Source openSource(final Meta meta, final DataVolume dataVolume) throws DataException {
        final Path volumePath = pathCreator.toAppPath(dataVolume.volume().getPath());
        final FsSource fsSource = FsSource.create(fileSystemStreamPathHelper, meta, volumePath, meta.getTypeName());
        LOGGER.debug("openSource() - meta: {}, dataVolume: {}, fsSource: {}", meta, dataVolume, fsSource);
        return fsSource;
    }
}
