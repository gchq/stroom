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

package stroom.security.identity.exceptions.mappers;


import stroom.config.common.UriFactory;
import stroom.security.identity.authenticate.api.AuthenticationService;
import stroom.security.identity.config.IdentityConfig;
import stroom.security.identity.exceptions.NoCertificateException;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("unused")
class NoCertificateExceptionMapper implements ExceptionMapper<NoCertificateException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(NoCertificateExceptionMapper.class);

    private final UriFactory uriFactory;
    private final IdentityConfig authenticationConfig;

    @SuppressWarnings("unused")
    @Inject
    NoCertificateExceptionMapper(final UriFactory uriFactory,
                                 final IdentityConfig authenticationConfig) {
        this.uriFactory = uriFactory;
        this.authenticationConfig = authenticationConfig;
    }

    @Override
    public Response toResponse(final NoCertificateException exception) {
        LOGGER.debug("Unable to create a token for this user. Redirecting to login as a backup method.", exception);
        return Response.temporaryRedirect(uriFactory.uiUri(AuthenticationService.SIGN_IN_URL_PATH)).build();
    }
}
