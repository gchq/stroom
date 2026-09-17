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


import stroom.aws.s3.shared.S3ClientConfig;
import stroom.cache.api.TemplateCache;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.data.store.impl.fs.shared.FsVolumeType;
import stroom.data.store.impl.fs.shared.ValidationResult;
import stroom.util.json.JsonUtil;
import stroom.util.json.JsonUtil.JsonDeserialisationException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Severity;

import java.util.List;
import java.util.Objects;

public abstract class AbstractS3StreamStore implements StreamStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractS3StreamStore.class);

    private final TemplateCache templateCache;

    protected AbstractS3StreamStore(final TemplateCache templateCache) {
        this.templateCache = templateCache;
    }

    @Override
    public ValidationResult validateVolume(final FsVolume volume,
                                           final List<FsVolume> otherVolumesInGroup,
                                           final List<FsVolume> allOtherVolumes) {

        Objects.requireNonNull(volume);
        ValidationResult validationResult = ValidationResult.ok();

        validationResult = validationResult.validate(
                Severity.ERROR,
                "Path is not supported for an S3 based Volume",
                () -> NullSafe.isBlankString(volume.getPath()));

        validationResult = validationResult.validate(
                Severity.ERROR,
                "Limit is not supported for an S3 based Volume",
                () -> volume.getByteLimit() == null);

        validationResult = validationResult.errorIfBlank(
                "S3 Client Configuration must be provided as a JSON object",
                volume.getS3ClientConfigData());

        if (validationResult.isOk()) {
            S3ClientConfig s3ClientConfig = null;
            try {
                s3ClientConfig = readS3ClientConfig(volume);
            } catch (final JsonDeserialisationException e) {
                validationResult = ValidationResult.error(e.getMessage()
                                                          + " - JSON:\n"
                                                          + e.getJson());
            } catch (final Exception e) {
                validationResult = ValidationResult.error("Error deserialising S3 configuration - "
                                                          + e.getMessage() + " - JSON:\n"
                                                          + volume.getS3ClientConfigData());
            }

            if (s3ClientConfig != null) {
                final S3ClientConfig finalS3ClientConfig = s3ClientConfig;
                // Check for other vols in the group with the same S3 config
                validationResult = validationResult.validate(() ->
                        validateForDupS3Config(finalS3ClientConfig, allOtherVolumes));

                // Let the subclasses do their own custom validation
                validationResult = validationResult.validate(() ->
                        validateS3Config(finalS3ClientConfig));

                validationResult = validationResult.validate(() ->
                        validateForDupRegionAndBucket(finalS3ClientConfig, allOtherVolumes));
            }
        }

        return validationResult;
    }


    private ValidationResult validateForDupS3Config(final S3ClientConfig s3ClientConfig,
                                                    final List<FsVolume> allOtherVolumes) {
        // Use s3ClientConfig has that has just been deserialised from the json in volume
        return NullSafe.stream(allOtherVolumes)
                .filter(otherVol ->
                        Objects.equals(otherVol.getS3ClientConfig(), s3ClientConfig))
                .findAny()
                .map(otherVol -> {
                    LOGGER.debug(() -> LogUtil.message(
                            "s3ClientConfig: {}, otherVol.s3ClientConfig: {}",
                            s3ClientConfig, otherVol.getS3ClientConfig()));
                    return ValidationResult.error("Duplicate S3 configuration found. The S3 configuration of this " +
                                                  "volume is identical to another volume in the group.");
                })
                .orElse(ValidationResult.ok());
    }

    private ValidationResult validateForDupRegionAndBucket(final S3ClientConfig s3ClientConfig,
                                                           final List<FsVolume> allOtherVolumes) {
        // Use s3ClientConfig has that has just been deserialised from the json in volume
        return NullSafe.stream(allOtherVolumes)
                .filter(otherVol -> FsVolumeType.isS3VolumeType(otherVol.getVolumeType()))
                .filter(otherVol -> otherVol.getS3ClientConfig() != null)
                .filter(otherVol ->
                        Objects.equals(otherVol.getS3ClientConfig().getRegion(), s3ClientConfig.getRegion())
                        && Objects.equals(otherVol.getS3ClientConfig().getBucketName(), s3ClientConfig.getBucketName()))
                .findAny()
                .map(otherVol -> {
                    LOGGER.debug(() -> LogUtil.message(
                            "s3ClientConfig: {}, otherVol.s3ClientConfig: {}",
                            s3ClientConfig, otherVol.getS3ClientConfig()));
                    return ValidationResult.error("An exising volume has the same S3 region and bucketName. " +
                                                  "The region and bucketName must be unique across all volumes.");
                })
                .orElse(ValidationResult.ok());
    }

    /// Any implementation-specific validation of the S3ClientConfig object.
    /// Will only be called if there are no prior validation errors.
    protected ValidationResult validateS3Config(final S3ClientConfig s3ClientConfig) {
        ValidationResult validationResult = ValidationResult.ok();

        // Region + bucket are probably common to all S3 volumes, but this can be overridden

        validationResult = validationResult.errorIfBlank(
                "The 'region' field must be provided in the S3 JSON configuration",
                s3ClientConfig.getRegion());

        validationResult = validationResult.errorIfBlank(
                "The 'bucketName' field must be provided in the S3 JSON configuration",
                s3ClientConfig.getBucketName());

        return validationResult;
    }

    protected S3ClientConfig readS3ClientConfig(final FsVolume fileVolume) {
        final String s3ClientConfigData = fileVolume.getS3ClientConfigData();
        if (NullSafe.isNonBlankString(s3ClientConfigData)) {
            return JsonUtil
                    .readValue(s3ClientConfigData, S3ClientConfig.class);
        } else {
            return null;
        }
    }

    protected TemplateCache getTemplateCache() {
        return templateCache;
    }
}
