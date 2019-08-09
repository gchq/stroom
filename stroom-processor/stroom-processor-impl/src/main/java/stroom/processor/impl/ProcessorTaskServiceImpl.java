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


import stroom.dashboard.expression.v1.Val;
import stroom.datasource.api.v2.AbstractField;
import stroom.datasource.api.v2.DataSource;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.processor.api.ProcessorTaskService;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.ProcessorTaskDataSource;
import stroom.processor.shared.ProcessorTaskSummary;
import stroom.searchable.api.Searchable;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.util.shared.BaseResultList;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.function.Consumer;

@Singleton
class ProcessorTaskServiceImpl implements ProcessorTaskService, Searchable {
    private static final String PERMISSION = PermissionNames.MANAGE_PROCESSORS_PERMISSION;

    private static final DocRef PROCESSOR_TASK_PSEUDO_DOC_REF = new DocRef("Searchable", "Processor Tasks", "Processor Tasks");

    private final ProcessorTaskDao processorTaskDao;
    private final SecurityContext securityContext;

    @Inject
    ProcessorTaskServiceImpl(final ProcessorTaskDao processorTaskDao,
                             final SecurityContext securityContext) {
        this.processorTaskDao = processorTaskDao;
        this.securityContext = securityContext;
    }

    @Override
    public BaseResultList<ProcessorTask> find(final ExpressionCriteria criteria) {
        return securityContext.secureResult(PERMISSION, () ->
                processorTaskDao.find(criteria));
    }

    @Override
    public BaseResultList<ProcessorTaskSummary> findSummary(final ExpressionCriteria criteria) {
        return securityContext.secureResult(PERMISSION, () ->
                processorTaskDao.findSummary(criteria));
    }

    @Override
    public void search(final ExpressionCriteria criteria, final AbstractField[] fields, final Consumer<Val[]> consumer) {
        securityContext.secure(PERMISSION, () ->
                processorTaskDao.search(criteria, fields, consumer));
    }

    @Override
    public DocRef getDocRef() {
        if (securityContext.hasAppPermission(PERMISSION)) {
            return PROCESSOR_TASK_PSEUDO_DOC_REF;
        }
        return null;
    }

    @Override
    public DataSource getDataSource() {
        return new DataSource(ProcessorTaskDataSource.getFields());
    }
}
