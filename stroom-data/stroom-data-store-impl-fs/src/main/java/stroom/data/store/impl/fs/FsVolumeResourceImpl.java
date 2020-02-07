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

import com.codahale.metrics.health.HealthCheck.Result;
import event.logging.BaseAdvancedQueryOperator.And;
import event.logging.Query;
import event.logging.Query.Advanced;
import stroom.data.store.impl.fs.shared.FindFsVolumeCriteria;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.data.store.impl.fs.shared.FsVolumeResource;
import stroom.data.store.impl.fs.shared.FsVolumeResultPage;
import stroom.event.logging.api.DocumentEventLog;
import stroom.security.api.SecurityContext;
import stroom.util.HasHealthCheck;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.BaseResultList;
import stroom.util.shared.RestResource;

import javax.inject.Inject;

// TODO : @66 add event logging
class FsVolumeResourceImpl implements FsVolumeResource, RestResource, HasHealthCheck {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FsVolumeResourceImpl.class);

    private final FsVolumeService volumeService;
    private final DocumentEventLog documentEventLog;
    private final SecurityContext securityContext;

    @Inject
    FsVolumeResourceImpl(final FsVolumeService volumeService,
                         final DocumentEventLog documentEventLog,
                         final SecurityContext securityContext) {
        this.volumeService = volumeService;
        this.documentEventLog = documentEventLog;
        this.securityContext = securityContext;
    }

    @Override
    public FsVolumeResultPage find(final FindFsVolumeCriteria criteria) {
        // TODO : @66 fill out query
        final Query query = new Query();
        final Advanced advanced = new Advanced();
        query.setAdvanced(advanced);
        final And and = new And();
        advanced.getAdvancedQueryItems().add(and);

        return securityContext.secureResult(() -> {
            BaseResultList<FsVolume> result = null;

            try {
                result = volumeService.find(criteria);
                documentEventLog.search(criteria.getClass().getSimpleName(), query, FsVolume.class.getSimpleName(), result.getPageResponse(), null);
            } catch (final RuntimeException e) {
                documentEventLog.search(criteria.getClass().getSimpleName(), query, FsVolume.class.getSimpleName(), null, e);
                throw e;
            }

            return result.toResultPage(new FsVolumeResultPage());
        });
    }

    @Override
    public FsVolume create() {
        return null;
    }

    @Override
    public FsVolume read(final Integer id) {
        return securityContext.secureResult(() -> {
            FsVolume result;

            try {
                result = volumeService.fetch(id);
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
                    result = volumeService.create(volume);
                    documentEventLog.create(volume, null);
                } catch (final RuntimeException e) {
                    documentEventLog.create(volume, e);
                    throw e;
                }
            } else {
                try {
                    result = volumeService.update(volume);
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
                volumeService.delete(id);
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
        volumeService.flush();
        return true;
    }

    @Override
    public Result getHealth() {
        return Result.healthy();
    }
}