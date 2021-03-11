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

package stroom.searchable.impl;

import stroom.datasource.api.v2.DataSource;
import stroom.docref.DocRef;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchResponse;
import stroom.util.json.JsonUtil;

import com.codahale.metrics.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;

@AutoLogged
public class SearchableResourceImpl implements SearchableResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchableResourceImpl.class);

    private final Provider<SearchableService> searchableServiceProvider;

    @Inject
    public SearchableResourceImpl(final Provider<SearchableService> searchableServiceProvider) {
        this.searchableServiceProvider = searchableServiceProvider;
    }

    @Timed
    @Override
    public DataSource getDataSource(final DocRef docRef) {
        if (LOGGER.isDebugEnabled()) {
            String json = JsonUtil.writeValueAsString(docRef);
            LOGGER.debug("/dataSource called with docRef:\n{}", json);
        }
        return searchableServiceProvider.get().getDataSource(docRef);
    }

    @Timed
    @Override
    public SearchResponse search(final SearchRequest request) {
        if (LOGGER.isDebugEnabled()) {
            String json = JsonUtil.writeValueAsString(request);
            LOGGER.debug("/search called with searchRequest:\n{}", json);
        }

        return searchableServiceProvider.get().search(request);
    }

    @Timed
    @Override
    @AutoLogged(OperationType.UNLOGGED)
    public Boolean destroy(final QueryKey queryKey) {
        if (LOGGER.isDebugEnabled()) {
            String json = JsonUtil.writeValueAsString(queryKey);
            LOGGER.debug("/destroy called with queryKey:\n{}", json);
        }
        return searchableServiceProvider.get().destroy(queryKey);
    }
}
