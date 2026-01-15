/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.security.common.impl;

import stroom.security.api.ServiceUserFactory;
import stroom.security.api.UserIdentity;
import stroom.util.authentication.DefaultOpenIdCredentials;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;

import java.util.Objects;

public class TestCredentialsServiceUserFactory implements ServiceUserFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestCredentialsServiceUserFactory.class);

    private final DefaultOpenIdCredentials defaultOpenIdCredentials;

    @Inject
    public TestCredentialsServiceUserFactory(final DefaultOpenIdCredentials defaultOpenIdCredentials) {
        this.defaultOpenIdCredentials = defaultOpenIdCredentials;
    }

    @Override
    public UserIdentity createServiceUserIdentity() {
        final UserIdentity serviceUserIdentity = new DefaultOpenIdCredsUserIdentity(
                defaultOpenIdCredentials.getApiKeyUserEmail(),
                defaultOpenIdCredentials.getApiKey());
        LOGGER.info("Created test service user identity {} {}",
                serviceUserIdentity.getClass().getSimpleName(), serviceUserIdentity);
        return serviceUserIdentity;
    }

    @Override
    public boolean isServiceUser(final UserIdentity userIdentity,
                                 final UserIdentity serviceUserIdentity) {
        if (userIdentity == null || serviceUserIdentity == null) {
            return false;
        } else {
            return userIdentity instanceof DefaultOpenIdCredsUserIdentity
                   && Objects.equals(
                    userIdentity.subjectId(),
                    serviceUserIdentity.subjectId());
        }
    }
}
