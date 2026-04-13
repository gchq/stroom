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


import stroom.data.store.api.DataException;
import stroom.data.store.api.Source;
import stroom.data.store.api.Target;
import stroom.data.store.impl.fs.DataVolumeDao.DataVolume;
import stroom.meta.shared.Meta;

import java.util.Collection;

/**
 * Interface for the various implementations of Stream Store, i.e. traditional FS, S3, etc.
 */
public interface StreamStore {

    /**
     * Open a new target (i.e. new file) based on some meta data
     *
     * @return the stream to write to
     */
    Target openTarget(final Meta meta, final DataVolume dataVolume) throws DataException;

    /**
     * Physically delete streams.
     */
    void physicallyDelete(final Collection<DataVolume> dataVolumes);

    /**
     * <p>
     * Open a existing stream source.
     * </p>
     *
     * @param meta       The Meta object of the stream to open.
     * @param dataVolume The volume on which the stream exists.
     * @return The stream source if the stream can be found.
     * @throws DataException in case of a IO error or stream volume not visible or non
     *                       existent.
     */
    Source openSource(final Meta meta, final DataVolume dataVolume) throws DataException;
}
