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

package stroom.receive.common;

import stroom.meta.api.AttributeMap;
import stroom.security.api.UserIdentity;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Handles the authentication of HTTP requests into the datafeed API on stroom or stroom-proxy
 */
public interface RequestAuthenticator {

    /**
     * Authenticate an inbound request
     * @return
     */
    UserIdentity authenticate(final HttpServletRequest request,
                              final AttributeMap attributeMap);
//
//    /**
//     * Check for presence of tokens/certs on an inbound request that determines if authentication
//     * is possible.
//     * @return True if the request has the required heaader(s) for authentication.
//     */
//    boolean hasAuthenticationToken(final HttpServletRequest request);
//
//    /**
//     * Remove any headers relating to authorisations, e.g. 'Authorisation',
//     * from the passed map
//     */
//    void removeAuthorisationEntries(final Map<String, String> headers);
//
//    /**
//     * @return The authentication/authorisation headers to enable authentication with this user
//     */
//    Map<String, String> getAuthHeaders(final UserIdentity userIdentity);
//
//    /**
//     * @return The authentication/authorisation headers to enable authentication with the service
//     * account user
//     */
//    Map<String, String> getServiceUserAuthHeaders();
}
