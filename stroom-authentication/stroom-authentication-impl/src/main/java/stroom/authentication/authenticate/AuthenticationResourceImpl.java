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

package stroom.authentication.authenticate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.authentication.exceptions.NoSuchUserException;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.net.URI;
import java.net.URISyntaxException;

import static javax.ws.rs.core.Response.seeOther;
import static javax.ws.rs.core.Response.status;

// TODO : @66 Add audit logging
class AuthenticationResourceImpl implements AuthenticationResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationResourceImpl.class);

    private AuthenticationServiceImpl service;

    @Inject
    AuthenticationResourceImpl(final AuthenticationServiceImpl service) {
        this.service = service;
    }

    @Override
    public Response login(final HttpServletRequest request,
                          final String redirectUri,
                          final Credentials credentials) {
        LOGGER.debug("Received a login request");
        final LoginResponse loginResponse = service.handleLogin(credentials, request, redirectUri);
        return status(loginResponse.getResponseCode())
                .entity(loginResponse)
                .build();
    }

    @Override
    public Response logout(
            final HttpServletRequest request,
            final String redirectUri) throws URISyntaxException {
        LOGGER.debug("Received a logout request");
        final String postLogoutUrl = service.logout(request, redirectUri);
        return seeOther(new URI(postLogoutUrl)).build();
    }

    @Override
    public Response resetEmail(
            final HttpServletRequest request,
            final String emailAddress) throws NoSuchUserException {
        final boolean resetEmailSent = service.resetEmail(emailAddress);
        return resetEmailSent ?
                status(Status.NO_CONTENT).build() :
                status(Status.NOT_FOUND).entity("User does not exist").build();
    }

//    @Override
//    public Response verifyToken(final String token) {
//        var usersEmail = tokenService.verifyToken(token);
//        return usersEmail
//                .map(s -> status(Status.OK).entity(s).build())
//                .orElseGet(() -> status(Status.UNAUTHORIZED).build());
//    }

    @Override
    public final Response changePassword(final HttpServletRequest request,
                                         final ChangePasswordRequest changePasswordRequest) {
        final ChangePasswordResponse changePasswordResponse = service.changePassword(request, changePasswordRequest);
        return Response.status(Status.OK).entity(changePasswordResponse).build();
    }

    @Override
    public final Response resetPassword(final ResetPasswordRequest req) {
        final ChangePasswordResponse changePasswordResponse = service.resetPassword(req);
        if (changePasswordResponse != null) {
            return Response.status(Status.OK).entity(changePasswordResponse).build();
        } else {
            return Response.status(Status.UNAUTHORIZED).build();
        }
    }

    @Override
    public final Response needsPasswordChange(final String email) {
        final boolean userNeedsToChangePassword = service.needsPasswordChange(email);
        return Response.status(Status.OK).entity(userNeedsToChangePassword).build();
    }

    @Override
    public final Response isPasswordValid(final HttpServletRequest request,
                                          final PasswordValidationRequest passwordValidationRequest) {
        final PasswordValidationResponse response = service.isPasswordValid(passwordValidationRequest);
        return Response.status(Status.OK).entity(response).build();
    }

//    @Override
//    public final Response postAuthenticationRedirect(final HttpServletRequest request,
//                                                     final String redirectUri) {
//        final URI uri = service.postAuthenticationRedirect(request, redirectUri);
//        return seeOther(uri).build();
//    }
//
//    @Override
//    public Boolean logout() {
//        return securityContext.insecureResult(() -> {
//            final HttpSession session = httpServletRequestProvider.get().getSession(false);
//            final UserIdentity userIdentity = UserIdentitySessionUtil.get(session);
//            if (session != null) {
//                // Invalidate the current user session
//                session.invalidate();
//            }
//            if (userIdentity != null) {
//                // Create an event for logout
//                stroomEventLoggingService.createAction("Logoff", "Logging off " + userIdentity.getId());
//            }
//
//            return true;
//        });
//    }
}
