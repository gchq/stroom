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
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.entity.shared.ExpressionCriteria;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.api.ProcessorTaskService;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.ProcessorTaskFields;
import stroom.processor.shared.ProcessorTaskSummary;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.QueryField;
import stroom.query.common.v2.FieldInfoResultPageFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.ValuesConsumer;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.searchable.api.Searchable;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Singleton
class ProcessorTaskServiceImpl implements ProcessorTaskService, Searchable {

    private static final AppPermission PERMISSION = AppPermission.MANAGE_PROCESSORS_PERMISSION;

    private final ProcessorTaskDao processorTaskDao;
    private final DocRefInfoService docRefInfoService;
    private final SecurityContext securityContext;
    private final FieldInfoResultPageFactory fieldInfoResultPageFactory;

    @Inject
    ProcessorTaskServiceImpl(final ProcessorTaskDao processorTaskDao,
                             final DocRefInfoService docRefInfoService,
                             final SecurityContext securityContext,
                             final FieldInfoResultPageFactory fieldInfoResultPageFactory) {
        this.processorTaskDao = processorTaskDao;
        this.docRefInfoService = docRefInfoService;
        this.securityContext = securityContext;
        this.fieldInfoResultPageFactory = fieldInfoResultPageFactory;
    }

    @Override
    public ResultPage<ProcessorTask> find(final ExpressionCriteria criteria) {
        return securityContext.secureResult(PERMISSION, () -> {
            final ResultPage<ProcessorTask> resultPage = processorTaskDao.find(criteria);
            resultPage.getValues().forEach(processorTask -> {
                final DocRef docRef = new DocRef(PipelineDoc.TYPE,
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
    public void search(final ExpressionCriteria criteria,
                       final FieldIndex fieldIndex,
                       final DateTimeSettings dateTimeSettings,
                       final ValuesConsumer valuesConsumer,
                       final ErrorConsumer errorConsumer) {
        securityContext.secure(PERMISSION, () ->
                processorTaskDao.search(criteria, fieldIndex, valuesConsumer));
    }

    @Override
    public String getDataSourceType() {
        return ProcessorTaskFields.PROCESSOR_TASK_PSEUDO_DOC_REF.getType();
    }

    @Override
    public List<DocRef> getDataSourceDocRefs() {
        if (securityContext.hasAppPermission(PERMISSION)) {
            return Collections.singletonList(ProcessorTaskFields.PROCESSOR_TASK_PSEUDO_DOC_REF);
        }
        return Collections.emptyList();
    }

    @Override
    public Optional<QueryField> getTimeField(final DocRef docRef) {
        return Optional.of(ProcessorTaskFields.CREATE_TIME);
    }

    @Override
    public ResultPage<QueryField> getFieldInfo(final FindFieldCriteria criteria) {
        if (!ProcessorTaskFields.PROCESSOR_TASK_PSEUDO_DOC_REF.equals(criteria.getDataSourceRef())) {
            return ResultPage.empty();
        }
        return fieldInfoResultPageFactory.create(criteria, getFields());
    }

    @Override
    public int getFieldCount(final DocRef docRef) {
        return NullSafe.size(getFields());
    }

    private List<QueryField> getFields() {
        return ProcessorTaskFields.getFields();
    }
}
