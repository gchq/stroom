/*
 * Copyright 2026 Crown Copyright
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

import stroom.aws.s3.impl.S3Manager;
import stroom.aws.s3.shared.S3ClientConfig;
import stroom.data.store.impl.fs.DataVolumeDao.DataVolume;
import stroom.meta.api.MetaService;
import stroom.meta.shared.Meta;
import stroom.meta.shared.SimpleMeta;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.time.TimeBasis;

import jakarta.inject.Inject;

import java.util.Objects;
import java.util.function.Function;

/// Deletes a stream stored in S3 and removes its local cache.
class S3StreamDeleter {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(S3StreamDeleter.class);
    private static final TimeBasis TIME_BASIS = TimeBasis.META_CREATION_TIME;

    private final MetaService metaService;
    private final S3Store s3Store;
    private final Function<S3ClientConfig, S3Manager> s3ManagerFactory;

    @Inject
    S3StreamDeleter(final MetaService metaService,
                    final S3Store s3Store,
                    final PathCreator pathCreator) {
        this(metaService, s3Store, s3ClientConfig -> new S3Manager(pathCreator, s3ClientConfig));
    }

    S3StreamDeleter(final MetaService metaService,
                    final S3Store s3Store,
                    final Function<S3ClientConfig, S3Manager> s3ManagerFactory) {
        this.metaService = Objects.requireNonNull(metaService);
        this.s3Store = Objects.requireNonNull(s3Store);
        this.s3ManagerFactory = Objects.requireNonNull(s3ManagerFactory);
    }

    /// Deletes the S3 object and local cache for a stream.
    ///
    /// @param dataVolume the data volume containing the S3 configuration
    /// @param simpleMeta the stream to delete
    /// @return {@code true} when the S3 object and local cache were deleted
    boolean delete(final DataVolume dataVolume, final SimpleMeta simpleMeta) {
        Objects.requireNonNull(dataVolume);
        Objects.requireNonNull(simpleMeta);
        LOGGER.debug("delete() - simpleMeta: {}, dataVolume: {}", simpleMeta, dataVolume);

        try {
            final Meta meta;
            if (simpleMeta instanceof Meta) {
                meta = (Meta) simpleMeta;
            } else {
                meta = metaService.getMeta(simpleMeta.getId(), true);
                if (meta == null) {
                    LOGGER.warn("Unable to find metadata for S3 stream {}", simpleMeta.getId());
                    return false;
                }
            }

            final S3ClientConfig s3ClientConfig = dataVolume.getVolume().getS3ClientConfig();
            final S3Manager s3Manager = s3ManagerFactory.apply(s3ClientConfig);
            s3Manager.delete(meta, TIME_BASIS);

            return s3Store.delete(simpleMeta.getId());
        } catch (final RuntimeException e) {
            LOGGER.error(() -> "Error deleting S3 stream " + simpleMeta.getId(), e);
            return false;
        }
    }
}
