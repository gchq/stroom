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

package stroom.search.elastic.search;

import stroom.datasource.api.v2.DataSource;
import stroom.docref.DocRef;
import stroom.event.logging.api.EventActionDecorator;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchResponse;
import stroom.search.elastic.ElasticIndexService;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.codahale.metrics.annotation.Timed;
import event.logging.ProcessAction;
import event.logging.ProcessEventAction;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
@AutoLogged
public class ElasticIndexQueryResourceImpl implements ElasticIndexQueryResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ElasticIndexQueryResourceImpl.class);

    private final Provider<ElasticIndexService> serviceProvider;

    @Inject
    ElasticIndexQueryResourceImpl(final Provider<ElasticIndexService> serviceProvider) {
        this.serviceProvider = serviceProvider;
    }

    @Timed
    @Override
    public DataSource getDataSource(final DocRef docRef) {
        if (LOGGER.isDebugEnabled()) {
            String json = JsonUtil.writeValueAsString(docRef);
            LOGGER.debug("/dataSource called with docRef:\n{}", json);
        }
        return serviceProvider.get().getDataSource(docRef);
    }

    @Timed
    @Override
    public SearchResponse search(final SearchRequest request) {
        if (LOGGER.isDebugEnabled()) {
            String json = JsonUtil.writeValueAsString(request);
            LOGGER.debug("/search called with searchRequest:\n{}", json);
        }
        return serviceProvider.get().search(request);
    }

    @Timed
    @Override
    @AutoLogged(OperationType.UNLOGGED)
    public Boolean keepAlive(final QueryKey queryKey) {
        if (LOGGER.isDebugEnabled()) {
            String json = JsonUtil.writeValueAsString(queryKey);
            LOGGER.debug("/keepAlive called with queryKey:\n{}", json);
        }
        return serviceProvider.get().keepAlive(queryKey);
    }

    @Timed
    @Override
    @AutoLogged(value = OperationType.PROCESS, verb = "Closing Query", decorator = TerminateDecorator.class)
    public Boolean destroy(final QueryKey queryKey) {
        if (LOGGER.isDebugEnabled()) {
            String json = JsonUtil.writeValueAsString(queryKey);
            LOGGER.debug("/destroy called with queryKey:\n{}", json);
        }
        return serviceProvider.get().destroy(queryKey);
    }

    static class TerminateDecorator implements EventActionDecorator<ProcessEventAction> {

        @Override
        public ProcessEventAction decorate(final ProcessEventAction eventAction) {
            return eventAction.newCopyBuilder()
                    .withAction(ProcessAction.TERMINATE)
                    .build();
        }
    }
}
