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

package stroom.data.retention.impl;

import stroom.data.retention.shared.DataRetentionDeleteSummaryRequest;
import stroom.data.retention.shared.DataRetentionDeleteSummaryResponse;
import stroom.data.retention.shared.DataRetentionRules;
import stroom.data.retention.shared.DataRetentionRulesResource;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.api.StroomEventLoggingUtil;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.meta.api.MetaService;

import event.logging.AdvancedQuery;
import event.logging.Criteria;
import event.logging.DeleteEventAction;
import event.logging.Query;
import event.logging.Term;
import event.logging.TermCondition;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

@AutoLogged
class DataRetentionRulesResourceImpl implements DataRetentionRulesResource {

    private final Provider<DataRetentionRulesService> dataRetentionRulesServiceProvider;
    private final Provider<MetaService> metaServiceProvider;
    private final Provider<StroomEventLoggingService> eventLoggingServiceProvider;

    @Inject
    DataRetentionRulesResourceImpl(final Provider<DataRetentionRulesService> dataRetentionRulesServiceProvider,
                                   final Provider<MetaService> metaServiceProvider,
                                   final Provider<StroomEventLoggingService> eventLoggingServiceProvider) {
        this.dataRetentionRulesServiceProvider = dataRetentionRulesServiceProvider;
        this.metaServiceProvider = metaServiceProvider;
        this.eventLoggingServiceProvider = eventLoggingServiceProvider;
    }

    @Override
    public DataRetentionRules fetch() {
        return dataRetentionRulesServiceProvider.get().getOrCreate();
    }

    @Override
    public DataRetentionRules update(final DataRetentionRules dataRetentionRules) {
        return dataRetentionRulesServiceProvider.get().writeDocument(dataRetentionRules);
    }

    @Override
    public DataRetentionDeleteSummaryResponse getRetentionDeletionSummary(
            @Parameter(description = "request", required = true) final DataRetentionDeleteSummaryRequest request) {
        return new DataRetentionDeleteSummaryResponse(
                metaServiceProvider.get()
                        .getRetentionDeleteSummary(
                                request.getQueryId(),
                                request.getDataRetentionRules(),
                                request.getCriteria()),
                request.getQueryId());
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public Boolean cancelQuery(final String queryId) {
        return eventLoggingServiceProvider.get()
                .loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "cancelQuery"))
                .withDescription("Cancelling data retention delete summary with query id " + queryId)
                .withDefaultEventAction(DeleteEventAction.builder()
                        .addCriteria(Criteria.builder()
                                .withQuery(Query.builder()
                                        .withAdvanced(AdvancedQuery.builder()
                                                .addTerm(Term.builder()
                                                        .withName("queryId")
                                                        .withCondition(TermCondition.EQUALS)
                                                        .withValue(queryId)
                                                        .build())
                                                .build())
                                        .build())
                                .build())
                        .build())
                .withSimpleLoggedResult(() -> {
                    return metaServiceProvider.get()
                            .cancelRetentionDeleteSummary(queryId);
                })
                .getResultAndLog();
    }
}
