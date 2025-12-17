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

package stroom.query.impl;

import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.query.api.FindResultStoreCriteria;
import stroom.query.api.QueryKey;
import stroom.query.shared.DestroyStoreRequest;
import stroom.query.shared.ResultStoreResource;
import stroom.query.shared.ResultStoreResponse;
import stroom.query.shared.UpdateStoreRequest;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

@AutoLogged(OperationType.UNLOGGED)
public class ResultStoreResourceImpl implements ResultStoreResource {

    private final Provider<ResultStoreService> resultStoreServiceProvider;

    @Inject
    public ResultStoreResourceImpl(final Provider<ResultStoreService> resultStoreServiceProvider) {
        this.resultStoreServiceProvider = resultStoreServiceProvider;
    }

    @AutoLogged(OperationType.UNLOGGED) // Called for each node so too noisy to log and of limited benefit
    @Override
    public ResultStoreResponse list(final String nodeName) {
        return resultStoreServiceProvider.get().list(nodeName);
    }

    @AutoLogged(OperationType.UNLOGGED) // Called for each node so too noisy to log and of limited benefit
    @Override
    public ResultStoreResponse find(final String nodeName, final FindResultStoreCriteria criteria) {
        return resultStoreServiceProvider.get().find(nodeName, criteria);
    }

    @Override
    public Boolean update(final String nodeName, final UpdateStoreRequest updateStoreRequest) {
        return resultStoreServiceProvider.get().update(nodeName, updateStoreRequest);
    }

    @Override
    public Boolean exists(final String nodeName, final QueryKey queryKey) {
        return resultStoreServiceProvider.get().exists(nodeName, queryKey);
    }

    //    @AutoLogged(value = OperationType.PROCESS, verb = "Terminating",
//            decorator = TerminateDecorator.class)
    @Override
    public Boolean terminate(final String nodeName, final QueryKey queryKey) {
        return resultStoreServiceProvider.get().terminate(nodeName, queryKey);
    }

    @Override
    public Boolean destroy(final String nodeName, final DestroyStoreRequest request) {
        return resultStoreServiceProvider.get().destroy(nodeName, request);
    }
}
