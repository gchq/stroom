package stroom.security.shared;

import stroom.util.shared.UserDesc;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.NoContentException;

// Intended to be inherited by other resource interfaces
public interface ApiKeyCheckResource {

    String VERIFY_API_KEY_PATH_PART = "/verifyApiKey";

    @POST
    @Path(ApiKeyCheckResource.VERIFY_API_KEY_PATH_PART)
    @Operation(
            summary = "Check if the passed API key is valid",
            operationId = "findApiKeysByCriteria")
    UserDesc verifyApiKey(@Parameter(description = "request", required = true) VerifyApiKeyRequest request)
            throws NoContentException;
}
