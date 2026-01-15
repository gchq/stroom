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

package stroom.security.identity.authenticate.api;

import stroom.security.identity.exceptions.BadRequestException;
import stroom.util.shared.ResourcePaths;

import jakarta.servlet.http.HttpServletRequest;

import java.net.URI;
import java.util.Optional;

public interface AuthenticationService {

    String SIGN_IN_URL_PATH = ResourcePaths.buildServletPath(ResourcePaths.SIGN_IN_PATH);

    AuthStatus currentAuthState(HttpServletRequest request);

    URI createSignInUri(String redirectUri);

    URI createErrorUri(BadRequestException badRequestException);


    // --------------------------------------------------------------------------------


    interface AuthStatus {

        Optional<AuthState> getAuthState();

        Optional<BadRequestException> getError();

        boolean isNew();
    }


    // --------------------------------------------------------------------------------


    interface AuthState {

        String getSubject();

        boolean isRequirePasswordChange();

        long getLastCredentialCheckMs();
    }
}
