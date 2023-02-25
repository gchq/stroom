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

package stroom.query.common.v2;

import stroom.datasource.api.v2.DataSource;
import stroom.datasource.api.v2.DataSourceResource;
import stroom.docref.DocRef;
import stroom.event.logging.api.EventActionDecorator;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.query.api.v2.DestroyReason;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchResponse;

import com.codahale.metrics.annotation.Timed;
import event.logging.ProcessAction;
import event.logging.ProcessEventAction;

import javax.inject.Provider;

@AutoLogged
public abstract class AbstractDataSourceResource implements DataSourceResource {

    private final Provider<ResultStoreManager> searchResponseCreatorManagerProvider;

    public AbstractDataSourceResource(
            final Provider<ResultStoreManager> searchResponseCreatorManagerProvider) {
        this.searchResponseCreatorManagerProvider = searchResponseCreatorManagerProvider;
    }

    @Timed
    @Override
    public DataSource getDataSource(final DocRef docRef) {
        return searchResponseCreatorManagerProvider.get().getDataSource(docRef);
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

    static class TerminateDecorator implements EventActionDecorator<ProcessEventAction> {

        @Override
        public ProcessEventAction decorate(final ProcessEventAction eventAction) {
            return eventAction.newCopyBuilder()
                    .withAction(ProcessAction.TERMINATE)
                    .build();
        }
    }
}
