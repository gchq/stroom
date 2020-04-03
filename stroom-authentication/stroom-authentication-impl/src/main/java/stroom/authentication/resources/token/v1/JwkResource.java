/*
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
 */

package stroom.authentication.resources.token.v1;

import com.codahale.metrics.annotation.Timed;
import event.logging.ObjectOutcome;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.PublicJsonWebKey;
import stroom.authentication.JwkCache;
import stroom.authentication.service.eventlogging.StroomEventLoggingService;
import stroom.util.shared.RestResource;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Singleton
@Path("/oauth2/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Api(tags = {"ApiKey"})
public class JwkResource implements RestResource {
    private final JwkCache jwkCache;
    private final StroomEventLoggingService stroomEventLoggingService;

    @Inject
    JwkResource(final JwkCache jwkCache,
                final StroomEventLoggingService stroomEventLoggingService) {
        this.jwkCache = jwkCache;
        this.stroomEventLoggingService = stroomEventLoggingService;
    }

    @ApiOperation(
            value = "Provides access to this service's current public key. " +
                    "A client may use these keys to verify JWTs issued by this service.",
            response = String.class,
            tags = {"ApiKey"})
    @GET
    @Path("/certs")
    @Timed
    public final Response getCerts(
            @Context @NotNull HttpServletRequest httpServletRequest) {
        final List<PublicJsonWebKey> list = jwkCache.get();
        final List<Map<String, Object>> maps = list.stream()
                .map(jwk -> jwk.toParams(JsonWebKey.OutputControlLevel.PUBLIC_ONLY))
                .collect(Collectors.toList());

        Map<String, List<Map<String, Object>>> keys = new HashMap<>();
        keys.put("keys", maps);

        event.logging.Object object = new event.logging.Object();
        object.setName("PublicKey");
        ObjectOutcome objectOutcome = new ObjectOutcome();
        objectOutcome.getObjects().add(object);
        stroomEventLoggingService.view(
                "getCerts",
                httpServletRequest,
                "anonymous",
                objectOutcome,
                "Read a token by the token ID.");

        return Response
                .status(Response.Status.OK)
                .entity(keys)
                .build();
    }
}
