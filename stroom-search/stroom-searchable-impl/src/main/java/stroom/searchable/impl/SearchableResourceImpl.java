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

package stroom.searchable.impl;

import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.query.api.DestroyReason;
import stroom.query.api.QueryKey;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchResponse;
import stroom.query.common.v2.ResultStoreManager;
import stroom.query.common.v2.TerminateDecorator;

import com.codahale.metrics.annotation.Timed;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

@AutoLogged
public class SearchableResourceImpl implements SearchableResource {

    private final Provider<ResultStoreManager> searchResponseCreatorManagerProvider;

    @Inject
    public SearchableResourceImpl(
            final Provider<ResultStoreManager> searchResponseCreatorManagerProvider) {
        this.searchResponseCreatorManagerProvider = searchResponseCreatorManagerProvider;
    }

    @Timed
    @Override
    public SearchResponse search(final SearchRequest request) {
        return searchResponseCreatorManagerProvider.get().search(request);
    }

    @Timed
    @Override
    @AutoLogged(value = OperationType.PROCESS, verb = "Closing Query", decorator = TerminateDecorator.class)
    public Boolean destroy(final QueryKey queryKey) {
        return searchResponseCreatorManagerProvider.get().destroy(queryKey, DestroyReason.NO_LONGER_NEEDED);
    }
}
