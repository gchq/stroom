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

package stroom.security.common.impl;

import jakarta.servlet.http.HttpServletRequest;
import org.jose4j.jwt.consumer.JwtContext;

import java.util.Map;
import java.util.Optional;

public interface JwtContextFactory {

    boolean hasToken(HttpServletRequest request);

    void removeAuthorisationEntries(final Map<String, String> headers);

    Map<String, String> createAuthorisationEntries(final String accessToken);

    Optional<JwtContext> getJwtContext(final HttpServletRequest request);

    /**
     * Extract the {@link JwtContext} from the passed JSON web token.
     * Will verify the jwt using the public keys and also check claims
     * like audience and subject.
     */
    Optional<JwtContext> getJwtContext(final String jwt);

    /**
     * Extract the {@link JwtContext} from the passed JSON web token.
     * Will verify the jwt using the public keys and also check claims
     * like audience and subject only if doVerification is true.
     */
    Optional<JwtContext> getJwtContext(final String jwt, final boolean doVerification);

}
