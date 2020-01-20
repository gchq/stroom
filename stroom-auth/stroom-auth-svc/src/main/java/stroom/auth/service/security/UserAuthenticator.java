/*
 * Copyright 2017 Crown Copyright
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

package stroom.auth.service.security;

import io.dropwizard.auth.AuthenticationException;
import io.dropwizard.auth.Authenticator;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.JwtContext;

import java.util.Optional;

public class UserAuthenticator implements Authenticator<JwtContext, ServiceUser> {

    @Override
    public Optional<ServiceUser> authenticate(JwtContext context) throws AuthenticationException {
        //TODO: If we want to check anything else about the user we need to do it here.
        try {
            return Optional.of(new ServiceUser(
                    context.getJwtClaims().getSubject(),
                    context.getJwt()));
        } catch (MalformedClaimException e) {
            return Optional.empty();
        }
    }
}
