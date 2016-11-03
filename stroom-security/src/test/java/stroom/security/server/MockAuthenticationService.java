/*
 * Copyright 2016 Crown Copyright
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

package stroom.security.server;

import stroom.security.shared.User;

public class MockAuthenticationService implements AuthenticationService {
    @Override
    public User autoLogin() throws RuntimeException {
        return null;
    }

    @Override
    public void refreshCurrentUser() throws RuntimeException {
    }

    @Override
    public boolean canEmailPasswordReset() {
        return false;
    }

    @Override
    public User changePassword(final User user, final String oldPassword, final String newPassword)
            throws RuntimeException {
        return null;
    }

    @Override
    public Boolean emailPasswordReset(final String userName) throws RuntimeException {
        return Boolean.FALSE;
    }

    @Override
    public User emailPasswordReset(final User user) throws RuntimeException {
        return null;
    }

    @Override
    public User getCurrentUser() throws RuntimeException {
        return null;
    }

    @Override
    public String getCurrentUserId() throws RuntimeException {
        return null;
    }

//    @Override
//    public boolean isUserAuthenticated() throws RuntimeException {
//        return false;
//    }

    @Override
    public User login(final String userName, final String password) throws RuntimeException {
        return null;
    }

    @Override
    public String logout() throws RuntimeException {
        return null;
    }

    @Override
    public User resetPassword(final User user, final String password) throws RuntimeException {
        return null;
    }
}
