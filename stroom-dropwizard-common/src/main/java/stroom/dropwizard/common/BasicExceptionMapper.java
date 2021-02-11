package stroom.dropwizard.common;

import io.dropwizard.jersey.errors.ErrorMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;

public class BasicExceptionMapper implements ExceptionMapper<Throwable> {
    private static final Logger LOGGER = LoggerFactory.getLogger(BasicExceptionMapper.class);

    @Override
    public Response toResponse(final Throwable exception) {
        if (exception instanceof WebApplicationException) {
            WebApplicationException wae = (WebApplicationException) exception;
            return wae.getResponse();
        } else if (exception.getClass().getName().contains("AuthenticationException") ||
                exception.getClass().getName().contains("TokenException") ||
                exception.getClass().getName().contains("PermissionException")) {
            return createExceptionResponse(Status.FORBIDDEN, exception);
        }
        return createExceptionResponse(Status.INTERNAL_SERVER_ERROR, exception);
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
