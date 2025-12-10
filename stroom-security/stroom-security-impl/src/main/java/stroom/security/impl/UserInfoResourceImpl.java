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

package stroom.security.impl;

import stroom.event.logging.rs.api.AutoLogged;
import stroom.security.shared.UserInfoResource;
import stroom.security.user.api.UserInfoLookup;
import stroom.util.shared.UserInfo;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

@AutoLogged
public class UserInfoResourceImpl implements UserInfoResource {

    private final Provider<UserInfoLookup> userInfoLookupProvider;

    @SuppressWarnings("unused")
    @Inject
    public UserInfoResourceImpl(final Provider<UserInfoLookup> userInfoLookupProvider) {
        this.userInfoLookupProvider = userInfoLookupProvider;
    }

    @Override
    public UserInfo fetch(final String userUuid) {
        return userInfoLookupProvider.get().getByUuid(userUuid)
                .orElse(null);
    }
}
