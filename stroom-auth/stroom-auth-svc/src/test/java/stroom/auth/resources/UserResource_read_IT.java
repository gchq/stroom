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

package stroom.auth.resources;

import org.junit.Test;
import stroom.auth.AuthenticationFlowHelper;
import stroom.auth.resources.user.v1.User;
import stroom.auth.service.ApiException;
import stroom.auth.service.ApiResponse;
import stroom.auth.service.api.AuthenticationApi;
import stroom.auth.service.api.UserApi;
import stroom.auth.service.api.model.ChangePasswordRequest;
import stroom.auth.resources.support.Dropwizard_IT;

import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Java6Assertions.fail;

public final class UserResource_read_IT extends Dropwizard_IT {

    @Test
    public final void search_users() throws Exception {
        UserApi userApi = SwaggerHelper.newUserApiClient(AuthenticationFlowHelper.authenticateAsAdmin());
        ApiResponse<String> response = userApi.getAllWithHttpInfo();
        assertThat(response.getStatusCode()).isEqualTo(200);
    }

    @Test
    public final void read_current_user() throws Exception {
        UserApi userApi = SwaggerHelper.newUserApiClient(AuthenticationFlowHelper.authenticateAsAdmin());
        ApiResponse<String> response = userApi.readCurrentUserWithHttpInfo();
        List<User> user = userManager.deserialiseUsers(response.getData());

        if (user != null) {
            assertThat(user.get(0).getEmail()).isEqualTo("admin");
        } else fail("No users found");
        assertThat(response.getStatusCode()).isEqualTo(200);
    }

    @Test
    public final void read_user_that_doesnt_exist() throws Exception {
        UserApi userApi = SwaggerHelper.newUserApiClient(AuthenticationFlowHelper.authenticateAsAdmin());
        try {
            userApi.getUser(129387298);
            fail("Expected a 404 exception!");
        }catch(ApiException e) {
            assertThat(e.getCode()).isEqualTo(404);
        }
    }

    @Test
    public final void read_other_user_with_authorisation() throws Exception {
        UserApi userApi = SwaggerHelper.newUserApiClient(AuthenticationFlowHelper.authenticateAsAdmin());

        ApiResponse<Integer> response = userApi.createUserWithHttpInfo(new stroom.auth.service.api.model.User()
            .email("read_other_user_with_authorisation_" + UUID.randomUUID().toString())
            .password("password"));
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getData()).isNotNull();

        String user = userApi.getUser(response.getData());
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getData()).isNotNull();
    }

    @Test
    public final void read_other_user_without_authorisation() throws Exception {
        UserApi adminUserApi = SwaggerHelper.newUserApiClient(AuthenticationFlowHelper.authenticateAsAdmin());

        String userEmailA = "userEmailA_" + UUID.randomUUID().toString();
        int userEmailAId = adminUserApi.createUserWithHttpInfo(new stroom.auth.service.api.model.User()
                .email(userEmailA)
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

        String userEmailB = "userEmailB_" + UUID.randomUUID().toString();
        ApiResponse<Integer> responseB = adminUserApi.createUserWithHttpInfo(new stroom.auth.service.api.model.User()
                .email(userEmailB)
                .password("password"));

        stubFor(post(urlEqualTo("/api/authorisation/v1/isAuthorised/"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(401)));

        UserApi userApiA = SwaggerHelper.newUserApiClient(AuthenticationFlowHelper.authenticateAs(userEmailA, "password"));
        try {
            userApiA.getUser(responseB.getData());
            fail("Expected a 401!");
        } catch(ApiException e){
            assertThat(e.getCode()).isEqualTo(401);
        }
    }

}
