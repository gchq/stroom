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


import stroom.processor.api.ProcessorTaskService;
import stroom.processor.shared.FindProcessorTaskCriteria;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.ProcessorTaskSummaryRow;
import stroom.security.api.Security;
import stroom.security.shared.PermissionNames;
import stroom.util.shared.BaseResultList;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
class ProcessorTaskServiceImpl implements ProcessorTaskService {
    private static final String PERMISSION = PermissionNames.MANAGE_PROCESSORS_PERMISSION;

    private final ProcessorTaskDao processorTaskDao;
    private final Security security;

    @Inject
    ProcessorTaskServiceImpl(final ProcessorTaskDao processorTaskDao,
                                   final Security security) {
        this.processorTaskDao = processorTaskDao;
        this.security = security;
    }

    @Override
    public BaseResultList<ProcessorTask> find(final FindProcessorTaskCriteria criteria) {
        return security.secureResult(PERMISSION, () ->
                processorTaskDao.find(criteria));
    }

    @Override
    public BaseResultList<ProcessorTaskSummaryRow> findSummary(final FindProcessorTaskCriteria criteria) {
        return security.secureResult(PERMISSION, () ->
                processorTaskDao.findSummary(criteria));
    }
}
