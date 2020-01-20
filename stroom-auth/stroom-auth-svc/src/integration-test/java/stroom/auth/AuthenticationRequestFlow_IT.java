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

package stroom.auth;

import org.junit.Test;
import stroom.auth.service.resources.support.Dropwizard_IT;

import static org.assertj.core.api.Assertions.assertThat;

public class AuthenticationRequestFlow_IT extends Dropwizard_IT {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(AuthenticationRequestFlow_IT.class);

    /**
     * This does nothing but initiate the login flow and expect an idToken.
     */
    @Test
    public void simplest() throws Exception {
        String idToken = AuthenticationFlowHelper.authenticateAsAdmin();
        assertThat(idToken).isNotEmpty();
    }

}
