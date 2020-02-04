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

package stroom.auth.clients;

import org.eclipse.jetty.http.HttpStatus;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.auth.config.AuthenticationConfig;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Singleton
public class AuthorisationServiceClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorisationServiceClient.class);

    private AuthenticationConfig config;
    private Client authorisationService = ClientBuilder.newClient(new ClientConfig().register(ClientResponse.class));

    @Inject
    public AuthorisationServiceClient(AuthenticationConfig config) {
        this.config = config;
    }

    public boolean hasPermission(String userName, String usersJws, String permission) {
        String authorisationUrl = config.getAuthorisationServiceConfig().getUrl() + "/isAuthorised/";
        Response response = authorisationService
                .target(authorisationUrl)
                .request()
                .header("Authorization", "Bearer " + usersJws)
                .post(Entity.entity(new UserPermissionRequest(permission),
                        MediaType.APPLICATION_JSON_TYPE));

        switch (response.getStatus()) {
            case HttpStatus.OK_200:
                return true;
            case HttpStatus.UNAUTHORIZED_401:
                return false;
            default:
                LOGGER.error("Unable to check permissions for user {}. Response code was {} {}",
                        userName, response.getStatus(), response.getStatusInfo().getReasonPhrase());
                return false;
        }
    }

    private class UserPermissionRequest {
        private String permission;

        public UserPermissionRequest() {
        }

        public UserPermissionRequest(String permission) {
            this.permission = permission;
        }

        public String getPermission() {
            return permission;
        }
    }
}
