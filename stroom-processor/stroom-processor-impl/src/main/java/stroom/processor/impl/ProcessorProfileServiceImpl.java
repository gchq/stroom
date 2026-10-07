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

package stroom.processor.impl;

import stroom.processor.shared.FindProcessorProfileRequest;
import stroom.processor.shared.ProcessorProfile;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEventBus;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Objects;

@Singleton
public class ProcessorProfileServiceImpl implements ProcessorProfileService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProcessorProfileServiceImpl.class);

    private final ProcessorProfileDao processorProfileDao;
    private final SecurityContext securityContext;
    private final Provider<EntityEventBus> entityEventBusProvider;

    @Inject
    public ProcessorProfileServiceImpl(final ProcessorProfileDao processorProfileDao,
                                       final SecurityContext securityContext,
                                       final Provider<EntityEventBus> entityEventBusProvider) {
        this.processorProfileDao = processorProfileDao;
        this.securityContext = securityContext;
        this.entityEventBusProvider = entityEventBusProvider;
    }

    @Override
    public List<String> getNames() {
        return securityContext.secureResult(processorProfileDao::getNames);
    }

    @Override
    public ResultPage<ProcessorProfile> find(final FindProcessorProfileRequest request) {
        return securityContext.secureResult(() -> processorProfileDao.find(request));
    }

    @Override
    public ProcessorProfile create(final ProcessorProfile processorProfile) {
        return securityContext.secureResult(AppPermission.MANAGE_PROCESSORS_PERMISSION, () -> {
            final ProcessorProfile persistedProfile = processorProfileDao.create(processorProfile.copy()
                    .stampAudit(securityContext)
                    .build());
            fireChange(EntityAction.CREATE, processorProfile.getName());
            return persistedProfile;
        });
    }

    @Override
    public ProcessorProfile fetchByName(final String name) {
        return securityContext.secureResult(() -> processorProfileDao.fetchByName(name));
    }

    @Override
    public ProcessorProfile fetchById(final int id) {
        return securityContext.secureResult(() -> processorProfileDao.fetchById(id));
    }

    @Override
    public ProcessorProfile update(final ProcessorProfile processorProfile) {
        final ProcessorProfile result = securityContext.secureResult(AppPermission.MANAGE_PROCESSORS_PERMISSION, () ->
                processorProfileDao.update(processorProfile.copy()
                        .stampAudit(securityContext)
                        .build()));
        fireChange(EntityAction.UPDATE, processorProfile.getName());
        return result;
    }

    @Override
    public void delete(final int id) {
        securityContext.secure(AppPermission.MANAGE_PROCESSORS_PERMISSION,
                () -> {
                    final ProcessorProfile processorProfile = processorProfileDao.fetchById(id);
                    Objects.requireNonNull(processorProfile, "Profile with id " + id + " not found");
                    processorProfileDao.delete(id);
                    fireChange(EntityAction.DELETE, processorProfile.getName());
                });
    }

    private void fireChange(final EntityAction action, final String profileName) {
        NullSafe.consume(entityEventBusProvider, Provider::get, entityEventBus -> {
            try {
                entityEventBus.buildFiring()
                        .withDocRef(EVENT_DOCREF)
                        .withAction(action)
                        .withStringData(profileName)
                        .fire();
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
            }
        });
    }
}
