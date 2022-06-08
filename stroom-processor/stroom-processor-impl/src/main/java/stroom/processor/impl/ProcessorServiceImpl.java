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
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.api.ProcessorService;
import stroom.processor.shared.Processor;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.security.shared.PermissionNames;
import stroom.util.AuditUtil;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResultPage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;
import javax.inject.Inject;

public class ProcessorServiceImpl implements ProcessorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessorServiceImpl.class);

    private static final String PERMISSION = PermissionNames.MANAGE_PROCESSORS_PERMISSION;

    private final SecurityContext securityContext;
    private final ProcessorDao processorDao;
    private final ProcessorFilterService processorFilterService;

    @Inject
    ProcessorServiceImpl(final SecurityContext securityContext,
                         final ProcessorDao processorDao,
                         final ProcessorFilterService processorFilterService) {
        this.securityContext = securityContext;
        this.processorDao = processorDao;
        this.processorFilterService = processorFilterService;
    }

    @Override
    public Processor create(final DocRef pipelineRef, final boolean enabled) {

        final Processor processor = new Processor();
        processor.setEnabled(enabled);
        processor.setPipeline(pipelineRef);

        // Check the user has read permissions on the pipeline.
        if (!securityContext.hasDocumentPermission(
                processor.getPipelineUuid(),
                DocumentPermissionNames.READ)) {

            throw new PermissionException(securityContext.getUserId(),
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
    public Processor create(DocRef processorDocRef, DocRef pipelineDocRef, boolean enabled) {
        final Processor processor = new Processor();
        processor.setEnabled(enabled);
        processor.setPipeline(pipelineDocRef);
        processor.setUuid(processorDocRef.getUuid());

        // Check the user has read permissions on the pipeline.
        if (!securityContext.hasDocumentPermission(
                processor.getPipelineUuid(),
                DocumentPermissionNames.READ)) {
            throw new PermissionException(securityContext.getUserId(),
                    "You do not have permission to create this processor");
        }
        return create(processor);
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
    public Optional<Processor> fetchByUuid(final String uuid) {
        return securityContext.secureResult(() ->
                processorDao.fetchByUuid(uuid));
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
        return securityContext.secureResult(PERMISSION, () -> {
            // This will also 'delete' processor filters and UNPROCESSED tasks
            return processorDao.logicalDelete(id);
        });
    }

    @Override
    public boolean deleteByPipelineUuid(final String pipelineUuid) {
        return securityContext.secureResult(PERMISSION, () ->
                processorDao.fetchByPipelineUuid(pipelineUuid)
                        .map(processor -> {
                            try {
                                // This will also 'delete' processor filters and UNPROCESSED tasks
                                return processorDao.logicalDelete(processor.getId());
                            } catch (Exception e) {
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
