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

package stroom.security.mock;

import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.security.impl.AuthenticationService;
import stroom.security.shared.User;
import stroom.util.shared.UserName;

import com.google.inject.Inject;

import java.util.Optional;
import java.util.function.Supplier;
import javax.inject.Provider;

public class MockSecurityContext implements SecurityContext {

    private static final MockAdminUserIdentity ADMIN_USER_IDENTITY = new MockAdminUserIdentity();

    // Non integration tests likely don't care about the admin user uuid and won't have
    // AuthenticationService bound, so make it optional. Most users of SecurityContext
    // are just after the secureXXX methods.
    @Inject(optional = true)
    private Provider<AuthenticationService> authenticationServiceProvider;

    @Override
    public String getSubjectId() {
        return getUserIdentity().getSubjectId();
    }

    @Override
    public String getUserUuid() {
        // This gets set when the admin user is first created.
        if (authenticationServiceProvider != null) {
            final String subjectId = User.ADMIN_SUBJECT_ID;
            // This method ensures the internal admin user exists
            return authenticationServiceProvider.get().getUser(subjectId)
                    .map(UserName::getUuid)
                    .orElseThrow(() ->
                            new RuntimeException("Internal admin user '"
                                    + subjectId + "' not found"));
        } else {
            throw new RuntimeException(AuthenticationService.class.getSimpleName()
                    + "not injected so user UUID not available. " +
                    "Bind it if you want this error to go away.");
        }
    }

    @Override
    public UserIdentity getUserIdentity() {
        return ADMIN_USER_IDENTITY;
    }

    @Override
    public UserIdentity createIdentity(final String subjectId) {
        return ADMIN_USER_IDENTITY;
    }

    @Override
    public boolean isLoggedIn() {
        return true;
    }

    @Override
    public boolean isAdmin() {
        return true;
    }

    @Override
    public boolean isProcessingUser() {
        return true;
    }

    @Override
    public boolean isUseAsRead() {
        return false;
    }

    @Override
    public boolean hasAppPermission(final String permission) {
        return true;
    }

    @Override
    public boolean hasDocumentPermission(final String documentUuid, final String permission) {
        return true;
    }

    @Override
    public <T> T asUserResult(final UserIdentity userIdentity, final Supplier<T> supplier) {
        return supplier.get();
    }

    @Override
    public void asUser(final UserIdentity userIdentity, final Runnable runnable) {
        runnable.run();
    }

    @Override
    public <T> T asProcessingUserResult(final Supplier<T> supplier) {
        return supplier.get();
    }

    @Override
    public void asProcessingUser(final Runnable runnable) {
        runnable.run();
    }

    @Override
    public <T> T asAdminUserResult(final Supplier<T> supplier) {
        return null;
    }

    @Override
    public void asAdminUser(final Runnable runnable) {
    }

    @Override
    public <T> T useAsReadResult(final Supplier<T> supplier) {
        return supplier.get();
    }

    @Override
    public void useAsRead(final Runnable runnable) {
        runnable.run();
    }

    @Override
    public void secure(final String permission, final Runnable runnable) {
        runnable.run();
    }

    @Override
    public <T> T secureResult(final String permission, final Supplier<T> supplier) {
        return supplier.get();
    }

    @Override
    public void secure(final Runnable runnable) {
        runnable.run();
    }

    @Override
    public <T> T secureResult(final Supplier<T> supplier) {
        return supplier.get();
    }

    @Override
    public void insecure(final Runnable runnable) {
        runnable.run();
    }

    @Override
    public <T> T insecureResult(final Supplier<T> supplier) {
        return supplier.get();
    }


    // --------------------------------------------------------------------------------


    private static class MockAdminUserIdentity implements UserIdentity {

        @Override
        public String getSubjectId() {
            return User.ADMIN_SUBJECT_ID;
        }

        @Override
        public String getDisplayName() {
            return User.ADMIN_SUBJECT_ID;
        }

        @Override
        public Optional<String> getFullName() {
            return Optional.of("Ad Min");
        }
    }
}
