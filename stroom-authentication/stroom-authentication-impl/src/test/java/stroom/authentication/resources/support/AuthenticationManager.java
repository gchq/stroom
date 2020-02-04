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

package stroom.authentication.resources.support;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.authentication.resources.user.v1.User;

import static org.assertj.core.api.Assertions.assertThat;

public class AuthenticationManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationManager.class);
    private String loginUrl;

    public final String loginAsAdmin() throws UnirestException {
        HttpResponse response = Unirest
                .post(loginUrl)
                .header("Content-Type", "application/json")
                .body("{\"email\" : \"admin\", \"password\" : \"admin\"}")
                .asString();

        LOGGER.info("Response: {}", response.getBody());
        String jwsToken = (String) response.getBody();
        assertThat(response.getStatus()).isEqualTo(200);
        // This is the first part of the token, which doesn't change
        assertThat(jwsToken).contains("eyJhbGciOiJIUzI1NiJ9");
        return jwsToken;
    }

    public final String logInAsUser(User user) throws UnirestException {
        HttpResponse getJwsResponse = Unirest
                .post(loginUrl)
                .header("Content-Type", "application/json")
                .body("{\"email\" : \"" + user.getEmail() + "\", \"password\" : \"testPassword\"}")
                .asString();
        return (String) getJwsResponse.getBody();
    }

    public void setPort(int appPort) {
        this.loginUrl = "http://localhost:" + appPort + "/authentication/v1/login";
    }

    public String getLoginUrl() {
        return this.loginUrl;
    }
}
