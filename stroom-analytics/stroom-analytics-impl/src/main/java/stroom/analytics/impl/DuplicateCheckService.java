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

package stroom.analytics.impl;

import stroom.analytics.shared.AbstractAnalyticRuleDoc;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.DeleteDuplicateCheckRequest;
import stroom.analytics.shared.DuplicateCheckRows;
import stroom.analytics.shared.ExecutionSchedule;
import stroom.analytics.shared.ExecutionScheduleRequest;
import stroom.analytics.shared.FindDuplicateCheckCriteria;
import stroom.docref.DocRef;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;

import java.util.Set;
import java.util.stream.Collectors;

@AutoLogged(OperationType.UNLOGGED)
class DuplicateCheckService {

    private final AnalyticLoader analyticLoader;
    private final ExecutionScheduleDao executionScheduleDao;
    private final DuplicateCheckFactoryImpl duplicateCheckFactory;

    @Inject
    public DuplicateCheckService(final AnalyticLoader analyticLoader,
                                 final ExecutionScheduleDao executionScheduleDao,
                                 final DuplicateCheckFactoryImpl duplicateCheckFactory) {
        this.analyticLoader = analyticLoader;
        this.executionScheduleDao = executionScheduleDao;
        this.duplicateCheckFactory = duplicateCheckFactory;
    }

    public String getNodeName(final String analyticUuid) {
        String nodeName = null;
        final DocRef docRef = DocRef
                .builder()
                .type(AnalyticRuleDoc.TYPE)
                .uuid(analyticUuid)
                .build();
        final AbstractAnalyticRuleDoc analyticRuleDoc = analyticLoader.load(docRef);
        if (analyticRuleDoc != null) {
            // Load schedules for the analytic.
            final ExecutionScheduleRequest request = ExecutionScheduleRequest
                    .builder()
                    .ownerDocRef(docRef)
                    .build();

            final ResultPage<ExecutionSchedule> executionSchedules =
                    executionScheduleDao.fetchExecutionSchedule(request);
            final Set<String> nodes = executionSchedules
                    .stream()
                    .map(ExecutionSchedule::getNodeName)
                    .collect(Collectors.toSet());
            if (nodes.size() > 1) {
                throw new RuntimeException("Duplicate checking is not supported when executors are running " +
                                           "on multiple nodes");
            }

            if (nodes.size() == 1) {
                nodeName = nodes.iterator().next();
            }
        }

        return nodeName;
    }

    public DuplicateCheckRows find(final FindDuplicateCheckCriteria criteria) {
        return duplicateCheckFactory.fetchData(criteria);
    }

    public boolean delete(final DeleteDuplicateCheckRequest request) {
        return duplicateCheckFactory.delete(request);
    }
}
