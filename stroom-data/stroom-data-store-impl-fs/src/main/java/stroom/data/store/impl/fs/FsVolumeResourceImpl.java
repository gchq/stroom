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

import stroom.data.store.impl.fs.shared.FindFsVolumeCriteria;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.data.store.impl.fs.shared.FsVolumeResource;
import stroom.data.store.impl.fs.shared.ValidationResult;
import stroom.event.logging.api.DocumentEventLog;
import stroom.event.logging.api.StroomEventLoggingUtil;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResultPage;

import event.logging.Query;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

@AutoLogged(OperationType.MANUALLY_LOGGED)
class FsVolumeResourceImpl implements FsVolumeResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FsVolumeResourceImpl.class);

    private final Provider<FsVolumeService> volumeServiceProvider;
    private final Provider<DocumentEventLog> documentEventLogProvider;

    @Inject
    FsVolumeResourceImpl(final Provider<FsVolumeService> volumeServiceProvider,
                         final Provider<DocumentEventLog> documentEventLogProvider) {
        this.volumeServiceProvider = volumeServiceProvider;
        this.documentEventLogProvider = documentEventLogProvider;
    }

    @Override
    public ResultPage<FsVolume> find(final FindFsVolumeCriteria criteria) {
        final Query.Builder<Void> builder = Query.builder();

        StroomEventLoggingUtil.appendSelection(builder, criteria.getSelection());

        final Query query = builder.build();

        ResultPage<FsVolume> result = null;

        try {
            result = volumeServiceProvider.get().find(criteria);
            documentEventLogProvider.get().search(
                    criteria.getClass().getSimpleName(),
                    query,
                    FsVolume.class.getSimpleName(),
                    result.getPageResponse(),
                    null);
        } catch (final RuntimeException e) {
            documentEventLogProvider.get().search(
                    criteria.getClass().getSimpleName(),
                    query,
                    FsVolume.class.getSimpleName(),
                    null,
                    e);
            throw e;
        }

        return result;
    }

    @Override
    public FsVolume create(final FsVolume volume) {
        final FsVolume result;

        try {
            result = volumeServiceProvider.get().create(volume);
            documentEventLogProvider.get().create(result, null);
        } catch (final RuntimeException e) {
            documentEventLogProvider.get().create(volume, e);
            throw e;
        }

        return result;
    }

    @Override
    public FsVolume fetch(final Integer id) {
        final FsVolume result;

        try {
            result = volumeServiceProvider.get().fetch(id);
            documentEventLogProvider.get().view(result, null);
        } catch (final RuntimeException e) {
            final FsVolume fsVolume = new FsVolume();
            fsVolume.setId(id);
            documentEventLogProvider.get().view(fsVolume, e);
            throw e;
        }

        return result;
    }

    @Override
    public FsVolume update(final Integer id, final FsVolume volume) {
        FsVolume result = null;

        if (volume.getId() == null) {
            try {
                result = volumeServiceProvider.get().create(volume);
                documentEventLogProvider.get().create(volume, null);
            } catch (final RuntimeException e) {
                documentEventLogProvider.get().create(volume, e);
                throw e;
            }
        } else {
            try {
                result = volumeServiceProvider.get().update(volume);
                documentEventLogProvider.get().update(volume, result, null);
            } catch (final RuntimeException e) {
                documentEventLogProvider.get().update(volume, result, e);
                throw e;
            }
        }

        return result;
    }

    @Override
    public Boolean delete(final Integer id) {
        final FsVolume fsVolume = new FsVolume();
        fsVolume.setId(id);

        try {
            volumeServiceProvider.get().delete(id);
            documentEventLogProvider.get().delete(fsVolume, null);
        } catch (final RuntimeException e) {
            documentEventLogProvider.get().delete(fsVolume, e);
            throw e;
        }

        return true;
    }

    @Override
    @AutoLogged(value = OperationType.PROCESS, verb = "Flushing cache")
    public Boolean rescan() {
        volumeServiceProvider.get().flush();
        return true;
    }

    @Override
    public ValidationResult validate(final FsVolume volume) {
        return volumeServiceProvider.get().validate(volume);
    }
}
