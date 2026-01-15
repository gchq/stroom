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

public class NoIdpServiceUserFactory implements ServiceUserFactory {

    private static final UserIdentity NO_IDP_SERVICE_USER_IDENTITY = new UserIdentity() {
        @Override
        public String subjectId() {
            return "NO_IDP SERVICE USER";
        }
    };

    @Override
    public UserIdentity createServiceUserIdentity() {
        return NO_IDP_SERVICE_USER_IDENTITY;
    }

    @Override
    public boolean isServiceUser(final UserIdentity userIdentity,
                                 final UserIdentity serviceUserIdentity) {
        if (userIdentity == null || serviceUserIdentity == null) {
            return false;
        } else {
            // Instance equality as there is only one service user identity
            return userIdentity == serviceUserIdentity;
        }
    }
}
