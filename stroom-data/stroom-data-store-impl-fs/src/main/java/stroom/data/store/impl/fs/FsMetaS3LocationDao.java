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


import stroom.aws.s3.shared.S3Location;
import stroom.data.store.impl.fs.shared.FsVolume;

import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

public interface FsMetaS3LocationDao {

    S3LocationDataVolume create(final long metaId,
                                final FsVolume fsVolume, final Set<S3Location> s3Locations);


    @Nullable
    S3LocationDataVolume getS3LocationDataVolume(final long metaId);

    int delete(final Collection<Long> metaIds);
}
