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
 *
 */

package stroom.processor.impl;

import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.processor.api.ProcessorService;
import stroom.processor.shared.Processor;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.util.AuditUtil;
import stroom.util.shared.BaseResultList;

import javax.inject.Inject;
import java.util.Optional;
import java.util.UUID;

public class ProcessorServiceImpl implements ProcessorService {
    private static final String PERMISSION = PermissionNames.MANAGE_PROCESSORS_PERMISSION;

    private final SecurityContext securityContext;
    private final ProcessorDao processorDao;

    @Inject
    ProcessorServiceImpl(final SecurityContext securityContext,
                         final ProcessorDao processorDao) {
        this.securityContext = securityContext;
        this.processorDao = processorDao;
    }

    @Override
    public Processor create(final DocRef pipelineRef, final boolean enabled) {
        final Processor processor = new Processor();
        processor.setEnabled(enabled);
        processor.setPipelineUuid(pipelineRef.getUuid());
        return create(processor);

//        Processor processor = null;
//
//        final List<Processor> list = find(new FindProcessorCriteria(pipelineRef));
//        if (list.size() > 0) {
//            processor = list.get(0);
//        }
//
//        if (processor == null) {
//            final Processor processor = new Processor();
//            processor.setEnabled(enabled);
//            processor.setPipelineUuid(pipelineRef.getUuid());
//            return create(processor);
//        }
//        return processor;
    }

    @Override
    public Processor create(Processor processor) {
        if (processor.getUuid() == null) {
            processor.setUuid(UUID.randomUUID().toString());
        }

        AuditUtil.stamp(securityContext.getUserId(), processor);
        return securityContext.secureResult(PERMISSION, () ->
                processorDao.create(processor));
    }

    @Override
    public Optional<Processor> fetch(final int id) {
        return securityContext.secureResult(() ->
                processorDao.fetch(id));
    }

    @Override
    public Processor update(final Processor processor) {
        if (processor.getUuid() == null) {
            processor.setUuid(UUID.randomUUID().toString());
        }

        AuditUtil.stamp(securityContext.getUserId(), processor);
        return securityContext.secureResult(PERMISSION, () ->
                processorDao.update(processor));
    }

    @Override
    public boolean delete(final int id) {
        return securityContext.secureResult(PERMISSION, () ->
                processorDao.delete(id));
    }

    @Override
    public BaseResultList<Processor> find(final ExpressionCriteria criteria) {
        return securityContext.secureResult(() ->
                processorDao.find(criteria));
    }
}
