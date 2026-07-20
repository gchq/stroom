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

package stroom.data.store.impl.fs.db;


import stroom.aws.s3.shared.S3Location;
import stroom.data.store.impl.fs.DataVolumeDao.DataVolume;
import stroom.data.store.impl.fs.FsMetaS3LocationDao;
import stroom.data.store.impl.fs.S3LocationDataVolume;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.db.util.JooqUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import org.jooq.Record5;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static stroom.data.store.impl.fs.db.jooq.tables.FsMetaS3Location.FS_META_S3_LOCATION;

public class FsMetaS3LocationDaoImpl implements FsMetaS3LocationDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FsMetaS3LocationDaoImpl.class);

    private final FsDataStoreDbConnProvider fsDataStoreDbConnProvider;
    private final DataVolumeDaoImpl dataVolumeDaoImpl;

    @Inject
    FsMetaS3LocationDaoImpl(final FsDataStoreDbConnProvider fsDataStoreDbConnProvider,
                            final DataVolumeDaoImpl dataVolumeDaoImpl) {
        this.fsDataStoreDbConnProvider = fsDataStoreDbConnProvider;
        this.dataVolumeDaoImpl = dataVolumeDaoImpl;
    }

    @Override
    public S3LocationDataVolume create(final long metaId,
                                       final FsVolume fsVolume,
                                       final Set<S3Location> s3Locations) {

        Objects.requireNonNull(fsVolume);
        return JooqUtil.transactionResult(fsDataStoreDbConnProvider, txnContext -> {
            // Create the link to the volume
            final DataVolume dataVolume = dataVolumeDaoImpl.createDataVolume(txnContext, metaId, fsVolume);

            if (NullSafe.hasItems(s3Locations)) {
                final List<S3Location> s3LocationList = List.copyOf(s3Locations);
//                JooqUtil.contextResult(fsDataStoreDbConnProvider, context -> {
                @SuppressWarnings("VariableTypeCanBeExplicit")
                var insert = txnContext.insertInto(
                        FS_META_S3_LOCATION,
                        FS_META_S3_LOCATION.META_ID,
                        FS_META_S3_LOCATION.S3_REGION,
                        FS_META_S3_LOCATION.S3_BUCKET,
                        FS_META_S3_LOCATION.S3_KEY);

                for (final S3Location s3Location : s3LocationList) {
                    insert = insert.values(
                            metaId,
                            s3Location.getRegionName(),
                            s3Location.getBucketName(),
                            s3Location.getKey());
                }

                insert.execute();
//                });
                return new S3LocationDataVolume(dataVolume, s3Locations);
            } else {
                return new S3LocationDataVolume(dataVolume, Collections.emptySet());
            }
        });
    }

    @Override
    @Nullable
    public S3LocationDataVolume getS3LocationDataVolume(final long metaId) {
        final DataVolume dataVolume = dataVolumeDaoImpl.findDataVolume(metaId);
        final S3LocationDataVolume s3LocationDataVolume;
        if (dataVolume != null) {
            final Set<S3Location> s3Locations = getS3LocationDataVolume(dataVolume);
            s3LocationDataVolume = new S3LocationDataVolume(dataVolume, s3Locations);
        } else {
            s3LocationDataVolume = null;
        }
        LOGGER.debug("getS3LocationDataVolume() - metaId: {}, s3LocationDataVolume: {}",
                metaId, s3LocationDataVolume);
        return s3LocationDataVolume;
    }

    @Override
    public @Nullable Set<S3Location> getS3LocationDataVolume(final DataVolume dataVolume) {
        Objects.requireNonNull(dataVolume);
        final List<S3Location> list = JooqUtil.contextResult(
                        fsDataStoreDbConnProvider, context -> context
                                .select(FS_META_S3_LOCATION.ID,
                                        FS_META_S3_LOCATION.META_ID,
                                        FS_META_S3_LOCATION.S3_REGION,
                                        FS_META_S3_LOCATION.S3_BUCKET,
                                        FS_META_S3_LOCATION.S3_KEY)
                                .from(FS_META_S3_LOCATION)
                                .where(FS_META_S3_LOCATION.META_ID.eq(dataVolume.metaId()))
                                .fetch())
                .map(this::mapRecordToS3Location);
        final Set<S3Location> s3Locations = NullSafe.asSet(list);
        LOGGER.debug("getS3Locations() - dataVolume: {}, s3Locations: {}", dataVolume, s3Locations);
        return s3Locations;
    }

    @Override
    public int delete(final Collection<Long> metaIds) {
        final int count = JooqUtil.contextResult(fsDataStoreDbConnProvider, context -> context
                .delete(FS_META_S3_LOCATION)
                .where(FS_META_S3_LOCATION.META_ID.in(metaIds))
                .execute());
        LOGGER.debug(() -> LogUtil.message("delete () - metaIds: {}, count: {}",
                LogUtil.getSample(metaIds, 10), count));
        return count;
    }

//    private S3LocationDataVolume mapRecordToMetaS3Location(final Record5<Long, Long, String, String, String> rec) {
//        Objects.requireNonNull(rec);
//        final S3LocationDataVolume s3LocationDataVolume = new S3LocationDataVolume(
//                rec.get(FS_META_S3_LOCATION.META_ID), rec.get(FS_META_S3_LOCATION.ID),
//                new S3Location(
//                        rec.get(FS_META_S3_LOCATION.S3_REGION),
//                        rec.get(FS_META_S3_LOCATION.S3_BUCKET),
//                        rec.get(FS_META_S3_LOCATION.S3_KEY)));
//        LOGGER.debug("mapRecordToMetaS3Location() - rec: {}, metaS3Location: {}", rec, s3LocationDataVolume);
//        return s3LocationDataVolume;
//    }

    private S3Location mapRecordToS3Location(final Record5<Long, Long, String, String, String> rec) {
        Objects.requireNonNull(rec);
        final S3Location s3Location = new S3Location(
                rec.get(FS_META_S3_LOCATION.S3_REGION),
                rec.get(FS_META_S3_LOCATION.S3_BUCKET),
                rec.get(FS_META_S3_LOCATION.S3_KEY));
        LOGGER.debug("mapRecordToMetaS3Location() - rec: {}, s3Location: {}", rec, s3Location);
        return s3Location;
    }
}
