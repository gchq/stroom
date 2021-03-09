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

package stroom.dashboard.impl;

import stroom.dashboard.shared.DashboardDoc;
import stroom.dashboard.shared.DashboardResource;
import stroom.dashboard.shared.DashboardSearchResponse;
import stroom.dashboard.shared.DownloadQueryRequest;
import stroom.dashboard.shared.DownloadSearchResultsRequest;
import stroom.dashboard.shared.FunctionSignature;
import stroom.dashboard.shared.SearchBusPollRequest;
import stroom.dashboard.shared.ValidateExpressionResult;
import stroom.docref.DocRef;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.ResourceGeneration;

import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Provider;

@AutoLogged
class DashboardResourceImpl implements DashboardResource {

    private final Provider<DashboardService> dashboardServiceProvider;

    @Inject
    DashboardResourceImpl(final Provider<DashboardService> dashboardServiceProvider) {
        this.dashboardServiceProvider = dashboardServiceProvider;
    }

    @Override
    public DashboardDoc fetch(final String uuid) {
        return dashboardServiceProvider.get().read(getDocRef(uuid));
    }

    @Override
    public DashboardDoc update(final String uuid, final DashboardDoc doc) {
        if (doc.getUuid() == null || !doc.getUuid().equals(uuid)) {
            throw new EntityServiceException("The document UUID must match the update UUID");
        }
        return dashboardServiceProvider.get().update(doc);
    }

    private DocRef getDocRef(final String uuid) {
        return DocRef.builder()
                .uuid(uuid)
                .type(DashboardDoc.DOCUMENT_TYPE)
                .build();
    }

    @Override
    @AutoLogged(OperationType.UNLOGGED)
    public ValidateExpressionResult validateExpression(final String expressionString) {
        return dashboardServiceProvider.get().validateExpression(expressionString);
    }

    @Override
    public ResourceGeneration downloadQuery(final DownloadQueryRequest request) {
        return dashboardServiceProvider.get().downloadQuery(request);
    }

    @Override
    public ResourceGeneration downloadSearchResults(final DownloadSearchResultsRequest request) {
        return dashboardServiceProvider.get().downloadSearchResults(request);
    }

    @Override
    @AutoLogged(OperationType.UNLOGGED)
    public Set<DashboardSearchResponse> poll(final SearchBusPollRequest request) {
        return dashboardServiceProvider.get().poll(request);
    }

    @Override
    @AutoLogged(OperationType.UNLOGGED)
    public List<String> fetchTimeZones() {
        return dashboardServiceProvider.get().fetchTimeZones();
    }

    @Override
    @AutoLogged(OperationType.UNLOGGED)
    public List<FunctionSignature> fetchFunctions() {
        return dashboardServiceProvider.get().fetchFunctions();
    }

}
