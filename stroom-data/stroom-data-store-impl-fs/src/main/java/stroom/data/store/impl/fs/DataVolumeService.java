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

import stroom.data.store.impl.fs.DataVolumeDao.DataVolume;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.security.api.SecurityContext;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;

public class DataVolumeService {

    private final DataVolumeDao dataVolumeDao;
    private final SecurityContext securityContext;
    private final FsOrphanedMetaDao fsOrphanedMetaDao;

    @Inject
    DataVolumeService(final DataVolumeDao dataVolumeDao,
                      final SecurityContext securityContext,
                      final FsOrphanedMetaDao fsOrphanedMetaDao) {
        this.dataVolumeDao = dataVolumeDao;
        this.securityContext = securityContext;
        this.fsOrphanedMetaDao = fsOrphanedMetaDao;
    }

    public ResultPage<DataVolume> find(final FindDataVolumeCriteria criteria) {
        if (!criteria.isValidCriteria()) {
            throw new IllegalArgumentException("Not enough criteria to run");
        }

        return securityContext.secureResult(() ->
                dataVolumeDao.find(criteria));
    }

    /**
     * Return the meta data volumes for a stream id.
     */
    public DataVolume findDataVolume(final long metaId) {
        return securityContext.secureResult(() ->
                dataVolumeDao.findDataVolume(metaId));
    }

    public DataVolume createDataVolume(final long metaId, final FsVolume volume) {
        return securityContext.secureResult(() ->
                dataVolumeDao.createDataVolume(metaId, volume));
    }

    public long getOrphanedMetaTrackerValue() {
        return securityContext.secureResult(fsOrphanedMetaDao::getMetaIdTrackerValue);
    }

    void updateOrphanedMetaTracker(final long metaId) {
        securityContext.secure(() ->
                fsOrphanedMetaDao.updateMetaIdTracker(metaId));
    }
}
