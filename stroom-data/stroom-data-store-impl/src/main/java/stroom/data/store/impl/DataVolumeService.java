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

package stroom.data.store.impl;


import stroom.aws.s3.shared.S3Location;
import stroom.data.store.impl.fs.shared.DataVolume;
import stroom.data.store.impl.fs.shared.FindDataVolumeCriteria;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.data.store.impl.fs.shared.S3LocationDataVolume;
import stroom.util.shared.ResultPage;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface DataVolumeService {

    ResultPage<DataVolume> find(FindDataVolumeCriteria criteria);

    DataVolume findDataVolume(long metaId);

    S3LocationDataVolume findS3Locations(long metaId);

    Set<S3Location> findS3Locations(DataVolume dataVolume);

    List<DataVolume> findDataVolumes(Collection<Long> metaIds);

    DataVolume createDataVolume(long metaId, FsVolume volume);

    S3LocationDataVolume createS3LocationDataVolume(long metaId,
                                                    FsVolume volume,
                                                    Set<S3Location> s3Locations,
                                                    boolean validateLocationsAgainstVolume);

    long getOrphanedMetaTrackerValue();
}
