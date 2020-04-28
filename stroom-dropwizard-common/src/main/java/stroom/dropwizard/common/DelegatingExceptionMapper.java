/*
 * This Class is a copy of https://github.com/dropwizard/dropwizard/blob/master/dropwizard-jersey/src/main/java/io/dropwizard/jersey/errors/LoggingExceptionMapper.java
 * which is also licenced under Apache 2.0
 *
 *
 * Copyright 2020 Crown Copyright
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
 *
 */
package stroom.dropwizard.common;

import io.dropwizard.jersey.errors.ErrorMessage;
import org.glassfish.jersey.spi.ExtendedExceptionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import java.util.Set;

public class DelegatingExceptionMapper implements ExtendedExceptionMapper<Throwable> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DelegatingExceptionMapper.class);

    private final Set<ExceptionMapper> exceptionMappers;

    @Inject
    DelegatingExceptionMapper(final Set<ExceptionMapper> exceptionMappers) {
        this.exceptionMappers = exceptionMappers;
    }

    @Override
    public Response toResponse(final Throwable throwable) {
        for (final ExceptionMapper exceptionMapper : exceptionMappers) {
            try {
                return exceptionMapper.toResponse(throwable);
            } catch (final RuntimeException e) {
                LOGGER.debug(e.getMessage(), e);
            }
        }

        LOGGER.debug(throwable.getMessage(), throwable);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode())
            .type(MediaType.APPLICATION_JSON_TYPE)
            .entity(new ErrorMessage(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), throwable.getMessage(), throwable.toString()))
            .build();
    }

    @Override
    public boolean isMappable(final Throwable exception) {
        return !(exception instanceof WebApplicationException);
    }
}
