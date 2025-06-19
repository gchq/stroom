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

import stroom.security.identity.exceptions.UnsupportedFilterException;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnsupportedFilterExceptionMapper implements ExceptionMapper<UnsupportedFilterException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(UnsupportedFilterExceptionMapper.class);

    @Override
    public Response toResponse(final UnsupportedFilterException exception) {
        LOGGER.debug(exception.getMessage());
        // 422 is UNPROCESSABLE_ENTITY - we understand the request and the request is fine but we don't support this yet
        return Response.status(422).entity(exception.getMessage()).build();
    }
}
