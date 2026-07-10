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


import stroom.aws.s3.shared.S3ClientConfigService;
import stroom.data.store.api.S3VolumeService;
import stroom.data.store.impl.fs.shared.FindFsVolumeCriteria;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.data.store.impl.fs.shared.ValidationResult;
import stroom.util.entityevent.EntityEvent;
import stroom.util.shared.Clearable;
import stroom.util.shared.Flushable;
import stroom.util.shared.ResultPage;
import stroom.util.sysinfo.HasSystemInfo;

public interface FsVolumeService
        extends S3VolumeService, S3ClientConfigService, EntityEvent.Handler, Clearable, Flushable, HasSystemInfo {

    FsVolume create(FsVolume fsVolume);

    FsVolume update(FsVolume fileVolume);

    int delete(int id);

    FsVolume fetch(int id);

    ResultPage<FsVolume> find(FindFsVolumeCriteria criteria);

    FsVolume getVolume(String volumeGroupName);

    void ensureDefaultVolumes();

    ValidationResult validate(FsVolume volume);

    ValidationResult validateForDupPath(FsVolume volume);

    ValidationResult validateVolumePath(FsVolume volume);
}
