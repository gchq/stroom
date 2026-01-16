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

package stroom.processor.impl;

import stroom.processor.shared.ProcessorProfile;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.util.AuditUtil;
import stroom.util.NextNameGenerator;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventBus;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
@EntityEventHandler(type = ProcessorProfileService.ENTITY_TYPE, action = {
        EntityAction.UPDATE,
        EntityAction.CREATE,
        EntityAction.DELETE})
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
    public List<ProcessorProfile> getAll() {
        return securityContext.secureResult(processorProfileDao::getAll);
    }

    @Override
    public ProcessorProfile getOrCreate(final String name) {
        final ProcessorProfile result = securityContext.secureResult(AppPermission.MANAGE_PROCESSORS_PERMISSION, () -> {
            final ProcessorProfile.Builder builder = ProcessorProfile.builder();
            builder.name(name);
            AuditUtil.stamp(securityContext, builder);
            return processorProfileDao.getOrCreate(builder.build());
        });
        fireChange(EntityAction.CREATE);
        return result;
    }

    @Override
    public ProcessorProfile create() {
        final String newName = NextNameGenerator.getNextName(processorProfileDao.getNames(), "New profile");
        final ProcessorProfile result = securityContext.secureResult(AppPermission.MANAGE_PROCESSORS_PERMISSION, () -> {
            final ProcessorProfile.Builder builder = ProcessorProfile.builder();
            builder.name(newName);
            AuditUtil.stamp(securityContext, builder);
            return processorProfileDao.getOrCreate(builder.build());
        });
        fireChange(EntityAction.CREATE);
        return result;
    }

    @Override
    public ProcessorProfile update(final ProcessorProfile processorProfile) {
        final ProcessorProfile result = securityContext.secureResult(AppPermission.MANAGE_PROCESSORS_PERMISSION, () -> {
            final ProcessorProfile.Builder builder = processorProfile.copy();
            AuditUtil.stamp(securityContext, builder);
            return processorProfileDao.update(builder.build());
        });
        fireChange(EntityAction.UPDATE);
        return result;
    }

    @Override
    public ProcessorProfile get(final String name) {
        return securityContext.secureResult(() -> processorProfileDao.get(name));
    }

    @Override
    public ProcessorProfile get(final int id) {
        return securityContext.secureResult(() -> processorProfileDao.get(id));
    }

    @Override
    public void delete(final int id) {
        securityContext.secure(AppPermission.MANAGE_PROCESSORS_PERMISSION,
                () -> processorProfileDao.delete(id));
        fireChange(EntityAction.DELETE);
    }

    private void fireChange(final EntityAction action) {
        if (entityEventBusProvider != null) {
            try {
                final EntityEventBus entityEventBus = entityEventBusProvider.get();
                if (entityEventBus != null) {
                    entityEventBus.fire(new EntityEvent(EVENT_DOCREF, action));
                }
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
            }
        }
    }
}
