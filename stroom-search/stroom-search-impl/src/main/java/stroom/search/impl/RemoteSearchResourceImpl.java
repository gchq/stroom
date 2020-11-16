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

import stroom.util.shared.ResourcePaths;

import io.swagger.annotations.Api;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.StreamingOutput;

@Api(value = "remoteSearch - /v1")
@Path("/remoteSearch" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RemoteSearchResourceImpl implements RemoteSearchResource {
    private final RemoteSearchService remoteSearchService;

    @Inject
    public RemoteSearchResourceImpl(final RemoteSearchService remoteSearchService) {
        this.remoteSearchService = remoteSearchService;
    }

    @Override
    public Boolean start(final ClusterSearchTask clusterSearchTask) {
        return remoteSearchService.start(clusterSearchTask);
    }

    @Override
    public StreamingOutput poll(final String queryKey) {
        return outputStream -> remoteSearchService.poll(queryKey, outputStream);
    }

    @Override
    public Boolean destroy(final String queryKey) {
        return remoteSearchService.destroy(queryKey);
    }
}