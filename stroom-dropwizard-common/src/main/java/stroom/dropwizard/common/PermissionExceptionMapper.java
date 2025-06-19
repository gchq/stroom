package stroom.dropwizard.common;

import stroom.util.shared.PermissionException;

import io.dropwizard.jersey.errors.ErrorMessage;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.ExceptionMapper;

public class PermissionExceptionMapper implements ExceptionMapper<PermissionException> {

    @Override
    public Response toResponse(final PermissionException exception) {
        final int forbiddenCode = Status.FORBIDDEN.getStatusCode();
        return Response
                .status(forbiddenCode)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(new ErrorMessage(forbiddenCode, exception.getMessage()))
                .build();
    }
}
