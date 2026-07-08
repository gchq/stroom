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

import stroom.aws.s3.shared.S3ClientConfig;
import stroom.aws.s3.shared.S3Location;
import stroom.data.store.impl.fs.DataVolumeDao.DataVolume;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class DataVolumeService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DataVolumeService.class);

    private final DataVolumeDao dataVolumeDao;
    private final SecurityContext securityContext;
    private final FsOrphanedMetaDao fsOrphanedMetaDao;
    private final FsMetaS3LocationDao fsMetaS3LocationDao;

    @Inject
    DataVolumeService(final DataVolumeDao dataVolumeDao,
                      final SecurityContext securityContext,
                      final FsOrphanedMetaDao fsOrphanedMetaDao,
                      final FsMetaS3LocationDao fsMetaS3LocationDao) {
        this.dataVolumeDao = dataVolumeDao;
        this.securityContext = securityContext;
        this.fsOrphanedMetaDao = fsOrphanedMetaDao;
        this.fsMetaS3LocationDao = fsMetaS3LocationDao;
    }

    public ResultPage<DataVolume> find(final FindDataVolumeCriteria criteria) {
        if (!criteria.isValidCriteria()) {
            throw new IllegalArgumentException("Not enough criteria to run");
        }

        return securityContext.secureResult(() ->
                dataVolumeDao.find(criteria));
    }

    /**
     * Return the meta data volumes for a stream id.
     */
    public DataVolume findDataVolume(final long metaId) {
        return securityContext.secureResult(() ->
                dataVolumeDao.findDataVolume(metaId));
    }

    /**
     * Return the S3 location information for metaId
     */
    public S3LocationDataVolume findS3Locations(final long metaId) {
        return securityContext.secureResult(() ->
                fsMetaS3LocationDao.getS3LocationDataVolume(metaId));
    }

    public List<DataVolume> findDataVolumes(final Collection<Long> metaIds) {
        return securityContext.secureResult(() ->
                dataVolumeDao.findDataVolumes(metaIds));
    }

    public DataVolume createDataVolume(final long metaId, final FsVolume volume) {
        return securityContext.secureResult(() -> {
            return dataVolumeDao.createDataVolume(metaId, volume);
        });
    }

//    public Set<MetaS3Location> createS3Location(final long metaId,
//                                                 final S3Location s3Locations) {
//        return createS3Locations(metaId, Set.of(s3Locations));
//    }

    public S3LocationDataVolume createS3LocationDataVolume(final long metaId,
                                                           final FsVolume volume,
                                                           final Set<S3Location> s3Locations) {
        return securityContext.secureResult(() -> {
            validateS3Locations(volume, s3Locations);
            // This also creates the DataVolume
            final S3LocationDataVolume s3LocationDataVolume = fsMetaS3LocationDao.create(metaId, volume, s3Locations);

            LOGGER.debug("createS3LocationDataVolume() - metaId: {}, volume: {}, s3Locations: {}",
                    metaId, volume, s3Locations);

            return s3LocationDataVolume;
        });
    }

    private void validateS3Locations(final FsVolume fsVolume, final Set<S3Location> s3Locations) {
        if (NullSafe.hasItems(s3Locations)) {
            final S3ClientConfig s3ClientConfig = Objects.requireNonNull(fsVolume.getS3ClientConfig());
            final String bucketName = s3ClientConfig.getBucketName();
            final String region = s3ClientConfig.getRegion();
            s3Locations.forEach(s3Location -> {
                if (!Objects.equals(s3Location.regionName(), region)
                    || !Objects.equals(s3Location.bucketName(), bucketName)) {
                    throw new IllegalArgumentException(LogUtil.message(
                            "s3Location {} doesn't match region {} and bucketName {}",
                            s3Location, region, bucketName));
                }
            });
        }
    }

    public long getOrphanedMetaTrackerValue() {
        return securityContext.secureResult(fsOrphanedMetaDao::getMetaIdTrackerValue);
    }

    void updateOrphanedMetaTracker(final long metaId) {
        securityContext.secure(() ->
                fsOrphanedMetaDao.updateMetaIdTracker(metaId));
    }

}
