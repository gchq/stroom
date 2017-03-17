/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.resources;

import com.codahale.metrics.annotation.Timed;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.web.util.WebUtils;
import stroom.security.server.JWTAuthentication;
import stroom.security.server.JWTAuthenticationToken;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import java.util.Optional;

@Path("auth")
@Produces(MediaType.APPLICATION_JSON)
public class AuthenticationResource {

    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Timed
    // We're going to use BasicHttpAuthentication by passing the token in with the header.
    public JWTAuthenticationToken login(Request request){
        Optional<AuthenticationToken> authenticationToken = JWTAuthentication.createToken(WebUtils.getRequest(request));
        return authenticationToken.isPresent() ?
                (JWTAuthenticationToken) authenticationToken.get() :
                null; // TODO What else might we want to return?
    }
}
