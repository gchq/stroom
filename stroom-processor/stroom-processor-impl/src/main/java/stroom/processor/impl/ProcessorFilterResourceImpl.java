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
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResultPage;

import javax.inject.Inject;
import java.util.List;

// TODO : @66 add event logging
class ProcessorFilterResourceImpl implements ProcessorFilterResource {
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
        return processorFilterService.create(
                request.getPipeline(),
                request.getQueryData(),
                request.getPriority(),
                request.isEnabled());
    }

    @Override
    public ProcessorFilter read(final Integer id) {
        return processorFilterService.fetch(id).orElse(null);
    }

    @Override
    public ProcessorFilter update(final Integer id, final ProcessorFilter processorFilter) {
        return processorFilterService.update(processorFilter);
    }

    @Override
    public void delete(final Integer id) {
        processorFilterService.delete(id);
    }

    @Override
    public void setPriority(final Integer id, final Integer priority) {
        processorFilterService.setPriority(id, priority);
    }

    @Override
    public void setEnabled(final Integer id, final Boolean enabled) {
        processorFilterService.setEnabled(id, enabled);
    }

    @Override
    public ProcessorListRowResultPage find(final FetchProcessorRequest request) {
        final ResultPage<ProcessorListRow> resultPage = processorFilterService.find(request);
        return new ProcessorListRowResultPage(resultPage.getValues(), resultPage.getPageResponse());
    }

    @Override
    public List<ReprocessDataInfo> reprocess(final FindMetaCriteria criteria) {
        return processorFilterService.reprocess(criteria);
    }
}