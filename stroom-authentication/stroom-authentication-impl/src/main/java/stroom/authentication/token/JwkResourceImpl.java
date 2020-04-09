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

package stroom.authentication.token;

import event.logging.ObjectOutcome;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.PublicJsonWebKey;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotNull;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class JwkResourceImpl implements JwkResource {
    private final JwkCache jwkCache;
    private final JwkEventLog jwkEventLog;

    @Inject
    JwkResourceImpl(final JwkCache jwkCache,
                    final JwkEventLog jwkEventLog) {
        this.jwkCache = jwkCache;
        this.jwkEventLog = jwkEventLog;
    }

    @Override
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
//        jwkEventLog.view(
//                "getCerts",
//                httpServletRequest,
//                "anonymous",
//                objectOutcome,
//                "Read a token by the token ID.");

        return Response
                .status(Response.Status.OK)
                .entity(keys)
                .build();
    }
}
