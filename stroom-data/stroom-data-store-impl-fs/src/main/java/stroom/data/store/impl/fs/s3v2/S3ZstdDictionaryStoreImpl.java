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


import stroom.aws.s3.impl.S3Manager;
import stroom.aws.s3.impl.S3ManagerFactory;
import stroom.aws.s3.shared.S3ClientConfig;
import stroom.data.store.impl.fs.DataVolumeDao.DataVolume;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

// TODO If we add zstd support for the local filesystem volumes we ought to have a ZstdDictionaryStore
//  impl that delegates to a ZstdDictionaryStore based on FsVolumeType
class S3ZstdDictionaryStoreImpl implements ZstdDictionaryStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(S3ZstdDictionaryStoreImpl.class);

    private final S3StreamTypeExtensions s3StreamTypeExtensions;
    private final S3ManagerFactory s3ManagerFactory;

    @Inject
    public S3ZstdDictionaryStoreImpl(final S3StreamTypeExtensions s3StreamTypeExtensions,
                                     final S3ManagerFactory s3ManagerFactory) {
        this.s3StreamTypeExtensions = s3StreamTypeExtensions;
        this.s3ManagerFactory = s3ManagerFactory;
    }

    @Override
    public Optional<ZstdDictionary> getZstdDictionary(final String uuid,
                                                      final DataVolume dataVolume) {
        Objects.requireNonNull(uuid);
        final S3ClientConfig s3ClientConfig = NullSafe.get(dataVolume,
                DataVolume::volume,
                FsVolume::getS3ClientConfig);
        Objects.requireNonNull(s3ClientConfig, "Expecting s3ClientConfig to be non null");
        final S3Manager s3Manager = s3ManagerFactory.createS3Manager(s3ClientConfig);
        final Optional<ZstdDictionary> optDict = Optional.ofNullable(fetchByUuid(uuid,
                s3Manager,
                s3ClientConfig));

        LOGGER.debug("getZstdDictionary() - uuid: {}, dataVolume: {}, optDict: {}", uuid, dataVolume, optDict);
        return optDict;
    }

    @Override
    public Optional<ZstdDictionary> getZstdDictionary(final UUID uuid,
                                                      final DataVolume dataVolume) {
        Objects.requireNonNull(uuid);
        return getZstdDictionary(uuid.toString(), dataVolume);
    }

    private ZstdDictionary fetchByUuid(final String dictionaryUuid,
                                       final S3Manager s3Manager,
                                       final S3ClientConfig s3ClientConfig) {

        // bucketName is validated in FsVolumeService to ensure it is static
        final String bucket = s3ClientConfig.getBucketName();
        final String key = s3StreamTypeExtensions.getDictkey(dictionaryUuid);
        try {
            LOGGER.debug("fetchByUuid() - dictionaryUuid: {}, bucketName: {}, key: {}",
                    dictionaryUuid, bucket, key);

            final ResponseInputStream<GetObjectResponse> responseInputStream = s3Manager.getObject(bucket, key);

            final byte[] dictionaryBytes = responseInputStream.readAllBytes();
            final int len = NullSafe.getOrElse(dictionaryBytes, arr -> arr.length, 0);
            if (len == 0) {
                throw new RuntimeException("Zstandard Dictionary response is empty");
            }
            LOGGER.error(() -> LogUtil.message(
                    "fetchZstdDictionary() - Dictionary found for bucket: {}, key: {}, len: {}", bucket, key, len));
            return new ZstdDictionary(dictionaryUuid, dictionaryBytes);
        } catch (final NoSuchKeyException e) {
            LOGGER.error("fetchZstdDictionary() - Dictionary not found for bucket: {}, key: {} - {}",
                    bucket, key, LogUtil.exceptionMessage(e), e);
            return null;
        } catch (final IOException e) {
            throw new UncheckedIOException(LogUtil.message("Error fetching dictionary - bucket: {}, key: {} - {}",
                    bucket, key, LogUtil.exceptionMessage(e)), e);
        }
    }
}
