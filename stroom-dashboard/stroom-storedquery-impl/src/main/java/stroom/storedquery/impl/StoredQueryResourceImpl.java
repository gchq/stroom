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

package stroom.storedquery.impl;

import stroom.dashboard.shared.FindStoredQueryCriteria;
import stroom.dashboard.shared.StoredQuery;
import stroom.dashboard.shared.StoredQueryResource;
import stroom.event.logging.api.DocumentEventLog;
import stroom.security.api.SecurityContext;
import stroom.util.shared.ResultPage;

import event.logging.BaseAdvancedQueryOperator.And;
import event.logging.Query;
import event.logging.Query.Advanced;

import javax.inject.Inject;

class StoredQueryResourceImpl implements StoredQueryResource {
    private final StoredQueryServiceImpl storedQueryService;
    private final DocumentEventLog documentEventLog;
    private final SecurityContext securityContext;

    @Inject
    StoredQueryResourceImpl(final StoredQueryServiceImpl storedQueryService,
                            final DocumentEventLog documentEventLog,
                            final SecurityContext securityContext) {
        this.storedQueryService = storedQueryService;
        this.documentEventLog = documentEventLog;
        this.securityContext = securityContext;
    }

    @Override
    public ResultPage<StoredQuery> find(final FindStoredQueryCriteria criteria) {
        ResultPage<StoredQuery> result;

        final Query query = new Query();
        final Advanced advanced = new Advanced();
        query.setAdvanced(advanced);
        final And and = new And();
        advanced.getAdvancedQueryItems().add(and);

        try {
            result = storedQueryService.find(criteria);
            documentEventLog.search(criteria.getClass().getSimpleName(), query, StoredQuery.class.getSimpleName(), result.getPageResponse(), null);
        } catch (final RuntimeException e) {
            documentEventLog.search(criteria.getClass().getSimpleName(), query, StoredQuery.class.getSimpleName(), null, e);
            throw e;
        }

        return result;
    }

    @Override
    public StoredQuery create(final StoredQuery storedQuery) {
        StoredQuery result;

        try {
            result = storedQueryService.create(storedQuery);
            documentEventLog.create(result, null);
        } catch (final RuntimeException e) {
            documentEventLog.create(new StoredQuery(), e);
            throw e;
        }

        return result;
    }

    @Override
    public StoredQuery read(final StoredQuery storedQuery) {
        StoredQuery result;
        try {
            result = storedQueryService.fetch(storedQuery.getId());
            documentEventLog.view(result, null);
        } catch (final RuntimeException e) {
            documentEventLog.view(storedQuery, e);
            throw e;
        }

        return result;
    }

    @Override
    public StoredQuery update(final StoredQuery storedQuery) {
        return securityContext.secureResult(() -> {
            StoredQuery result;
            StoredQuery before = null;

            try {
                // Get the before version.
                before = storedQueryService.fetch(storedQuery.getId());
                result = storedQueryService.update(storedQuery);
                documentEventLog.update(before, result, null);
            } catch (final RuntimeException e) {
                // Get the before version.
                documentEventLog.update(before, storedQuery, e);
                throw e;
            }

            return result;
        });
    }

    @Override
    public Boolean delete(final StoredQuery storedQuery) {
        try {
            storedQueryService.delete(storedQuery.getId());
            documentEventLog.delete(storedQuery, null);
        } catch (final RuntimeException e) {
            documentEventLog.delete(storedQuery, e);
            throw e;
        }
        return true;
    }
}