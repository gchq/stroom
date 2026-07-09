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

package stroom.data.store.api;


import stroom.data.store.impl.fs.shared.FsVolume;

import org.jspecify.annotations.NullMarked;

import java.util.Optional;

public interface S3VolumeService {

    /**
     * Gets a {@link FsVolume} matching the supplied fsVolumeType, regionName, bucketName.
     */
    @NullMarked
    Optional<FsVolume> getS3Volume(final String regionName,
                                   final String bucketName);

    @NullMarked
    default Optional<FsVolume> getS3Volume(final stroom.aws.s3.shared.S3Location s3Location) {
        return getS3Volume(s3Location.regionName(), s3Location.bucketName());
    }
}
