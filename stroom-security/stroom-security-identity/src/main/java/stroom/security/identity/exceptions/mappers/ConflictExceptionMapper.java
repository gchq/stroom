/*
 *
 *   Copyright 2017 Crown Copyright
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */

package stroom.security.identity.exceptions.mappers;

import stroom.security.identity.exceptions.ConflictException;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Provider
public class ConflictExceptionMapper implements ExceptionMapper<ConflictException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConflictExceptionMapper.class);

    @Override
    public Response toResponse(final ConflictException exception) {
        LOGGER.debug(exception.getMessage());
        return Response.status(Response.Status.CONFLICT).entity(exception.getMessage()).build();
    }
}
