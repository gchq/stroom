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

import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.processor.api.ProcessorService;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorType;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.util.AuditUtil;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;

public class ProcessorServiceImpl implements ProcessorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessorServiceImpl.class);

    private static final AppPermission PERMISSION = AppPermission.MANAGE_PROCESSORS_PERMISSION;

    private final SecurityContext securityContext;
    private final ProcessorDao processorDao;
    private final ProcessorTaskDao processorTaskDao;

    @Inject
    ProcessorServiceImpl(final SecurityContext securityContext,
                         final ProcessorDao processorDao,
                         final ProcessorTaskDao processorTaskDao) {
        this.securityContext = securityContext;
        this.processorDao = processorDao;
        this.processorTaskDao = processorTaskDao;
    }

    @Override
    public Processor create(final ProcessorType processorType,
                            final DocRef pipelineRef,
                            final boolean enabled) {

        final Processor processor = new Processor();
        processor.setProcessorType(processorType);
        processor.setPipeline(pipelineRef);
        processor.setEnabled(enabled);

        // Check the user has read permissions on the pipeline.
        if (!securityContext.hasDocumentPermission(
                pipelineRef,
                DocumentPermission.VIEW)) {
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to create this processor");
        }

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
    public Processor create(final ProcessorType processorType,
                            final DocRef processorDocRef,
                            final DocRef pipelineDocRef,
                            final boolean enabled) {
        final Processor processor = new Processor();
        processor.setProcessorType(processorType);
        processor.setPipeline(pipelineDocRef);
        processor.setEnabled(enabled);
        processor.setUuid(processorDocRef.getUuid());

        // Check the user has read permissions on the pipeline.
        if (!securityContext.hasDocumentPermission(
                pipelineDocRef,
                DocumentPermission.VIEW)) {
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to create this processor");
        }
        return create(processor);
    }


    @Override
    public Processor create(final Processor processor) {
        if (processor.getUuid() == null) {
            processor.setUuid(UUID.randomUUID().toString());
        }

        AuditUtil.stamp(securityContext, processor);

        return securityContext.secureResult(PERMISSION, () ->
                processorDao.create(processor));
    }

    @Override
    public Optional<Processor> fetch(final int id) {
        return securityContext.secureResult(() ->
                processorDao.fetch(id));
    }

    @Override
    public Optional<Processor> fetchByUuid(final String uuid) {
        return securityContext.secureResult(() ->
                processorDao.fetchByUuid(uuid));
    }

    @Override
    public Processor update(final Processor processor) {
        if (processor.getUuid() == null) {
            processor.setUuid(UUID.randomUUID().toString());
        }

        AuditUtil.stamp(securityContext, processor);
        return securityContext.secureResult(PERMISSION, () ->
                processorDao.update(processor));
    }

    @Override
    public boolean delete(final int id) {
        return securityContext.secureResult(PERMISSION, () -> {
            if (processorDao.logicalDeleteByProcessorId(id) > 0) {
                // Logically delete any associated tasks that have not yet finished processing.
                // Once the processor and filters are logically deleted no new tasks will be created
                // but we may still have active tasks for 'deleted' filters.
                processorTaskDao.logicalDeleteByProcessorId(id);
                return true;
            }
            return false;
        });
    }

    @Override
    public boolean deleteByPipelineUuid(final String pipelineUuid) {
        return securityContext.secureResult(PERMISSION, () ->
                processorDao.fetchByPipelineUuid(pipelineUuid)
                        .map(processor -> {
                            try {
                                // This will also logically 'delete' processor filters and any associated tasks that
                                // have not yet finished processing.
                                return delete(processor.getId());
                            } catch (final Exception e) {
                                throw new RuntimeException("Error deleting filters and processor for pipelineUuid "
                                        + pipelineUuid, e);
                            }
                        })
                        .orElseGet(() -> {
                            LOGGER.debug("No processor found with pipelineUuid {}", pipelineUuid);
                            return false;
                        }));
    }

    @Override
    public ResultPage<Processor> find(final ExpressionCriteria criteria) {
        return securityContext.secureResult(() ->
                processorDao.find(criteria));
    }

    @Override
    public void setEnabled(final Integer id, final Boolean enabled) {
        fetch(id).ifPresent(processor -> {
            processor.setEnabled(enabled);
            update(processor);
        });
    }

}
