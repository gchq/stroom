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

package stroom.storedquery.impl;

import stroom.dashboard.shared.FindStoredQueryCriteria;
import stroom.dashboard.shared.StoredQuery;
import stroom.dashboard.shared.StoredQueryResource;
import stroom.event.logging.api.DocumentEventLog;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.storedquery.api.StoredQueryService;
import stroom.util.shared.ResultPage;

import event.logging.AdvancedQuery;
import event.logging.And;
import event.logging.Query;
import event.logging.Term;
import event.logging.TermCondition;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.function.Supplier;

@Singleton
@AutoLogged(OperationType.MANUALLY_LOGGED)
class StoredQueryResourceImpl implements StoredQueryResource {

    private final Provider<StoredQueryService> storedQueryServiceProvider;
    private final Provider<DocumentEventLog> documentEventLogProvider;

    @Inject
    StoredQueryResourceImpl(final Provider<StoredQueryService> storedQueryServiceProvider,
                            final Provider<DocumentEventLog> documentEventLogProvider) {
        this.storedQueryServiceProvider = storedQueryServiceProvider;
        this.documentEventLogProvider = documentEventLogProvider;
    }

    @Override
    public ResultPage<StoredQuery> find(final FindStoredQueryCriteria criteria) {
        final ResultPage<StoredQuery> result;
        final And.Builder<Void> andBuilder = And.builder();

        addCriteria(andBuilder, "Favorite", criteria::getFavourite);
        addCriteria(andBuilder, "ComponentId", criteria::getComponentId);
        addCriteria(andBuilder, "UserId", criteria::getOwner);
        addCriteria(andBuilder, "DashboardUuid", criteria::getDashboardUuid);

        final Query query = Query.builder()
                .withAdvanced(AdvancedQuery.builder()
                        .addAnd(andBuilder.build())
                        .build())
                .build();

        try {
            result = storedQueryServiceProvider.get().find(criteria);

            documentEventLogProvider.get().search(
                    criteria.getClass().getSimpleName(),
                    query,
                    StoredQuery.class.getSimpleName(),
                    result.getPageResponse(),
                    null);
        } catch (final RuntimeException e) {
            documentEventLogProvider.get().search(
                    criteria.getClass().getSimpleName(),
                    query,
                    StoredQuery.class.getSimpleName(),
                    null,
                    e);
            throw e;
        }

        return result;
    }

    private <T> void addCriteria(final And.Builder<Void> andBuilder,
                                 final String name,
                                 final Supplier<T> valueSupplier) {
        if (valueSupplier != null) {
            final T value = valueSupplier.get();
            if (value != null) {
                andBuilder.addTerm(Term.builder()
                        .withName(name)
                        .withCondition(TermCondition.EQUALS)
                        .withValue(value.toString())
                        .build());
            }
        }
    }

    @Override
    public StoredQuery create(final StoredQuery storedQuery) {
        final StoredQuery result;

        try {
            result = storedQueryServiceProvider.get().create(storedQuery);
            documentEventLogProvider.get().create(result, null);
        } catch (final RuntimeException e) {
            documentEventLogProvider.get().create(new StoredQuery(), e);
            throw e;
        }

        return result;
    }

    @Override
    public StoredQuery fetch(final StoredQuery storedQuery) {
        final StoredQuery result;
        try {
            result = storedQueryServiceProvider.get().fetch(storedQuery.getId());
            documentEventLogProvider.get().view(result, null);
        } catch (final RuntimeException e) {
            documentEventLogProvider.get().view(storedQuery, e);
            throw e;
        }

        return result;
    }

    @Override
    public StoredQuery update(final StoredQuery storedQuery) {
        final StoredQuery result;
        StoredQuery before = null;

        try {
            // Get the before version.
            before = storedQueryServiceProvider.get().fetch(storedQuery.getId());
            result = storedQueryServiceProvider.get().update(storedQuery);
            documentEventLogProvider.get().update(before, result, null);
        } catch (final RuntimeException e) {
            // Get the before version.
            documentEventLogProvider.get().update(before, storedQuery, e);
            throw e;
        }

        return result;
    }

    @Override
    public Boolean delete(final StoredQuery storedQuery) {
        try {
            storedQueryServiceProvider.get().delete(storedQuery.getId());
            documentEventLogProvider.get().delete(storedQuery, null);
        } catch (final RuntimeException e) {
            documentEventLogProvider.get().delete(storedQuery, e);
            throw e;
        }
        return true;
    }
}
