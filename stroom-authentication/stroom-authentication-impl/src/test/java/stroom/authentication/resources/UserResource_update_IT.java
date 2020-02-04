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

package stroom.authentication.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Ignore;
import org.junit.Test;
import stroom.authentication.AuthenticationFlowHelper;
//import stroom.authentication.resources.v1.User;
import stroom.authentication.service.ApiResponse;
import stroom.authentication.service.api.AuthenticationApi;
import stroom.authentication.service.api.UserApi;
import stroom.authentication.service.api.model.ChangePasswordRequest;
import stroom.authentication.service.api.model.User;
import stroom.authentication.resources.support.Dropwizard_IT;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Ignore("Temporarily ignore for auth migration")
public final class UserResource_update_IT extends Dropwizard_IT {
    @Test
    public final void update_user() throws Exception {
        UserApi userApi = SwaggerHelper.newUserApiClient(AuthenticationFlowHelper.authenticateAsAdmin());
        ObjectMapper mapper = new ObjectMapper();

        // Create the user
        String email = "update_user_" + UUID.randomUUID().toString();
        ApiResponse<Integer> response = userApi.createUserWithHttpInfo(new User()
                .email(email)
                .state("enabled")
                .comments("Some comments")
                .password("password"));
        int id = response.getData();

        // Get the full user and update
        String users = userApi.getUser(id);
        User user = mapper.readValue(users, User.class);
        user.setComments("Different comments");
        userApi.updateUser(user.getId(), user);

        // Get the user again
        String updatedUserJson = userApi.getUser(id);
        User updatedUser = mapper.readValue(updatedUserJson, User.class);

        // Verify the update
        assertThat(updatedUser.getEmail()).isEqualTo(user.getEmail());
    }

    @Test
    public final void update_self_basic_user() throws Exception {
        UserApi userApi = SwaggerHelper.newUserApiClient(AuthenticationFlowHelper.authenticateAsAdmin());
        ObjectMapper mapper = new ObjectMapper();

        // Create the user
        String userEmailA = "update_user_" + UUID.randomUUID().toString();
        int userEmailAId = userApi.createUserWithHttpInfo(new User()
                .email(userEmailA)
                .state("enabled")
                .password("password"))
                .getData();

        // If we don't change the password the AuthenticationResource will think this is the first login and
        // try to force us to change the password. This means we won't get an access code and complete the
        // authentication flow. So we'll change the password so the flow completes as normal.
        AuthenticationApi authApi = SwaggerHelper.newAuthApiClient(AuthenticationFlowHelper.authenticateAsAdmin());
        authApi.changePassword(userEmailAId, new ChangePasswordRequest()
                .email(userEmailA)
                .oldPassword("password")
                .newPassword("password"));

        // Get the full user and update
        String userJson = userApi.getUser(userEmailAId);
        User userA = mapper.readValue(userJson, User.class);
        userA.setComments("Updated user");
        userApi.updateUser(userEmailAId, userA);

        // Get the user again
        UserApi userApiA = SwaggerHelper.newUserApiClient(AuthenticationFlowHelper.authenticateAs(userEmailA, "password"));
        String updatedUserJson = userApiA.getUser(userEmailAId);
        User updatedUser = mapper.readValue(updatedUserJson, User.class);

        //Verify the update
        assertThat(updatedUser.getComments()).isEqualTo("Updated user");
    }
}
