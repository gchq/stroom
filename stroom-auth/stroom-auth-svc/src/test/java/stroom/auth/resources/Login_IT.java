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

import org.junit.Ignore;
import org.junit.Test;
import stroom.auth.AuthenticationFlowHelper;
import stroom.auth.service.ApiException;
import stroom.auth.resources.support.Dropwizard_IT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Java6Assertions.fail;

@Ignore("Temporarily ignore for auth migration")
public class Login_IT extends Dropwizard_IT {

    @Test
    public void incorrect_credentials_1() throws Exception {
        String sessionId = AuthenticationFlowHelper.sendInitialAuthenticationRequest();
        try {
            AuthenticationFlowHelper.performLogin(sessionId, "BAD", "admin");
            fail("Expected an UnAuthorisedException!");
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).isEqualToIgnoringCase("LOGIN_FAILED");
        }
    }

    @Test
    public void incorrect_credentials_2() throws Exception {
        String sessionId = AuthenticationFlowHelper.sendInitialAuthenticationRequest();
        try {
            AuthenticationFlowHelper.performLogin(sessionId, "admin", "BAD");
            fail("Expected an UnAuthorisedException!");
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).isEqualToIgnoringCase("LOGIN_FAILED");
        }
    }

    @Test
    public void incorrect_credentials_3() throws Exception {
        String sessionId = AuthenticationFlowHelper.sendInitialAuthenticationRequest();
        try {
            AuthenticationFlowHelper.performLogin(sessionId, "BAD", "BAD");
            fail("Expected an UnAuthorisedException!");
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).isEqualToIgnoringCase("LOGIN_FAILED");
        }
    }

    @Test
    public void missing_credentials_1() throws Exception {
        String sessionId = AuthenticationFlowHelper.sendInitialAuthenticationRequest();
        try {
            String accessCode = AuthenticationFlowHelper.performLogin(sessionId, "BAD", "");
            fail("Expected a 400!");
        } catch (ApiException e) {
            assertThat(e.getCode()).isEqualTo(400);
        }
    }

    @Test
    public void missing_credentials_2() throws Exception {
        String sessionId = AuthenticationFlowHelper.sendInitialAuthenticationRequest();
        try {
            String accessCode = AuthenticationFlowHelper.performLogin(sessionId, "", "");
            fail("Expected a 400!");
        } catch (ApiException e) {
            assertThat(e.getCode()).isEqualTo(400);
        }
    }
}
