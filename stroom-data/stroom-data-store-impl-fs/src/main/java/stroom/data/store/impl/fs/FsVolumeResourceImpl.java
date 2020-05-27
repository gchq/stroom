/*
 * Copyright 2017 Crown Copyright
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
import stroom.event.logging.api.DocumentEventLog;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResultPage;

import event.logging.BaseAdvancedQueryOperator.And;
import event.logging.Query;
import event.logging.Query.Advanced;

import javax.inject.Inject;
import javax.inject.Provider;

// TODO : @66 add event logging
class FsVolumeResourceImpl implements FsVolumeResource {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FsVolumeResourceImpl.class);

    private final Provider<FsVolumeService> volumeServiceProvider;
    private final DocumentEventLog documentEventLog;
    private final SecurityContext securityContext;

    @Inject
    FsVolumeResourceImpl(final Provider<FsVolumeService> volumeServiceProvider,
                         final DocumentEventLog documentEventLog,
                         final SecurityContext securityContext) {
        this.volumeServiceProvider = volumeServiceProvider;
        this.documentEventLog = documentEventLog;
        this.securityContext = securityContext;
    }

    @Override
    public ResultPage<FsVolume> find(final FindFsVolumeCriteria criteria) {
        // TODO : @66 fill out query
        final Query query = new Query();
        final Advanced advanced = new Advanced();
        query.setAdvanced(advanced);
        final And and = new And();
        advanced.getAdvancedQueryItems().add(and);

        return securityContext.secureResult(() -> {
            ResultPage<FsVolume> result = null;

            try {
                result = volumeServiceProvider.get().find(criteria);
                documentEventLog.search(criteria.getClass().getSimpleName(), query, FsVolume.class.getSimpleName(), result.getPageResponse(), null);
            } catch (final RuntimeException e) {
                documentEventLog.search(criteria.getClass().getSimpleName(), query, FsVolume.class.getSimpleName(), null, e);
                throw e;
            }

            return result;
        });
    }

    @Override
    public FsVolume create(final FsVolume volume) {
        return securityContext.secureResult(() -> {
            FsVolume result;

            try {
                result = volumeServiceProvider.get().create(volume);
                documentEventLog.create(result, null);
            } catch (final RuntimeException e) {
                documentEventLog.create(volume, e);
                throw e;
            }

            return result;
        });
    }

    @Override
    public FsVolume read(final Integer id) {
        return securityContext.secureResult(() -> {
            FsVolume result;

            try {
                result = volumeServiceProvider.get().fetch(id);
                documentEventLog.view(result, null);
            } catch (final RuntimeException e) {
                final FsVolume fsVolume = new FsVolume();
                fsVolume.setId(id);
                documentEventLog.view(fsVolume, e);
                throw e;
            }

            return result;
        });
    }

    @Override
    public FsVolume update(final Integer id, final FsVolume volume) {
        return securityContext.secureResult(() -> {
            FsVolume result = null;

            if (volume.getId() == null) {
                try {
                    result = volumeServiceProvider.get().create(volume);
                    documentEventLog.create(volume, null);
                } catch (final RuntimeException e) {
                    documentEventLog.create(volume, e);
                    throw e;
                }
            } else {
                try {
                    result = volumeServiceProvider.get().update(volume);
                    documentEventLog.update(volume, result, null);
                } catch (final RuntimeException e) {
                    documentEventLog.update(volume, result, e);
                    throw e;
                }
            }

            return result;
        });
    }

    @Override
    public Boolean delete(final Integer id) {
        return securityContext.secureResult(() -> {
            final FsVolume fsVolume = new FsVolume();
            fsVolume.setId(id);

            try {
                volumeServiceProvider.get().delete(id);
                documentEventLog.delete(fsVolume, null);
            } catch (final RuntimeException e) {
                documentEventLog.delete(fsVolume, e);
                throw e;
            }

            return true;
        });
    }

    @Override
    public Boolean rescan() {
        volumeServiceProvider.get().flush();
        return true;
    }
}