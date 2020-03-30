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

package stroom.processor.impl;

import com.codahale.metrics.health.HealthCheck.Result;
import stroom.event.logging.api.DocumentEventLog;
import stroom.meta.shared.FindMetaCriteria;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.shared.CreateProcessorFilterRequest;
import stroom.processor.shared.FetchProcessorRequest;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterResource;
import stroom.processor.shared.ProcessorListRow;
import stroom.processor.shared.ProcessorListRowResultPage;
import stroom.processor.shared.ReprocessDataInfo;
import stroom.util.HasHealthCheck;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResultPage;

import javax.inject.Inject;
import javax.ws.rs.ServerErrorException;
import javax.ws.rs.core.Response.Status;
import java.util.List;

// TODO : @66 add event logging
class ProcessorFilterResourceImpl implements ProcessorFilterResource, HasHealthCheck {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProcessorFilterResourceImpl.class);

    private final ProcessorFilterService processorFilterService;
    private final DocumentEventLog documentEventLog;

    @Inject
    ProcessorFilterResourceImpl(final ProcessorFilterService processorFilterService,
                                final DocumentEventLog documentEventLog) {
        this.processorFilterService = processorFilterService;
        this.documentEventLog = documentEventLog;
    }

    @Override
    public ProcessorFilter create(final CreateProcessorFilterRequest request) {
        try {
            return processorFilterService.create(request.getPipeline(), request.getQueryData(), request.getPriority(), request.isEnabled());
        } catch (final RuntimeException e) {
            throw new ServerErrorException(Status.INTERNAL_SERVER_ERROR, e);
        }
    }

    @Override
    public ProcessorFilter read(final Integer id) {
        try {
            return processorFilterService.fetch(id).orElse(null);
        } catch (final RuntimeException e) {
            throw new ServerErrorException(Status.INTERNAL_SERVER_ERROR, e);
        }
    }

    @Override
    public ProcessorFilter update(final Integer id, final ProcessorFilter processorFilter) {
        try {
            return processorFilterService.update(processorFilter);
        } catch (final RuntimeException e) {
            throw new ServerErrorException(Status.INTERNAL_SERVER_ERROR, e);
        }
    }

    @Override
    public void delete(final Integer id) {
        try {
            processorFilterService.delete(id);
        } catch (final RuntimeException e) {
            throw new ServerErrorException(Status.INTERNAL_SERVER_ERROR, e);
        }
    }

    @Override
    public void setPriority(final Integer id, final Integer priority) {
        try {
            processorFilterService.setPriority(id, priority);
        } catch (final RuntimeException e) {
            throw new ServerErrorException(Status.INTERNAL_SERVER_ERROR, e);
        }
    }

    @Override
    public void setEnabled(final Integer id, final Boolean enabled) {
        try {
            processorFilterService.setEnabled(id, enabled);
        } catch (final RuntimeException e) {
            throw new ServerErrorException(Status.INTERNAL_SERVER_ERROR, e);
        }
    }

    @Override
    public ProcessorListRowResultPage find(final FetchProcessorRequest request) {
        try {
            final ResultPage<ProcessorListRow> resultPage = processorFilterService.find(request);
            return new ProcessorListRowResultPage(resultPage.getValues(), resultPage.getPageResponse());
        } catch (final RuntimeException e) {
            throw new ServerErrorException(Status.INTERNAL_SERVER_ERROR, e);
        }
    }

    @Override
    public List<ReprocessDataInfo> reprocess(final FindMetaCriteria criteria) {
        try {
            return processorFilterService.reprocess(criteria);
        } catch (final RuntimeException e) {
            throw new ServerErrorException(Status.INTERNAL_SERVER_ERROR, e);
        }
    }

    @Override
    public Result getHealth() {
        return Result.healthy();
    }
}