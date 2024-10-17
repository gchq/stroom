/*
 * Copyright 2017-2024 Crown Copyright
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


import stroom.datasource.api.v2.FindFieldCriteria;
import stroom.datasource.api.v2.QueryField;
import stroom.docref.DocRef;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.entity.shared.ExpressionCriteria;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.api.ProcessorTaskService;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.ProcessorTaskFields;
import stroom.processor.shared.ProcessorTaskSummary;
import stroom.query.common.v2.FieldInfoResultPageBuilder;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.ValuesConsumer;
import stroom.searchable.api.Searchable;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.util.NullSafe;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

@Singleton
class ProcessorTaskServiceImpl implements ProcessorTaskService, Searchable {

    private static final AppPermission PERMISSION = AppPermission.MANAGE_PROCESSORS_PERMISSION;

    private final ProcessorTaskDao processorTaskDao;
    private final DocRefInfoService docRefInfoService;
    private final SecurityContext securityContext;

    @Inject
    ProcessorTaskServiceImpl(final ProcessorTaskDao processorTaskDao,
                             final DocRefInfoService docRefInfoService,
                             final SecurityContext securityContext) {
        this.processorTaskDao = processorTaskDao;
        this.docRefInfoService = docRefInfoService;
        this.securityContext = securityContext;
    }

    @Override
    public ResultPage<ProcessorTask> find(final ExpressionCriteria criteria) {
        return securityContext.secureResult(PERMISSION, () -> {
            final ResultPage<ProcessorTask> resultPage = processorTaskDao.find(criteria);
            resultPage.getValues().forEach(processorTask -> {
                final DocRef docRef = new DocRef(PipelineDoc.DOCUMENT_TYPE,
                        processorTask.getProcessorFilter().getPipelineUuid());
                final Optional<String> name = docRefInfoService.name(docRef);
                processorTask.getProcessorFilter().setPipelineName(name.orElse(null));
            });
            return resultPage;
        });
    }

    @Override
    public ResultPage<ProcessorTaskSummary> findSummary(final ExpressionCriteria criteria) {
        return securityContext.secureResult(PERMISSION, () ->
                processorTaskDao.findSummary(criteria));
    }

    @Override
    public void search(final ExpressionCriteria criteria, final FieldIndex fieldIndex, final ValuesConsumer consumer) {
        securityContext.secure(PERMISSION, () ->
                processorTaskDao.search(criteria, fieldIndex, consumer));
    }

    @Override
    public DocRef getDocRef() {
        if (securityContext.hasAppPermission(PERMISSION)) {
            return ProcessorTaskFields.PROCESSOR_TASK_PSEUDO_DOC_REF;
        }
        return null;
    }

    @Override
    public ResultPage<QueryField> getFieldInfo(final FindFieldCriteria criteria) {
        if (!ProcessorTaskFields.PROCESSOR_TASK_PSEUDO_DOC_REF.equals(criteria.getDataSourceRef())) {
            return ResultPage.empty();
        }
        return FieldInfoResultPageBuilder.builder(criteria)
                .addAll(getFields())
                .build();
    }

    @Override
    public int getFieldCount(final DocRef docRef) {
        return NullSafe.size(getFields());
    }

    private List<QueryField> getFields() {
        return ProcessorTaskFields.getFields();
    }

    @Override
    public Optional<String> fetchDocumentation(final DocRef docRef) {
        return Optional.empty();
    }

    @Override
    public QueryField getTimeField() {
        return ProcessorTaskFields.CREATE_TIME;
    }
}
