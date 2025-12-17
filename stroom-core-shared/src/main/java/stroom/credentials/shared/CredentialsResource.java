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

package stroom.credentials.shared;

import stroom.util.shared.PageRequest;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.fusesource.restygwt.client.DirectRestService;

@Tag(name = "Credentials")
@Path("/credentials")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface CredentialsResource extends RestResource, DirectRestService {

    /**
     * Returns the list of all credentials, paged as necessary.
     */
    @POST
    @Path("/list")
    @Operation(
            summary = "Lists credentials",
            operationId = "listCredentials")
    ResultPage<CredentialsWithPerms> listCredentials(PageRequest pageRequest);

    /**
     * Creates credential in the DB. Errors are indicated in the return value.
     * Note that the UUID of the created credential will be new - UUIDs must
     * be created on the server for security.
     */
    @POST
    @Path("/create")
    @Operation(
            summary = "Creates the credential",
            operationId = "createCredential")
    CredentialsResponse createCredentials(CredentialsCreateRequest request);


    /**
     * Stores a credential in the DB. Errors are indicated in the return value.
     * Credential must already exist.
     */
    @POST
    @Path("/store")
    @Operation(
            summary = "Stores the credential",
            operationId = "storeCredential")
    CredentialsResponse storeCredentials(Credentials credentials);

    /**
     * Gets one credential by UUID.
     */
    @POST
    @Path("/get")
    @Operation(
            summary = "Returns the credentials with the given UUID, if it exists, or null if it does not exist",
            operationId = "getCredentialsWithUuid")
    CredentialsResponse getCredentials(String uuid);

    /**
     * Deletes one credential by UUID.
     */
    @POST
    @Path("/delete")
    @Operation(
            summary = "Deletes the credentials and secret with the given UUID",
            operationId = "deleteCredential")
    CredentialsResponse deleteCredentials(String uuid);

    /**
     * Stores the secret to the database.
     */
    @POST
    @Path("/storeSecret")
    @Operation(
            summary = "Stores the secret in the database under the given ID",
            operationId = "storeSecret")
    CredentialsResponse storeSecret(CredentialsSecret secret);

    /**
     * Gets the secret from the database.
     */
    @POST
    @Path("/getSecret")
    @Operation(
            summary = "Gets a secret from the database given the ID",
            operationId = "getSecret")
    CredentialsResponse getSecret(String credentialsId);

}
