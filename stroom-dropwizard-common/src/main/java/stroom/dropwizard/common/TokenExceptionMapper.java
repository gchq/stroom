package stroom.dropwizard.common;

import stroom.security.api.TokenException;

import io.dropwizard.jersey.errors.ErrorMessage;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.ExceptionMapper;

public class TokenExceptionMapper implements ExceptionMapper<TokenException> {
    @Override
    public Response toResponse(final TokenException exception) {
        return Response
                .status(Status.FORBIDDEN)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(new ErrorMessage(Status.FORBIDDEN.getStatusCode(), exception.getMessage()))
                .build();
    }
}
