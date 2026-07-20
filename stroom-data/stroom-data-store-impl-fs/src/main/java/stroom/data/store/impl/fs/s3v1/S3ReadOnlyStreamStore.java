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

package stroom.data.store.impl.fs.s3v1;


import stroom.aws.s3.shared.S3Location;
import stroom.cache.api.TemplateCache;
import stroom.data.store.api.DataException;
import stroom.data.store.api.Source;
import stroom.data.store.api.Target;
import stroom.data.store.impl.fs.AbstractS3StreamStore;
import stroom.data.store.impl.fs.DataVolumeDao.DataVolume;
import stroom.data.store.impl.fs.DataVolumeService;
import stroom.data.store.impl.fs.FsMetaS3LocationDao;
import stroom.data.store.impl.fs.PhysicalDeleteExecutor.Progress;
import stroom.data.store.impl.fs.PhysicalDeleteOutcome;
import stroom.data.store.impl.fs.S3LocationDataVolume;
import stroom.data.store.impl.fs.shared.FsVolumeType;
import stroom.meta.shared.Meta;
import stroom.meta.shared.SimpleMeta;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class S3ReadOnlyStreamStore extends AbstractS3StreamStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(S3ReadOnlyStreamStore.class);

    // Delegates everything that is a read only to this
    private final S3StreamStore streamStore;
    private final DataVolumeService dataVolumeService;
    private final FsMetaS3LocationDao fsMetaS3LocationDao;

    @Inject
    public S3ReadOnlyStreamStore(final S3StreamStore streamStore,
                                 final DataVolumeService dataVolumeService,
                                 final TemplateCache templateCache,
                                 final FsMetaS3LocationDao fsMetaS3LocationDao) {
        super(templateCache);
        this.streamStore = streamStore;
        this.dataVolumeService = dataVolumeService;
        this.fsMetaS3LocationDao = fsMetaS3LocationDao;
    }

    @Override
    public Target openTarget(final Meta meta, final DataVolume dataVolume) throws DataException {
        throw new UnsupportedOperationException(LogUtil.message(
                "openTarget not supported on a read-only stream store, meta: {}, dataVolume: {}",
                meta, dataVolume));
    }

//    @Override
//    public void physicallyDelete(final Collection<DataVolume> dataVolumes) {
//        LOGGER.debug(() -> LogUtil.message(
//                "physicallyDelete() - No-op - dataVolumes: {}, dataVolume: {}, progress: {}",
//                LogUtil.getSample(dataVolumes, 10)));
//    }

    @Override
    public PhysicalDeleteOutcome physicallyDelete(final SimpleMeta simpleMeta,
                                                  final DataVolume dataVolume,
                                                  final Progress progress) {
        Objects.requireNonNull(simpleMeta);
        LOGGER.debug("physicallyDelete() - No-op - simpleMeta: {}, dataVolume: {}, progress: {}",
                simpleMeta, dataVolume, progress);

        // S3 is not under our control, so we don't touch the files on there.

        // We do need to remove the S3 location records in the DB though
        fsMetaS3LocationDao.delete(List.of(simpleMeta.getId()));

        return new ReadOnlyS3PhysicalDeleteOutcome(true, dataVolume, simpleMeta);
    }

    @Override
    public void clean(final List<PhysicalDeleteOutcome> ignoredPhysicalDeleteOutcomes,
                      final Instant deleteThreshold,
                      final Progress progress) {
        LOGGER.debug("clean() - No-op - ignoredPhysicalDeleteOutcomes: {}, deleteThreshold: {}, progress: {}",
                ignoredPhysicalDeleteOutcomes, deleteThreshold, progress);
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public Source openSource(final Meta meta, final DataVolume dataVolume) throws DataException {
        LOGGER.debug("openSource() - meta: {}, dataVolume: {}", meta, dataVolume);
        Objects.requireNonNull(meta);
        Objects.requireNonNull(dataVolume);

        final S3LocationDataVolume s3LocationDataVolume = dataVolumeService.findS3Locations(meta.getId());
        final Set<S3Location> s3Locations = s3LocationDataVolume.s3Locations();
        if (NullSafe.hasOneItem(s3Locations)) {
            final S3Location s3Location = s3Locations.stream()
                    .findAny()
                    .orElseThrow();
            return streamStore.openSource(meta, dataVolume, s3Location, FilePadStyle.TEN_DIGITS);
        } else {
            throw new IllegalStateException(LogUtil.message(
                    "Only one s3Location is supported, found {}, dataVolume: {}, s3Locations: {}",
                    NullSafe.size(s3Locations), dataVolume, s3Locations));
        }
    }

    @Override
    public FsVolumeType getVolumeType() {
        return FsVolumeType.S3_V1_READ_ONLY;
    }


    // --------------------------------------------------------------------------------


    record ReadOnlyS3PhysicalDeleteOutcome(
            boolean wasSuccessful,
            DataVolume dataVolume,
            SimpleMeta simpleMeta) implements PhysicalDeleteOutcome {

    }
}
