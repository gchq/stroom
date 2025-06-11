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

package stroom.gitrepo.shared;

import stroom.util.shared.FetchWithUuid;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.fusesource.restygwt.client.DirectRestService;

/**
 * Provides the REST API for the GitRepo UI elements.
 */
@Tag(name = "GitRepos")
@Path("/gitRepo" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface GitRepoResource extends RestResource, DirectRestService, FetchWithUuid<GitRepoDoc> {

    @GET
    @Path("/{uuid}")
    @Operation(
            summary = "Fetch a GIT repo doc by its UUID",
            operationId = "fetchGitRepo")
    GitRepoDoc fetch(@PathParam("uuid") String uuid);

    @PUT
    @Path("/{uuid}")
    @Operation(
            summary = "Update a GIT repo doc",
            operationId = "updateGitRepo")
    GitRepoDoc update(@PathParam("uuid") String uuid,
                     @Parameter(description = "doc", required = true) GitRepoDoc doc);

    @POST
    @Path("/pushToGit")
    @Operation(
            summary = "Push items to Git",
            operationId = "pushToGit")
    GitRepoResponse pushToGit(
            @Parameter(description = "gitRepoPushDto", required = true) GitRepoPushDto gitRepoPushDto);

    @POST
    @Path("/pullFromGit")
    @Operation(
            summary = "Pull items from Git",
            operationId = "pullFromGit")
    GitRepoResponse pullFromGit(
            @Parameter(description = "gitRepoDoc", required = true) GitRepoDoc gitRepoDoc);

    @POST
    @Path("/areUpdatesAvailable")
    @Operation(
            summary = "Check if any content has changed",
            operationId = "areUpdatesAvailable")
    GitRepoResponse areUpdatesAvailable(
            @Parameter(description = "gitRepoDoc", required = true) GitRepoDoc gitRepoDoc);
}
