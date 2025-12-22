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

import stroom.security.api.CommonSecurityContext;
import stroom.security.api.UserIdentity;
import stroom.security.impl.BasicUserIdentity;
import stroom.security.shared.AppPermission;
import stroom.security.shared.AppPermissionSet;
import stroom.util.shared.UserRef;

import java.util.UUID;
import java.util.function.Supplier;

public class MockCommonSecurityContext implements CommonSecurityContext {

    private static final MockCommonSecurityContext INSTANCE = new MockCommonSecurityContext();

    private static final UserRef MOCK_ADMIN = UserRef
            .builder()
            .uuid(UUID.randomUUID().toString())
            .subjectId("admin")
            .displayName("admin")
            .fullName("Ad Min")
            .build();

    private static final UserIdentity USER_IDENTITY = new BasicUserIdentity(MOCK_ADMIN);

    public static MockCommonSecurityContext getInstance() {
        return INSTANCE;
    }

    public MockCommonSecurityContext() {
    }

    @Override
    public UserIdentity getUserIdentity() {
        return USER_IDENTITY;
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
    public boolean hasAppPermission(final AppPermission permission) {
        return true;
    }

    @Override
    public boolean hasAppPermissions(final AppPermissionSet permissions) {
        return true;
    }

    @Override
    public boolean hasAppPermissions(final UserIdentity userIdentity, final AppPermissionSet permissions) {
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
    public void secure(final AppPermission permission, final Runnable runnable) {
        runnable.run();
    }

    @Override
    public void secure(final AppPermissionSet permission, final Runnable runnable) {
        runnable.run();
    }

    @Override
    public <T> T secureResult(final AppPermission permission, final Supplier<T> supplier) {
        return supplier.get();
    }

    @Override
    public <T> T secureResult(final AppPermissionSet permissionSet, final Supplier<T> supplier) {
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
}
