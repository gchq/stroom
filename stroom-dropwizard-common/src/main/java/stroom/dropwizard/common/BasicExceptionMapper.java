/*
 * Copyright 2021 Crown Copyright
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
                // Must match the response status, else a client that reads the code from the body
                // reports something other than the status it was actually sent.
                .entity(new ErrorMessage(status.getStatusCode(),
                        throwable.getMessage(),
                        throwable.toString()))
                .build();
    }
}
