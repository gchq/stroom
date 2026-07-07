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


import stroom.data.store.api.S3Location;
import stroom.data.store.impl.fs.DataVolumeDao.DataVolume;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.data.store.impl.fs.shared.FsVolumeType;
import stroom.util.shared.NullSafe;

import java.util.Objects;
import java.util.Set;

/**
 * @param dataVolume
 * @param s3Locations
 */
public record S3LocationDataVolume(DataVolume dataVolume,
                                   Set<S3Location> s3Locations) implements DataVolume {

    public S3LocationDataVolume(final DataVolume dataVolume,
                                final Set<S3Location> s3Locations) {
        this.dataVolume = Objects.requireNonNull(dataVolume);
        if (NullSafe.isEmptyCollection(s3Locations)) {
            throw new IllegalArgumentException("There must be at least one S3Location");
        }
        this.s3Locations = NullSafe.unmodifialbeSet(s3Locations);
    }

    @Override
    public long metaId() {
        return dataVolume.metaId();
    }

    @Override
    public FsVolume volume() {
        return dataVolume.volume();
    }

    @Override
    public Integer getVolumeId() {
        return dataVolume.getVolumeId();
    }

    @Override
    public FsVolumeType getVolumeType() {
        return dataVolume.getVolumeType();
    }
}
