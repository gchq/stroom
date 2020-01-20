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
import stroom.auth.service.ApiException;
import stroom.auth.service.ApiResponse;
import stroom.auth.service.api.UserApi;
import stroom.auth.resources.support.Dropwizard_IT;

import java.util.UUID;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.assertj.core.api.Java6Assertions.fail;

public final class UserResource_create_IT extends Dropwizard_IT {
    @Test
    public final void create_user() throws Exception {
        String idToken = AuthenticationFlowHelper.authenticateAsAdmin();
        UserApi userApi = SwaggerHelper.newUserApiClient(idToken);

        ApiResponse<Integer> response = userApi.createUserWithHttpInfo(new stroom.auth.service.api.model.User()
                .email("create_user_"+ UUID.randomUUID().toString())
                .password("password"));

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getData()).isNotNull();
    }

    @Test
    public final void create_user_missing_name() throws Exception {
        String idToken = AuthenticationFlowHelper.authenticateAsAdmin();
        UserApi userApi = SwaggerHelper.newUserApiClient(idToken);

        try {
            userApi.createUser(new stroom.auth.service.api.model.User()
                    .email("")
                    .password("password"));
            fail("Should throw an exception!");
        } catch(ApiException e ){
            assertThat(e.getCode()).isEqualTo(400);
        }
    }

    @Test
    public final void create_user_missing_password() throws Exception {
        String idToken = AuthenticationFlowHelper.authenticateAsAdmin();
        UserApi userApi = SwaggerHelper.newUserApiClient(idToken);

        try {
            userApi.createUser(new stroom.auth.service.api.model.User()
                    .email("create_user_missing_password_" + UUID.randomUUID().toString())
                    .password(""));
            fail("Should throw an exception!");
        } catch(ApiException e ){
            assertThat(e.getCode()).isEqualTo(400);
        }
    }

    @Test
    public final void create_user_with_duplicate_name() throws Exception {
        String idToken = AuthenticationFlowHelper.authenticateAsAdmin();
        UserApi userApi = SwaggerHelper.newUserApiClient(idToken);

        String emailToBeReused = UUID.randomUUID().toString();
        ApiResponse<Integer> response = userApi.createUserWithHttpInfo(new stroom.auth.service.api.model.User()
                .email(emailToBeReused)
                .password("password"));

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getData()).isNotNull();

        try {
            userApi.createUserWithHttpInfo(new stroom.auth.service.api.model.User()
                    .email(emailToBeReused)
                    .password("password"));
            fail("Should have had an exception!");
        }catch(ApiException e){
            assertThat(e.getCode()).isEqualTo(409);
        }
    }

}
