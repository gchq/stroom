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

import stroom.entity.shared.ExpressionCriteria;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.shared.BulkProcessorFilterChangeRequest;
import stroom.processor.shared.CreateProcessFilterRequest;
import stroom.processor.shared.FetchProcessorRequest;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterResource;
import stroom.processor.shared.ProcessorListRow;
import stroom.processor.shared.ProcessorListRowResultPage;
import stroom.processor.shared.ReprocessDataInfo;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.List;

@AutoLogged
class ProcessorFilterResourceImpl implements ProcessorFilterResource {

    private static final AppPermission PERMISSION = AppPermission.MANAGE_PROCESSORS_PERMISSION;

    private final Provider<ProcessorFilterService> processorFilterServiceProvider;
    private final Provider<SecurityContext> securityContextProvider;

    @Inject
    ProcessorFilterResourceImpl(final Provider<ProcessorFilterService> processorFilterServiceProvider,
                                final Provider<SecurityContext> securityContextProvider) {
        this.processorFilterServiceProvider = processorFilterServiceProvider;
        this.securityContextProvider = securityContextProvider;
    }

    @Override
    public ProcessorFilter create(final CreateProcessFilterRequest request) {
        return processorFilterServiceProvider.get().create(request);
    }

    @AutoLogged(value = OperationType.PROCESS, verb = "Reprocessing")
    @Override
    public List<ReprocessDataInfo> reprocess(final CreateProcessFilterRequest request) {
        return processorFilterServiceProvider.get().reprocess(request);
    }

    @Override
    public ProcessorFilter fetch(final Integer id) {
        return processorFilterServiceProvider.get().fetch(id).orElse(null);
    }

    @Override
    public ProcessorFilter update(final Integer id, final ProcessorFilter processorFilter) {
        return processorFilterServiceProvider.get().update(processorFilter);
    }

    @Override
    public boolean delete(final Integer id) {
        processorFilterServiceProvider.get().delete(id);
        return true;
    }

    @Override
    public boolean setPriority(final Integer id, final Integer priority) {
        processorFilterServiceProvider.get().setPriority(id, priority);
        return true;
    }

    @Override
    public boolean setMaxProcessingTasks(final Integer id, final Integer maxProcessingTasks) {
        processorFilterServiceProvider.get().setMaxProcessingTasks(id, maxProcessingTasks);
        return true;
    }

    @Override
    public boolean setEnabled(final Integer id, final Boolean enabled) {
        processorFilterServiceProvider.get().setEnabled(id, enabled);
        return true;
    }

    @Override
    public ProcessorListRowResultPage find(final FetchProcessorRequest request) {
        final ResultPage<ProcessorListRow> resultPage = processorFilterServiceProvider.get().find(request);
        return new ProcessorListRowResultPage(resultPage.getValues(), resultPage.getPageResponse());
    }

    @Override
    public Boolean bulkChange(final BulkProcessorFilterChangeRequest request) {
        final ProcessorFilterService processorFilterService = processorFilterServiceProvider.get();
        final SecurityContext securityContext = securityContextProvider.get();
        securityContext.secure(PERMISSION, () -> {
            final ExpressionCriteria criteria = new ExpressionCriteria(
                    PageRequest.unlimited(),
                    null,
                    request.getExpression());
            final ResultPage<ProcessorFilter> resultPage = processorFilterService.find(criteria);
            resultPage.getValues().stream().forEach(filter -> {
                switch (request.getChange()) {
                    case ENABLE -> processorFilterService.setEnabled(filter.getId(), true);
                    case DISABLE -> processorFilterService.setEnabled(filter.getId(), false);
                    case DELETE -> processorFilterService.delete(filter.getId());
                    case SET_RUN_AS_USER -> {
                        filter.setRunAsUser(request.getUserRef());
                        processorFilterService.update(filter);
                    }
                }
            });
        });

        return true;
    }
}
