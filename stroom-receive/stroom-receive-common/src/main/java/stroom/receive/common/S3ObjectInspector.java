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

package stroom.receive.common;


import stroom.aws.s3.client.S3ClientHelper;
import stroom.aws.s3.client.S3ClientHelper.S3ObjectInfo;
import stroom.aws.s3.client.S3ClientPool;
import stroom.aws.s3.client.S3MetaFieldsMapper;
import stroom.aws.s3.shared.S3ClientConfig;
import stroom.aws.s3.shared.S3Location;
import stroom.data.store.api.S3VolumeService;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.meta.api.AttributeMap;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.string.CIKey;

import jakarta.inject.Inject;

import java.util.Objects;
import java.util.Optional;

public class S3ObjectInspector {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(S3ObjectInspector.class);

    private final S3ClientPool s3ClientPool;
    private final S3VolumeService s3VolumeService;
    private final S3MetaFieldsMapper s3MetaFieldsMapper;

    @Inject
    public S3ObjectInspector(final S3ClientPool s3ClientPool,
                             final S3VolumeService s3VolumeService,
                             final S3MetaFieldsMapper s3MetaFieldsMapper) {
        this.s3ClientPool = s3ClientPool;
        this.s3VolumeService = s3VolumeService;
        this.s3MetaFieldsMapper = s3MetaFieldsMapper;
    }

    public void addS3MetaAttributes(final S3Location s3Location,
                                    final AttributeMap attributeMap) {
        LOGGER.debug("addS3MetaAttributes() - s3Location: {}, attributeMap: {}", s3Location, attributeMap);
        Objects.requireNonNull(s3Location);
        Objects.requireNonNull(attributeMap);

        final Optional<FsVolume> optS3Volume = s3VolumeService.getS3Volume(
                s3Location.regionName(),
                s3Location.bucketName());

        optS3Volume.ifPresentOrElse(
                s3Volume -> {
                    final S3ClientConfig s3ClientConfig = s3Volume.getS3ClientConfig();
                    final S3ClientHelper s3ClientHelper = new S3ClientHelper(s3ClientConfig, s3ClientPool);
                    final S3ObjectInfo objectInfo = s3ClientHelper.getObjectInfo(
                            s3Location.bucketName(),
                            s3Location.key());

                    // Map any known keys back to their original form as some of our keys may not fit the
                    // key restrictions.
                    objectInfo.s3Metadata().forEach((k, v) -> {
                        final CIKey originalKey = s3MetaFieldsMapper.getOriginalKey(k)
                                .orElse(k);
                        attributeMap.put(originalKey.get(), v);
                    });
                    LOGGER.debug("addS3MetaAttributes() - s3Location: {}, modified attributeMap: {}",
                            s3Location, attributeMap);
                },
                () -> LOGGER.warn("No S3 volume found matching region '{}' and bucket '{}'. " +
                                  "Unable to fetch S3 metadata for key '{}'",
                        s3Location.regionName(), s3Location.bucketName(), s3Location.key()));
    }
}
