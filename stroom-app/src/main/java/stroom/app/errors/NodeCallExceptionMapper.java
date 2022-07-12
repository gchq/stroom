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

package stroom.app.errors;

import stroom.cluster.api.ClusterCallException;

import io.dropwizard.jersey.errors.ErrorMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

public class NodeCallExceptionMapper implements ExceptionMapper<ClusterCallException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(NodeCallExceptionMapper.class);

    @Override
    public Response toResponse(final ClusterCallException clusterCallException) {

        // Inter-node comms failure is not very exceptional so we don't want a big stack
        // every time.
        final long id = ThreadLocalRandom.current().nextLong();
        final String msg = String.format("%s [%016x]", clusterCallException.getMessage(), id);

        LOGGER.debug(msg, clusterCallException);
        LOGGER.error(msg + " Enable DEBUG for stacktrace");

        return Response.status(Response.Status.SERVICE_UNAVAILABLE.getStatusCode())
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(new ErrorMessage(msg))
                .build();
    }
}
