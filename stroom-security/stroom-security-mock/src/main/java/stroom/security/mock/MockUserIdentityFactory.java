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

package stroom.security.mock;

import stroom.security.api.ServiceUserFactory;
import stroom.security.api.UserIdentity;
import stroom.security.api.UserIdentityFactory;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public class MockUserIdentityFactory implements UserIdentityFactory {

    private final ServiceUserFactory serviceUserFactory;
    private final UserIdentity serviceUserIdentity;

    @Inject
    public MockUserIdentityFactory(final ServiceUserFactory serviceUserFactory) {
        this.serviceUserFactory = serviceUserFactory;
        this.serviceUserIdentity = serviceUserFactory.createServiceUserIdentity();
    }

    @Override
    public Optional<UserIdentity> getApiUserIdentity(final HttpServletRequest request) {
        return Optional.empty();
    }

    @Override
    public UserIdentity getServiceUserIdentity() {
        return serviceUserIdentity;
    }

    @Override
    public boolean isServiceUser(final UserIdentity userIdentity) {
        return serviceUserFactory.isServiceUser(userIdentity, serviceUserIdentity);
    }

    @Override
    public void removeAuthEntries(final Map<String, String> headers) {

    }

    @Override
    public Map<String, String> getAuthHeaders(final UserIdentity userIdentity) {
        return Collections.emptyMap();
    }

    @Override
    public Map<String, String> getServiceUserAuthHeaders() {
        return Collections.emptyMap();
    }

    @Override
    public Map<String, String> getAuthHeaders(final String token) {
        return Collections.emptyMap();
    }

    @Override
    public void refresh(final UserIdentity userIdentity) {

    }

    @Override
    public boolean hasAuthenticationCertificate(final jakarta.servlet.http.HttpServletRequest request) {
        return false;
    }

    @Override
    public boolean hasAuthenticationToken(final jakarta.servlet.http.HttpServletRequest request) {
        return false;
    }
}
