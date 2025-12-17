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

package stroom.search.impl;

import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;

import com.codahale.metrics.annotation.Timed;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.core.StreamingOutput;

@AutoLogged(OperationType.UNLOGGED)
public class RemoteSearchResourceImpl implements RemoteSearchResource {

    private final Provider<RemoteSearchService> remoteSearchServiceProvider;

    @Inject
    public RemoteSearchResourceImpl(final Provider<RemoteSearchService> remoteSearchServiceProvider) {
        this.remoteSearchServiceProvider = remoteSearchServiceProvider;
    }

    @Timed
    @Override
    public Boolean start(final NodeSearchTask nodeSearchTask) {
        return remoteSearchServiceProvider.get().start(nodeSearchTask);
    }

    @Timed
    @Override
    public StreamingOutput poll(final String queryKey) {
        return outputStream -> remoteSearchServiceProvider.get().poll(queryKey, outputStream);
    }

    @Timed
    @Override
    public Boolean destroy(final String queryKey) {
        return remoteSearchServiceProvider.get().destroy(queryKey);
    }
}
