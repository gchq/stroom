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

package stroom.security.api;

import stroom.security.shared.AppPermission;
import stroom.security.shared.AppPermissionSet;

import java.util.function.Supplier;

/**
 * A Security Context that is used by both Stroom and Stroom-Proxy, allowing
 * each to implement in their own way. Only deals in {@link UserIdentity} and
 * {@link AppPermission}, not {@link stroom.util.shared.UserRef} or
 * {@link stroom.security.shared.DocumentPermission}.
 */
public interface CommonSecurityContext {

    /**
     * Gets the identity of the current user.
     *
     * @return The identity of the current user.
     */
    UserIdentity getUserIdentity();

    /**
     * This is a convenience method to check that the user has system administrator privileges.
     *
     * @return True if the current user is an administrator.
     */
    boolean isAdmin();

    /**
     * Check if the current user is the processing user.
     *
     * @return True if the current user is the processing user.
     */
    boolean isProcessingUser();

    /**
     * Check if the user associated with this security context has the requested
     * permission to use the specified functionality.
     *
     * @param requiredPermission The permission we are checking for.
     * @return True if the user associated with the security context has the
     * requested permission.
     */
    default boolean hasAppPermission(final AppPermission requiredPermission) {
        return hasAppPermissions(requiredPermission.asAppPermissionSet());
    }

    /**
     * Check if the user associated with this security context has the requested
     * permission to use the specified functionality.
     *
     * @param requiredPermissions The permission we are checking for. All must be held.
     * @return True if the user associated with the security context has the
     * requested permission.
     */
    boolean hasAppPermissions(AppPermissionSet requiredPermissions);

    /**
     * Check if the supplied user has the requested
     * permission to use the specified functionality.
     *
     * @param permissions The permission we are checking for. All must be held.
     * @return True if the supplied user has the requested permission.
     */
    boolean hasAppPermissions(UserIdentity userIdentity, AppPermissionSet permissions);

    /**
     * Run the supplied code as the specified user.
     */
    <T> T asUserResult(UserIdentity userIdentity, Supplier<T> supplier);

    /**
     * Run the supplied code as the specified user.
     */
    void asUser(UserIdentity userIdentity, Runnable runnable);

    /**
     * Run the supplied code as the internal processing user.
     */
    <T> T asProcessingUserResult(Supplier<T> supplier);

    /**
     * Run the supplied code as the internal processing user.
     */
    void asProcessingUser(Runnable runnable);

    /**
     * Secure the supplied code with the supplied application permission.
     */
    default void secure(final AppPermission permission, final Runnable runnable) {
        secure(permission.asAppPermissionSet(), runnable);
    }

    void secure(AppPermissionSet permission, Runnable runnable);

    /**
     * Secure the supplied code with the supplied application permission.
     */
    default <T> T secureResult(final AppPermission permission, final Supplier<T> supplier) {
        return secureResult(permission.asAppPermissionSet(), supplier);
    }

    <T> T secureResult(AppPermissionSet permissionSet, Supplier<T> supplier);

    /**
     * Secure the supplied code to ensure that there is a current authenticated user.
     */
    void secure(Runnable runnable);

    /**
     * Secure the supplied code to ensure that there is a current authenticated user.
     */
    <T> T secureResult(Supplier<T> supplier);

    /**
     * Run the supplied code regardless of whether there is a current user and also allow all inner code to run
     * insecurely even if it is often secured when executed from other entry points.
     */
    void insecure(Runnable runnable);

    /**
     * Run the supplied code regardless of whether there is a current user and also allow all inner code to run
     * insecurely even if it is often secured when executed from other entry points.
     */
    <T> T insecureResult(Supplier<T> supplier);
}
