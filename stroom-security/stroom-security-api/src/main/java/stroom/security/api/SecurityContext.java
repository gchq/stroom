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
import stroom.util.shared.HasAuditableUserIdentity;
import stroom.util.shared.SimpleUserName;
import stroom.util.shared.UserName;

import java.util.function.Supplier;

public interface SecurityContext extends HasAuditableUserIdentity {

    /**
     * Get the id of the user associated with this security context.
     * If using an external IDP this may not be a very user-friendly value so for anything
     * where the user identity is going to be shown in the UI, use {@link SecurityContext#getUserIdentityForAudit()}
     *
     * @return The id of the user associated with this security context.
     */
    String getSubjectId();

    /**
     * Retrieve the user's UUID if supported by the type of user.
     * This is the Stroom User UUID.
     */
    String getUserUuid();

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
     * Creates a {@link UserIdentity} instance for the supplied subjectId. Will return
     * null if the user does not exist in stroom.
     */
    default UserIdentity createIdentity(String subjectId) {
        return createIdentity(subjectId, false);
    }

    /**
     * Creates a {@link UserIdentity} instance for the supplied subjectId. Will create
     * a new stroom user record if one doesn't exist.
     */
    UserIdentity createIdentity(String subjectId, boolean ensureUser);

    /**
     * Creates a {@link UserIdentity} instance for the supplied userUuid. Will return
     * null if the user does not exist in stroom.
     */
    UserIdentity createIdentityByUserUuid(String userUuid);

    /**
     * Gets the identity of the current user.
     *
     * @return The identity of the current user.
     */
    UserIdentity getUserIdentity();

    default UserName getUserName() {
        final UserIdentity userIdentity = getUserIdentity();
        final String displayName;
        final String fullName;
        final boolean isGroup;

        if (userIdentity != null) {
            displayName = userIdentity.getDisplayName();
            fullName = userIdentity.getFullName().orElse(null);
            isGroup = userIdentity.isGroup();
        } else {
            displayName = null;
            fullName = null;
            isGroup = false;
        }
        return new SimpleUserName(getSubjectId(), displayName, fullName, getUserUuid(), isGroup);
    }

    /**
     * Check if the user associated with this security context is logged in.
     *
     * @return True if the user is logged in.
     */
    boolean isLoggedIn();

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
    boolean hasAppPermission(String permission);

    /**
     * Check if the user associated with this security context has the requested
     * permission on the document specified by the document type and document
     * id.
     *
     * @param documentUuid The uuid of the document.
     * @param permission   The permission we are checking for.
     * @return True if the user associated with the security context has the
     * requested permission.
     */
    boolean hasDocumentPermission(String documentUuid, String permission);

    /**
     * Check if the user associated with this security context has the requested
     * permission on the document specified by the document docRef.
     *
     * @param docRef     The docRef of the document.
     * @param permission The permission we are checking for.
     * @return True if the user associated with the security context has the
     * requested permission.
     */
    default boolean hasDocumentPermission(DocRef docRef, String permission) {
        return docRef != null && hasDocumentPermission(docRef.getUuid(), permission);
    }

    /**
     * Get the user UUID of the owner of a document. Throws authentication exception if there are multiple users with
     * ownership or no owners.
     *
     * @param docRef The uuid of the document.
     * @return The UUID of the document owner.
     */
    String getDocumentOwnerUuid(DocRef docRef);

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
     * Run the supplied code as an admin user.
     */
    <T> T asAdminUserResult(Supplier<T> supplier);

    /**
     * Run the supplied code as an admin user.
     */
    void asAdminUser(Runnable runnable);

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
    void secure(String permission, Runnable runnable);

    /**
     * Secure the supplied code with the supplied application permission.
     */
    <T> T secureResult(String permission, Supplier<T> supplier);

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
