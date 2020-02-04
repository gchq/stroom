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

package stroom.authentication.clients;

import org.eclipse.jetty.http.HttpStatus;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.authentication.config.AuthenticationConfig;
import stroom.authentication.config.UserServiceConfig;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;

@Singleton
public class UserServiceClient {
    public static final String UNAUTHORISED_USER_MESSAGE = "This user is not authorised to access this resource";
    private static final Logger LOGGER = LoggerFactory.getLogger(UserServiceClient.class);
    private static final String NOT_FOUND_404_MESSAGE = "Received a 404 when trying to access the authorisation service! I am unable to check authorisation so all requests will be rejected until this is fixed. Is the service location correctly configured? Is the service running? The URL I tried was: {}";

    private UserServiceConfig config;
    private Client authorisationService = ClientBuilder.newClient(new ClientConfig().register(ClientResponse.class));

    @Inject
    public UserServiceClient(AuthenticationConfig config) {
        this.config = config.getUserServiceConfig();
    }

//    public List<User> getUsersGroups(String userId, String usersJws) {
//        String authorisationUrl = config.getUserServiceConfig().getGroupsForUserUrl() + "/" + userId;
//        Response response = authorisationService
//                .target(authorisationUrl)
//                .request()
//                .header("Authorization", "Bearer " + usersJws)
//                .get();
//
//        List<User> groups = response.readEntity(new GenericType<List<User>>(){});
//       return groups;
//    }

//    public boolean isUserAuthorisedToManageUsers(String userId, String usersJws) {
//        List<User> groups = getUsersGroups(userId, usersJws);
//        Optional<User> optionalGroup = groups.stream().filter(group ->
//                group.getName().equals(config.getUserServiceConfig().getCanManageUsersPermission()))
//                .findFirst();
//        return optionalGroup.isPresent();
//    }

//    public boolean isUserAuthorisedToManageUsers(String usersJws) {
//        String authorisationUrl = config.getUserServiceConfig().getCanManageUsersUrl();
//        Response response = authorisationService
//                .target(authorisationUrl)
//                .request()
//                .header("Authorization", "Bearer " + usersJws)
//                .post(getManageUserPermissionEntity());
//
//        boolean isUserAuthorisedToManageUsers;
//
//        switch (response.getStatus()) {
//            case HttpStatus.UNAUTHORIZED_401:
//                isUserAuthorisedToManageUsers = false;
//                break;
//            case HttpStatus.OK_200:
//                isUserAuthorisedToManageUsers = true;
//                break;
//            case HttpStatus.NOT_FOUND_404:
//                isUserAuthorisedToManageUsers = false;
//                LOGGER.error(NOT_FOUND_404_MESSAGE);
//                break;
//            default:
//                isUserAuthorisedToManageUsers = false;
//                LOGGER.error("Tried to check authorisation for a user but got an unknown response! Response code was {}", response.getStatus());
//        }
//
//        return isUserAuthorisedToManageUsers;
//    }

    public boolean setUserStatus(String usersJws, String user, boolean isEnabled) {
        String url = String.format("%s/%s/status?enabled=%s",
                config.getUrl(),
                user,
                isEnabled);
        Response response = authorisationService
                .target(url)
                .request()
                .header("Authorization", "Bearer " + usersJws)
                .get();

        boolean result;

        switch (response.getStatus()) {
            case HttpStatus.UNAUTHORIZED_401:
                result = false;
                break;
            case HttpStatus.OK_200:
                result = true;
                break;
            case HttpStatus.NOT_FOUND_404:
                result = false;
                LOGGER.error(NOT_FOUND_404_MESSAGE, url);
                break;
            default:
                result = false;
                LOGGER.error("Tried to change the status for a user but got an unknown response! Response code was {}",
                        response.getStatus());
        }

        return result;
    }


    /**
     * In Stroom User and Group are the same entity
     */
//    static class User {
//        private Integer id;
//        private Integer version;
//        private Long createTimeMs;
//        private String createUser;
//        private Long updateTimeMs;
//        private String updateUser;
//        private String name;
//        private String uuid;
//
//        private boolean group;
//
//        private boolean enabled = true;
//
//        public Integer getId() {
//            return id;
//        }
//
//        public void setId(final Integer id) {
//            this.id = id;
//        }
//
//        public Integer getVersion() {
//            return version;
//        }
//
//        public void setVersion(final Integer version) {
//            this.version = version;
//        }
//
//        public Long getCreateTimeMs() {
//            return createTimeMs;
//        }
//
//        public void setCreateTimeMs(final Long createTimeMs) {
//            this.createTimeMs = createTimeMs;
//        }
//
//        public String getCreateUser() {
//            return createUser;
//        }
//
//        public void setCreateUser(final String createUser) {
//            this.createUser = createUser;
//        }
//
//        public Long getUpdateTimeMs() {
//            return updateTimeMs;
//        }
//
//        public void setUpdateTimeMs(final Long updateTimeMs) {
//            this.updateTimeMs = updateTimeMs;
//        }
//
//        public String getUpdateUser() {
//            return updateUser;
//        }
//
//        public void setUpdateUser(final String updateUser) {
//            this.updateUser = updateUser;
//        }
//
//        public String getName() {
//            return name;
//        }
//
//        public void setName(String name) {
//            this.name = name;
//        }
//
//        public String getUuid() {
//            return uuid;
//        }
//
//        public void setUuid(String uuid) {
//            this.uuid = uuid;
//        }
//
//        public boolean isGroup() {
//            return group;
//        }
//
//        public void setGroup(boolean group) {
//            this.group = group;
//        }
//
//        public boolean isEnabled() {
//            return enabled;
//        }
//
//        public void setEnabled(final boolean enabled) {
//            this.enabled = enabled;
//        }
//
//        @Override
//        public String toString() {
//            return "User{" +
//                    "id=" + id +
//                    ", version=" + version +
//                    ", createTimeMs=" + createTimeMs +
//                    ", createUser='" + createUser + '\'' +
//                    ", updateTimeMs=" + updateTimeMs +
//                    ", updateUser='" + updateUser + '\'' +
//                    ", name='" + name + '\'' +
//                    ", uuid='" + uuid + '\'' +
//                    ", group=" + group +
//                    ", enabled=" + enabled +
//                    '}';
//        }
//
//        @Override
//        public boolean equals(final Object o) {
//            if (this == o) return true;
//            if (o == null || getClass() != o.getClass()) return false;
//            final User user = (User) o;
//            return Objects.equals(id, user.id);
//        }
//
//        @Override
//        public int hashCode() {
//            return Objects.hash(id);
//        }

//    }
}
