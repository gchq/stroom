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

package stroom.security.api;

import stroom.docref.DocRef;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.HasAuditableUserIdentity;
import stroom.util.shared.UserRef;

import java.util.function.Supplier;

public interface SecurityContext extends HasAuditableUserIdentity {

    /**
     * @return The user identity in a form suitable for use in audit events, for display
     * in the UI, or in exception messages. Returns {@link UserIdentity#getDisplayName()} or
     * if that is not set {@link UserIdentity#getSubjectId()}.
     */
    default String getUserIdentityForAudit() {
        final UserIdentity userIdentity = getUserIdentity();
        if (userIdentity == null) {
            return null;
        }
        return userIdentity.getUserIdentityForAudit();
    }

    /**
     * Gets the identity of the current user.
     *
     * @return The identity of the current user.
     */
    UserIdentity getUserIdentity();

    /**
     * Get the current user identity as a user ref object.
     *
     * @return A user ref object representing the current authenticated user.
     */
    UserRef getUserRef();

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
     * Find out if we are running with elevated permissions.
     *
     * @return True if we are running in use as read mode.
     */
    boolean isUseAsRead();

    /**
     * Check if the user associated with this security context has the requested
     * permission to use the specified functionality.
     *
     * @param permission The permission we are checking for.
     * @return True if the user associated with the security context has the
     * requested permission.
     */
    boolean hasAppPermission(AppPermission permission);

    /**
     * Check if the user associated with this security context has the requested
     * permission on the document specified by the document docRef.
     *
     * @param docRef     The docRef of the document.
     * @param permission The permission we are checking for.
     * @return True if the user associated with the security context has the
     * requested permission.
     */
    boolean hasDocumentPermission(DocRef docRef, DocumentPermission permission);

    /**
     * Check if the user associated with this security context has the requested
     * permission on the document specified by the document docRef.
     *
     * @param docRef       The docRef of the parent folder.
     * @param documentType The document type we want to create.
     * @return True if the user associated with the security context has the
     * requested permission.
     */
    boolean hasDocumentCreatePermission(DocRef docRef, String documentType);

    /**
     * Run the supplied code as the specified user.
     */
    <T> T asUserResult(UserIdentity userIdentity, Supplier<T> supplier);

    /**
     * Run the supplied code as the specified user.
     */
    <T> T asUserResult(UserRef userRef, Supplier<T> supplier);

    /**
     * Run the supplied code as the specified user.
     */
    void asUser(UserRef userRef, Runnable runnable);


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
     * Allow the current user to read items that they only have 'Use' permission on.
     */
    <T> T useAsReadResult(Supplier<T> supplier);

    /**
     * Allow the current user to read items that they only have 'Use' permission on.
     */
    void useAsRead(Runnable runnable);

    /**
     * Secure the supplied code with the supplied application permission.
     */
    void secure(AppPermission permission, Runnable runnable);

    /**
     * Secure the supplied code with the supplied application permission.
     */
    <T> T secureResult(AppPermission permission, Supplier<T> supplier);

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
