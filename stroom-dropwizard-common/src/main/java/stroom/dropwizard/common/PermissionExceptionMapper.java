package stroom.dropwizard.common;

import stroom.util.shared.PermissionException;

import io.dropwizard.jersey.errors.ErrorMessage;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;

public class PermissionExceptionMapper implements ExceptionMapper<PermissionException> {
    @Override
    public Response toResponse(PermissionException exception) {
        final int forbiddenCode = Status.FORBIDDEN.getStatusCode();
        return Response
                .status(forbiddenCode)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(new ErrorMessage(forbiddenCode, exception.getMessage()))
                .build();
    }
}
