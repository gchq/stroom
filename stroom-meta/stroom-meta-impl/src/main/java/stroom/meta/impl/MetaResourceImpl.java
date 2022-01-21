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

package stroom.meta.impl;

import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.api.StroomEventLoggingUtil;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.MetaResource;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.SelectionSummary;
import stroom.meta.shared.UpdateStatusRequest;
import stroom.query.api.v2.ExpressionOperator;
import stroom.util.shared.ResultPage;

import event.logging.SearchEventAction;

import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;

@AutoLogged
class MetaResourceImpl implements MetaResource {

    private final Provider<MetaService> metaServiceProvider;
    private final Provider<StroomEventLoggingService> eventLoggingServiceProvider;

    @Inject
    MetaResourceImpl(Provider<MetaService> metaServiceProvider,
                     final Provider<StroomEventLoggingService> eventLoggingServiceProvider) {
        this.metaServiceProvider = metaServiceProvider;
        this.eventLoggingServiceProvider = eventLoggingServiceProvider;
    }


    @Override
    @AutoLogged(OperationType.MANUALLY_LOGGED)
    public Integer updateStatus(final UpdateStatusRequest request) {
        final StroomEventLoggingService eventLoggingService = eventLoggingServiceProvider.get();

        final ExpressionOperator expression = request.getCriteria().getExpression();

        final String currentStatus = (request.getCurrentStatus() != null)
                ?
                request.getCurrentStatus().getDisplayValue()
                : "selected";
        final String newStatus = (request.getNewStatus() != null)
                ?
                request.getNewStatus().getDisplayValue()
                : "unspecified / unknown";

        final SearchEventAction action = SearchEventAction.builder()
                .withQuery(StroomEventLoggingUtil.convertExpression(expression))
                .build();

        return eventLoggingService.loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "updateStatus"))
                .withDescription("Modify the status of " + currentStatus + " streams to " + newStatus)
                .withDefaultEventAction(action)
                .withSimpleLoggedResult(() -> metaServiceProvider.get().updateStatus(
                        request.getCriteria(),
                        request.getCurrentStatus(),
                        request.getNewStatus()))
                .getResultAndLog();
    }

    @AutoLogged(OperationType.SEARCH)
    @Override
    public ResultPage<MetaRow> findMetaRow(final FindMetaCriteria criteria) {
        return metaServiceProvider.get().findDecoratedRows(criteria);
    }

    @AutoLogged(OperationType.SEARCH)
    @Override
    public SelectionSummary getSelectionSummary(final FindMetaCriteria criteria) {
        return metaServiceProvider.get().getSelectionSummary(criteria);
    }

    @AutoLogged(OperationType.SEARCH)
    @Override
    public SelectionSummary getReprocessSelectionSummary(final FindMetaCriteria criteria) {
        return metaServiceProvider.get().getReprocessSelectionSummary(criteria);
    }

    @Override
    @AutoLogged(OperationType.VIEW)
    public List<String> getTypes() {
        return metaServiceProvider
                .get()
                .getTypes()
                .stream()
                .sorted()
                .collect(Collectors.toList());
    }
}
