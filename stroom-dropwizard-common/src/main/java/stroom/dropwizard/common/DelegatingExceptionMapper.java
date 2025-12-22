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

package stroom.dropwizard.common;

import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import org.glassfish.jersey.spi.ExtendedExceptionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public class DelegatingExceptionMapper implements ExtendedExceptionMapper<Throwable> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DelegatingExceptionMapper.class);

    private final Set<ExceptionMapper> exceptionMappers;
    private final ExceptionMapper<Throwable> basicExceptionMapper = new BasicExceptionMapper();

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

        return basicExceptionMapper.toResponse(throwable);
    }

    @Override
    public boolean isMappable(final Throwable exception) {
        return !(exception instanceof WebApplicationException);
    }
}
