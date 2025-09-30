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
    ResultPage<Credentials> list(PageRequest pageRequest);

    /**
     * Stores a credential in the DB. Errors are indicated in the return value.
     */
    @POST
    @Path("/store")
    @Operation(
            summary = "Stores the credential",
            operationId = "storeCredential")
    CredentialsResponse store(Credentials credentials);

    /**
     * Gets one credential by UUID.
     */
    @POST
    @Path("/get")
    @Operation(
            summary = "Returns the credentials with the given UUID, if it exists, or null if it does not exist",
            operationId="getCredentialsWithUuid")
    CredentialsResponse get(String uuid);

    /**
     * Deletes one credential by UUID.
     */
    @POST
    @Path("/delete")
    @Operation(
            summary = "Deletes the credentials with the given UUID",
            operationId="deleteCredential")
    CredentialsResponse delete(String uuid);

}
