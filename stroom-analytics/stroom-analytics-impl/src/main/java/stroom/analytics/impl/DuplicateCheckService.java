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

package stroom.analytics.impl;

import stroom.analytics.rule.impl.AnalyticRuleStore;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.DeleteDuplicateCheckRequest;
import stroom.analytics.shared.DuplicateCheckRows;
import stroom.analytics.shared.FindDuplicateCheckCriteria;
import stroom.docref.DocRef;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@AutoLogged(OperationType.UNLOGGED)
class DuplicateCheckService {

    private final AnalyticRuleStore analyticRuleStore;
    private final ExecutionScheduleDao executionScheduleDao;
    private final DuplicateCheckFactoryImpl duplicateCheckFactory;

    @Inject
    public DuplicateCheckService(final AnalyticRuleStore analyticRuleStore,
                                 final ExecutionScheduleDao executionScheduleDao,
                                 final DuplicateCheckFactoryImpl duplicateCheckFactory) {
        this.analyticRuleStore = analyticRuleStore;
        this.executionScheduleDao = executionScheduleDao;
        this.duplicateCheckFactory = duplicateCheckFactory;
    }

    public Set<String> getEnabledNodeNames(final DocRef analyticDocRef) {
        return executionScheduleDao.fetchExecutionNodes(analyticDocRef)
                .stream()
                .filter(ExecutionNode::isEnabled)
                .map(ExecutionNode::nodeName)
                .collect(Collectors.toSet());
    }

    public String getEnabledNodeName(final String analyticUuid) {
        final DocRef docRef = AnalyticRuleDoc.buildDocRef()
                .uuid(analyticUuid)
                .build();
        return getEnabledNodeName(docRef);
    }

    public String getEnabledNodeName(final DocRef analyticDocRef) {
        Objects.requireNonNull(analyticDocRef);
        final Set<String> nodeNames = getEnabledNodeNames(analyticDocRef);
        if (NullSafe.isEmptyCollection(nodeNames)) {
            return null;
        } else if (nodeNames.size() == 1) {
            return nodeNames.iterator().next();
        } else {
            throw new RuntimeException("Duplicate checking is not supported when executors are running " +
                                       "on multiple nodes");
        }
    }

    public DuplicateCheckRows find(final FindDuplicateCheckCriteria criteria) {
        return duplicateCheckFactory.fetchData(criteria);
    }

    public boolean delete(final DeleteDuplicateCheckRequest request) {
        return duplicateCheckFactory.delete(request);
    }

    public Optional<List<String>> fetchColumnNames(final String analyticUuid) {
        return duplicateCheckFactory.fetchColumnNames(analyticUuid);
    }
}
