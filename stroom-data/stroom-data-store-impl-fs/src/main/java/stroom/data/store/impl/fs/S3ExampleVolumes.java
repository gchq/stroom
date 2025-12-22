/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.aws.s3.shared.AwsBasicCredentials;
import stroom.aws.s3.shared.S3ClientConfig;
import stroom.data.store.api.FsVolumeGroupService;
import stroom.data.store.impl.fs.shared.FindFsVolumeCriteria;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.data.store.impl.fs.shared.FsVolumeGroup;
import stroom.data.store.impl.fs.shared.FsVolumeType;
import stroom.util.json.JsonUtil;

import jakarta.inject.Inject;

import java.util.Objects;
import java.util.Optional;

public class S3ExampleVolumes {

    private final FsVolumeGroupService fsVolumeGroupService;
    private final FsVolumeService fsVolumeService;

    @Inject
    public S3ExampleVolumes(final FsVolumeGroupService fsVolumeGroupService,
                            final FsVolumeService fsVolumeService) {
        this.fsVolumeGroupService = fsVolumeGroupService;
        this.fsVolumeService = fsVolumeService;
    }

    public void addS3ExampleVolume() {
        final FsVolumeGroup s3VolumeGroup = fsVolumeGroupService.getOrCreate("S3");
        final Optional<FsVolume> existing = fsVolumeService
                .find(FindFsVolumeCriteria.matchAll())
                .stream()
                .filter(vol -> Objects.equals(vol.getVolumeGroupId(), s3VolumeGroup.getId()))
                .findAny();
        if (existing.isEmpty()) {
            final S3ClientConfig s3ClientConfig = S3ClientConfig
                    .builder()
                    .credentials(AwsBasicCredentials
                            .builder()
                            .accessKeyId("AKIAIOSFODNN7EXAMPLE")
                            .secretAccessKey("wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY")
                            .build())
                    .region("us-west-2")
                    .endpointOverride("http://localhost:9444")
                    .build();

            final FsVolume volume = new FsVolume();
            volume.setVolumeGroupId(s3VolumeGroup.getId());
            volume.setVolumeType(FsVolumeType.S3);
            volume.setS3ClientConfig(s3ClientConfig);
            volume.setS3ClientConfigData(JsonUtil.writeValueAsString(s3ClientConfig));
            volume.setPath("s3");
            fsVolumeService.create(volume);
        }

//        // Make all existing volumes S3 volumes.
//        final ResultPage<FsVolume> volumes = fsVolumeService.find(FindFsVolumeCriteria.matchAll());
//        volumes.forEach(volume -> {
//            volume.setVolumeType(FsVolumeType.S3);
//            volume.setS3ClientConfig(s3ClientConfig);
//            volume.setS3ClientConfigData(JsonUtil.writeValueAsString(s3ClientConfig));
//            volume.setPath("test");
//            fsVolumeService.update(volume);
//        });
    }
}
