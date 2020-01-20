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

import org.joda.time.DateTime;
import org.junit.Test;
import stroom.auth.AuthenticationFlowHelper;
import stroom.auth.service.ApiException;
import stroom.auth.service.ApiResponse;
import stroom.auth.service.api.ApiKeyApi;
import stroom.auth.service.api.UserApi;
import stroom.auth.service.api.model.CreateTokenRequest;
import stroom.auth.service.api.model.Token;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class TokenResource_delete_IT extends TokenResource_IT {

    @Test
    public void delete() throws Exception {
        String idToken = AuthenticationFlowHelper.authenticateAsAdmin();
        ApiKeyApi apiKeyApiClient = SwaggerHelper.newApiKeyApiClient(idToken);

        CreateTokenRequest createTokenRequest = new CreateTokenRequest();
        createTokenRequest.setUserEmail("admin");
        createTokenRequest.setTokenType("api");
        createTokenRequest.setEnabled(false);
        createTokenRequest.setComments("Created by TokenResource_create_IT");
        createTokenRequest.setExpiryDate(DateTime.now().plusMinutes(30));
        Token newApiKeyId = apiKeyApiClient.create(createTokenRequest);
        assertThat(newApiKeyId).isNotNull();

        // Check that the token we just created has been saved.
        stroom.auth.service.api.model.Token newApiKeyJws = apiKeyApiClient.read_0(newApiKeyId.getId());
        assertThat(newApiKeyJws).isNotNull();

        ApiResponse<String> deleteResponse = apiKeyApiClient.delete_0WithHttpInfo(newApiKeyId.getId());
        assertThat(deleteResponse.getStatusCode()).isEqualTo(204);

        // Check that the token we just created has been saved.
        try {
            apiKeyApiClient.read_0WithHttpInfo(newApiKeyId.getId());
        } catch (ApiException ex) {
            assertThat(ex.getCode()).isEqualTo(404);
        }
    }

    @Test
    public void deleteAll() throws Exception {
        String idToken = AuthenticationFlowHelper.authenticateAsAdmin();
        ApiKeyApi apiKeyApiClient = SwaggerHelper.newApiKeyApiClient(idToken);

        // Set up users and tokens

        UserApi userApi = SwaggerHelper.newUserApiClient(idToken);
        stroom.auth.service.api.model.User user1 = new stroom.auth.service.api.model.User();
        String user1Email = "user_" + UUID.randomUUID().toString();
        user1.setEmail(user1Email);
        user1.setPassword("password");
        userApi.createUser(user1);

        CreateTokenRequest createTokenRequest1 = new CreateTokenRequest();
        createTokenRequest1.setTokenType("API");
        createTokenRequest1.setUserEmail(user1Email);
        createTokenRequest1.setExpiryDate(DateTime.now().plusMinutes(30));
        Token key1Id = apiKeyApiClient.create(createTokenRequest1);

        CreateTokenRequest createTokenRequest2 = new CreateTokenRequest();
        createTokenRequest2.setTokenType("API");
        createTokenRequest2.setUserEmail(user1Email);
        createTokenRequest2.setExpiryDate(DateTime.now().plusMinutes(30));
        Token key2Id = apiKeyApiClient.create(createTokenRequest2);


        stroom.auth.service.api.model.User user2 = new stroom.auth.service.api.model.User();
        String user2Email = "user_" + UUID.randomUUID().toString();
        user2.setEmail(user2Email);
        user2.setPassword("password");
        userApi.createUser(user2);

        CreateTokenRequest createTokenRequest3 = new CreateTokenRequest();
        createTokenRequest3.setTokenType("API");
        createTokenRequest3.setUserEmail(user2Email);
        createTokenRequest3.setExpiryDate(DateTime.now().plusMinutes(30));
        Token key3Id = apiKeyApiClient.create(createTokenRequest3);

        CreateTokenRequest createTokenRequest4 = new CreateTokenRequest();
        createTokenRequest4.setTokenType("API");
        createTokenRequest4.setUserEmail(user2Email);
        createTokenRequest4.setExpiryDate(DateTime.now().plusMinutes(30));
        Token key4Id = apiKeyApiClient.create(createTokenRequest4);

        CreateTokenRequest createTokenRequest5 = new CreateTokenRequest();
        createTokenRequest5.setTokenType("API");
        createTokenRequest5.setUserEmail(user2Email);
        createTokenRequest5.setExpiryDate(DateTime.now().plusMinutes(30));
        Token key5Id = apiKeyApiClient.create(createTokenRequest5);


        // Verify users and tokens were created
        stroom.auth.service.api.model.Token key1 = apiKeyApiClient.read_0(key1Id.getId());
        assertThat(key1).isNotNull();
        stroom.auth.service.api.model.Token key2 = apiKeyApiClient.read_0(key2Id.getId());
        assertThat(key2).isNotNull();
        stroom.auth.service.api.model.Token key3 = apiKeyApiClient.read_0(key3Id.getId());
        assertThat(key3).isNotNull();
        stroom.auth.service.api.model.Token key4 = apiKeyApiClient.read_0(key4Id.getId());
        assertThat(key4).isNotNull();
        stroom.auth.service.api.model.Token key5 = apiKeyApiClient.read_0(key5Id.getId());
        assertThat(key5).isNotNull();

        ApiResponse response = apiKeyApiClient.deleteAllWithHttpInfo();
        assertThat(response.getStatusCode()).isEqualTo(200);

        try {
            apiKeyApiClient.read_0(key1Id.getId());
        } catch (ApiException e) {
            assertThat(e.getCode()).isEqualTo(404);
        }
        try {
            apiKeyApiClient.read_0(key2Id.getId());
        } catch (ApiException e) {
            assertThat(e.getCode()).isEqualTo(404);
        }
        try {
            apiKeyApiClient.read_0(key3Id.getId());
        } catch (ApiException e) {
            assertThat(e.getCode()).isEqualTo(404);
        }
        try {
            apiKeyApiClient.read_0(key4Id.getId());
        } catch (ApiException e) {
            assertThat(e.getCode()).isEqualTo(404);
        }
        try {
            apiKeyApiClient.read_0(key5Id.getId());
        } catch (ApiException e) {
            assertThat(e.getCode()).isEqualTo(404);
        }
    }

}
