/*
 * Copyright 2022 Crown Copyright
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

package stroom.query.impl;

import stroom.dashboard.shared.DashboardSearchResponse;
import stroom.dashboard.shared.ValidateExpressionResult;
import stroom.docref.DocRef;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.query.shared.DownloadQueryResultsRequest;
import stroom.query.shared.QueryDoc;
import stroom.query.shared.QueryResource;
import stroom.query.shared.QuerySearchRequest;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.ResourceGeneration;

import java.util.List;
import javax.inject.Inject;
import javax.inject.Provider;

@AutoLogged
class QueryResourceImpl implements QueryResource {

    private final Provider<QueryService> queryServiceProvider;

    @Inject
    QueryResourceImpl(final Provider<QueryService> dashboardServiceProvider) {
        this.queryServiceProvider = dashboardServiceProvider;
    }

    @Override
    public QueryDoc fetch(final String uuid) {
        return queryServiceProvider.get().read(getDocRef(uuid));
    }

    @Override
    public QueryDoc update(final String uuid, final QueryDoc doc) {
        if (doc.getUuid() == null || !doc.getUuid().equals(uuid)) {
            throw new EntityServiceException("The document UUID must match the update UUID");
        }
        return queryServiceProvider.get().update(doc);
    }

    private DocRef getDocRef(final String uuid) {
        return DocRef.builder()
                .uuid(uuid)
                .type(QueryDoc.DOCUMENT_TYPE)
                .build();
    }

    @Override
    @AutoLogged(OperationType.UNLOGGED)
    public ValidateExpressionResult validateQuery(final String query) {
        return queryServiceProvider.get().validateQuery(query);
    }

//    @Override
//    public ResourceGeneration downloadQuery(final DashboardSearchRequest request) {
//        return queryServiceProvider.get().downloadQuery(request);
//    }

    @Override
    public ResourceGeneration downloadSearchResults(final DownloadQueryResultsRequest request) {
        return queryServiceProvider.get().downloadSearchResults(request);
    }

    @Override
    @AutoLogged(OperationType.UNLOGGED)
    public DashboardSearchResponse search(final QuerySearchRequest request) {
        return queryServiceProvider.get().search(request);
    }

//    @Override
//    @AutoLogged(OperationType.UNLOGGED)
//    public Boolean destroy(final DestroyQueryRequest request) {
//        return queryServiceProvider.get().destroy(request);
//    }

    @Override
    @AutoLogged(OperationType.UNLOGGED)
    public List<String> fetchTimeZones() {
        return queryServiceProvider.get().fetchTimeZones();
    }

//    @Override
//    @AutoLogged(OperationType.UNLOGGED)
//    public List<FunctionSignature> fetchFunctions() {
//        return queryServiceProvider.get().fetchFunctions();
//    }
}
