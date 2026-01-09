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

import stroom.docref.DocRef;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
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
    @Path("/findCredentialsWithPermissions")
    @Operation(
            summary = "Find credentials with permissions",
            operationId = "findCredentialsWithPermissions")
    ResultPage<CredentialWithPerms> findCredentialsWithPermissions(FindCredentialRequest request);

    /**
     * Returns the list of all credentials, paged as necessary.
     */
    @POST
    @Path("/findCredentials")
    @Operation(
            summary = "Find credentials",
            operationId = "findCredentials")
    ResultPage<Credential> findCredentials(FindCredentialRequest request);

    /**
     * Create a temporary doc ref to use for new credentials so that we can assign permissions straight away.
     */
    @GET
    @Path("/createDocRef")
    @Operation(
            summary = "Returns a newly generated DocRef that can be used to for permissions",
            operationId = "createDocRef")
    DocRef createDocRef();

    /**
     * Stores a credential in the DB. Errors are indicated in the return value.
     * Credential must already exist.
     */
    @POST
    @Path("/store")
    @Operation(
            summary = "Stores the credential",
            operationId = "storeCredential")
    Credential storeCredential(PutCredentialRequest credential);

    /**
     * Gets one credential by UUID.
     */
    @POST
    @Path("/getByUuid")
    @Operation(
            summary = "Returns the credential with the given UUID, if it exists, or null if it does not exist",
            operationId = "getCredentialByUuid")
    Credential getCredentialByUuid(String uuid);

    /**
     * Gets one credential by name.
     */
    @POST
    @Path("/getByName")
    @Operation(
            summary = "Returns the credential with the given name, if it exists, or null if it does not exist",
            operationId = "getCredentialByName")
    Credential getCredentialByName(String name);

    /**
     * Deletes one credential by UUID.
     */
    @POST
    @Path("/delete")
    @Operation(
            summary = "Deletes the credentials and secret with the given UUID",
            operationId = "deleteCredential")
    Boolean deleteCredentials(String uuid);
}
