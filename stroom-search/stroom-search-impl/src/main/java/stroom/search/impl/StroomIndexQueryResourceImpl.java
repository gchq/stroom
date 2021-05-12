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

package stroom.search.impl;

import stroom.datasource.api.v2.DataSource;
import stroom.docref.DocRef;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.index.impl.StroomIndexQueryResource;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchResponse;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContextFactory;

import com.codahale.metrics.annotation.Timed;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Provider;

@AutoLogged
public class StroomIndexQueryResourceImpl implements StroomIndexQueryResource {

    private final Provider<StroomIndexQueryService> stroomIndexQueryServiceProvider;
    private final ExecutorProvider executorProvider;
    private final TaskContextFactory taskContextFactory;

    @Inject
    public StroomIndexQueryResourceImpl(final Provider<StroomIndexQueryService> stroomIndexQueryServiceProvider,
                                        final ExecutorProvider executorProvider,
                                        final TaskContextFactory taskContextFactory) {
        this.stroomIndexQueryServiceProvider = stroomIndexQueryServiceProvider;
        this.executorProvider = executorProvider;
        this.taskContextFactory = taskContextFactory;
    }

    @Override
    @Timed
    public DataSource getDataSource(final DocRef docRef) {
        final Supplier<DataSource> supplier = taskContextFactory.contextResult("Get Data Source", taskContext ->
                stroomIndexQueryServiceProvider.get().getDataSource(docRef));

        final Executor executor = executorProvider.get();
        final CompletableFuture<DataSource> completableFuture = CompletableFuture.supplyAsync(supplier, executor);
        try {
            return completableFuture.get();
        } catch (final InterruptedException | ExecutionException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    @Timed
    public SearchResponse search(final SearchRequest request) {
        final Supplier<SearchResponse> supplier = taskContextFactory.contextResult("Search", taskContext ->
                stroomIndexQueryServiceProvider.get().search(request));

        final Executor executor = executorProvider.get();
        final CompletableFuture<SearchResponse> completableFuture = CompletableFuture.supplyAsync(supplier, executor);
        try {
            return completableFuture.get();
        } catch (final InterruptedException | ExecutionException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    @Timed
    @AutoLogged(OperationType.UNLOGGED)
    public Boolean destroy(final QueryKey queryKey) {
        final Supplier<Boolean> supplier = taskContextFactory.contextResult("Destroy", taskContext ->
                stroomIndexQueryServiceProvider.get().destroy(queryKey));

        final Executor executor = executorProvider.get();
        final CompletableFuture<Boolean> completableFuture = CompletableFuture.supplyAsync(supplier, executor);
        try {
            return completableFuture.get();
        } catch (final InterruptedException | ExecutionException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
