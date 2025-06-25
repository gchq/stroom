package stroom.dropwizard.common;

import io.dropwizard.jersey.errors.ErrorMessage;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.ExceptionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BasicExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOGGER = LoggerFactory.getLogger(BasicExceptionMapper.class);

    @Override
    public Response toResponse(final Throwable exception) {
        if (exception instanceof WebApplicationException) {
            final WebApplicationException wae = (WebApplicationException) exception;
            return wae.getResponse();
        } else if (exception.getClass().getName().contains("AuthenticationException") ||
                exception.getClass().getName().contains("TokenException") ||
                exception.getClass().getName().contains("PermissionException")) {
            return createExceptionResponse(Status.FORBIDDEN, exception);
        } else {
            return createExceptionResponse(Status.INTERNAL_SERVER_ERROR, exception);
        }
    }

    private Response createExceptionResponse(final Response.Status status,
                                             final Throwable throwable) {
        LOGGER.debug(throwable.getMessage(), throwable);
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(new ErrorMessage(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                        throwable.getMessage(),
                        throwable.toString()))
                .build();
    }
}
