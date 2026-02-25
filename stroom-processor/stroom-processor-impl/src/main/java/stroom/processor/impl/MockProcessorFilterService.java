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
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.api.ProcessorService;
import stroom.processor.shared.CreateProcessFilterRequest;
import stroom.processor.shared.FetchProcessorRequest;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterRow;
import stroom.processor.shared.ProcessorListRow;
import stroom.processor.shared.ProcessorType;
import stroom.processor.shared.ReprocessDataInfo;
import stroom.security.api.SecurityContext;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

@Singleton
public class MockProcessorFilterService implements ProcessorFilterService {

    private final ProcessorService processorService;
    private final MockProcessorFilterDao dao;
    private final SecurityContext securityContext;

    @Inject
    MockProcessorFilterService(final ProcessorService processorService,
                               final MockProcessorFilterDao dao,
                               final SecurityContext securityContext) {
        this.processorService = processorService;
        this.dao = dao;
        this.securityContext = securityContext;
    }

    @Override
    public ProcessorFilter create(final CreateProcessFilterRequest request) {
        final ProcessorFilter.Builder builder = ProcessorFilter.builder()
                .pipelineUuid(request.getPipeline().getUuid())
                .queryData(request.getQueryData())
                .priority(request.getPriority())
                .maxProcessingTasks(request.getMaxProcessingTasks())
                .enabled(request.isEnabled())
                .minMetaCreateTimeMs(request.getMinMetaCreateTimeMs())
                .maxMetaCreateTimeMs(request.getMaxMetaCreateTimeMs());
        setRunAs(request, builder);
        final Processor processor = processorService.create(
                request.getProcessorType(),
                request.getPipeline(),
                request.isEnabled());
        builder.processor(processor);
        return dao.create(builder.build());
    }

    private void setRunAs(final CreateProcessFilterRequest request,
                          final ProcessorFilter.Builder filter) {
        filter.runAsUser(NullSafe.getOrElse(request,
                CreateProcessFilterRequest::getRunAsUser,
                securityContext.getUserRef()));
    }

    //    @Override
//    public ProcessorFilter create(DocRef pipelineRef, QueryData queryData, int priority,
//    boolean enabled, Long trackerStartMs) {
//        return create (pipelineRef, queryData, priority, enabled, null);
//    }
//
    @Override
    public ProcessorFilter create(final Processor processor,
                                  final CreateProcessFilterRequest request) {
        final ProcessorFilter.Builder builder = ProcessorFilter.builder()
                .processor(processor)
                .queryData(request.getQueryData())
                .priority(request.getPriority())
                .maxProcessingTasks(request.getMaxProcessingTasks())
                .enabled(request.isEnabled())
                .minMetaCreateTimeMs(request.getMinMetaCreateTimeMs())
                .maxMetaCreateTimeMs(request.getMaxMetaCreateTimeMs());
        setRunAs(request, builder);
        return dao.create(builder.build());
    }

    @Override
    public List<ReprocessDataInfo> reprocess(final CreateProcessFilterRequest request) {
        return null;
    }

    @Override
    public ProcessorFilter importFilter(final ProcessorFilter existingProcessorFilter,
                                        final Processor processor,
                                        final DocRef processorFilterDocRef,
                                        final CreateProcessFilterRequest request) {
        final ProcessorFilter.Builder builder = ProcessorFilter.builder()
                .processor(processor)
                .queryData(request.getQueryData())
                .priority(request.getPriority())
                .maxProcessingTasks(request.getMaxProcessingTasks())
                .uuid(processorFilterDocRef.getUuid())
                .enabled(request.isEnabled())
                .minMetaCreateTimeMs(request.getMinMetaCreateTimeMs())
                .maxMetaCreateTimeMs(request.getMaxMetaCreateTimeMs());
        setRunAs(request, builder);
        return dao.create(builder.build());
    }

    @Override
    public ResultPage<ProcessorFilter> find(final ExpressionCriteria criteria) {
        return dao.find(criteria);
    }

    @Override
    public ResultPage<ProcessorListRow> find(final FetchProcessorRequest request) {
        return null;
    }

    @Override
    public ProcessorFilterRow getRow(final ProcessorFilter processorFilter) {
        return null;
    }

    @Override
    public ResultPage<ProcessorFilter> find(final DocRef pipelineDocRef) {
        return null;
    }

    @Override
    public void setPriority(final Integer id, final Integer priority) {
    }

    @Override
    public void setMaxProcessingTasks(final Integer id, final Integer maxProcessingTasks) {
    }

    @Override
    public void setEnabled(final Integer id, final Boolean enabled) {

    }

    @Override
    public ProcessorFilter create(final ProcessorFilter processorFilter) {
        return dao.create(processorFilter);
    }

    @Override
    public Optional<ProcessorFilter> fetch(final int id) {
        return dao.fetch(id);
    }

    @Override
    public ProcessorFilter update(final ProcessorFilter processorFilter) {
        return dao.update(processorFilter);
    }

    @Override
    public boolean delete(final int id) {
        return dao.delete(id);
    }

    @Override
    public Optional<String> getPipelineName(final ProcessorType processorType, final String uuid) {
        return Optional.empty();
    }

    @Override
    public ProcessorFilter restore(final DocRef processorFilterDocRef, final boolean resetTracker) {
        final ProcessorFilter processorFilter = dao.fetchByUuid(processorFilterDocRef.getUuid())
                .orElseThrow();
        return processorFilter.copy().deleted(false).build();
    }

    @Override
    public Optional<ProcessorFilter> fetchByUuid(final String uuid) {
        return dao.fetchByUuid(uuid);
    }
}
