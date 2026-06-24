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

package stroom.data.store.impl.fs.s3v1;


import stroom.data.store.api.DataException;
import stroom.data.store.api.Source;
import stroom.data.store.api.Target;
import stroom.data.store.impl.fs.DataVolumeDao.DataVolume;
import stroom.data.store.impl.fs.PhysicalDeleteExecutor.Progress;
import stroom.data.store.impl.fs.StreamStore;
import stroom.data.store.impl.fs.shared.FsVolumeType;
import stroom.meta.shared.Meta;
import stroom.meta.shared.SimpleMeta;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;

import java.util.Collection;

public class S3ReadOnlyStreamStore implements StreamStore {

    // Delegates everything that is a read only to this
    final S3StreamStore streamStore;

    @Inject
    public S3ReadOnlyStreamStore(final S3StreamStore streamStore) {
        this.streamStore = streamStore;
    }

    @Override
    public Target openTarget(final Meta meta, final DataVolume dataVolume) throws DataException {
        throw new UnsupportedOperationException(LogUtil.message(
                "openTarget not supported on a read-only stream store, meta: {}, dataVolume: {}",
                meta, dataVolume));
    }

    @Override
    public void physicallyDelete(final Collection<DataVolume> dataVolumes) {
        throw new UnsupportedOperationException(LogUtil.message(
                "physicallyDelete not supported on a read-only stream store, dataVolumes: {}",
                LogUtil.getSample(dataVolumes, 10)));
    }

    @Override
    public PhysicalDeleteOutcome physicallyDelete(final SimpleMeta simpleMeta,
                                                  final DataVolume dataVolume,
                                                  final Progress progress) {
        throw new UnsupportedOperationException(LogUtil.message(
                "physicallyDelete not supported on a read-only stream store, simpleMeta: {}, dataVolume: {}",
                simpleMeta, dataVolume));
    }

    @Override
    public Source openSource(final Meta meta, final DataVolume dataVolume) throws DataException {
        return streamStore.openSource(meta, dataVolume);
    }

    @Override
    public FsVolumeType getVolumeType() {
        return FsVolumeType.S3_V1_READ_ONLY;
    }
}
