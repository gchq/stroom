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

    /**
     * Fetches a document given its UUID.
     * @param uuid The UUID of the document to fetch
     * @return The GitRepoDoc matching the UUID.
     */
    @GET
    @Path("/{uuid}")
    @Operation(
            summary = "Fetch a Git repo doc by its UUID",
            operationId = "fetchGitRepo")
    GitRepoDoc fetch(@PathParam("uuid") String uuid);

    /**
     * Updates a GitRepoDoc in the DB.
     * @param uuid The UUID of the document.
     * @param doc The data of the document.
     * @return The updated document.
     */
    @PUT
    @Path("/{uuid}")
    @Operation(
            summary = "Update a Git repo doc",
            operationId = "updateGitRepo")
    GitRepoDoc update(@PathParam("uuid") String uuid,
                     @Parameter(description = "doc", required = true) GitRepoDoc doc);

    /**
     * Exports items from Stroom and pushes them into Git.
     * @param gitRepoPushDto Contains all the data needed for the push.
     * @return Object with ok/fail status and messages.
     */
    @POST
    @Path("/pushToGit")
    @Operation(
            summary = "Push items to Git",
            operationId = "pushToGit")
    GitRepoResponse pushToGit(
            @Parameter(description = "gitRepoPushDto", required = true) GitRepoPushDto gitRepoPushDto);

    /**
     * Pulls items from Git and imports them into Git.
     * @param gitRepoDoc The document holding the Git settings.
     * @return Object with ok/fail status and messages.
     */
    @POST
    @Path("/pullFromGit")
    @Operation(
            summary = "Pull items from Git",
            operationId = "pullFromGit")
    GitRepoResponse pullFromGit(
            @Parameter(description = "gitRepoDoc", required = true) GitRepoDoc gitRepoDoc);

    /**
     * Checks if updates are available for the given GitRepoDoc.
     * Intensive operation - requires downloading the whole Git repo.
     * Needs to become more sophisticated:
     * <ul>
     *     <li>Message of yes/no is insufficient - need boolean value</li>
     *     <li>A message listing each thing that has been updated would be nice</li>
     * </ul>
     * @param gitRepoDoc Settings for Git.
     * @return Object with ok/fail status and messages.
     */
    @POST
    @Path("/areUpdatesAvailable")
    @Operation(
            summary = "Check if any content has changed",
            operationId = "areUpdatesAvailable")
    GitRepoResponse areUpdatesAvailable(
            @Parameter(description = "gitRepoDoc", required = true) GitRepoDoc gitRepoDoc);
}
