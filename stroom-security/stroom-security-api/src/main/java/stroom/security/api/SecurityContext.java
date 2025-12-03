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

import stroom.docref.DocRef;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.HasAuditableUserIdentity;
import stroom.util.shared.UserRef;

import java.util.Objects;
import java.util.function.Supplier;

public interface SecurityContext extends CommonSecurityContext, HasAuditableUserIdentity {

    /**
     * @return The user identity in a form suitable for use in audit events, for display
     * in the UI, or in exception messages. Returns {@link UserIdentity#getDisplayName()} or
     * if that is not set {@link UserIdentity#subjectId()}.
     */
    default String getUserIdentityForAudit() {
        final UserIdentity userIdentity = getUserIdentity();
        if (userIdentity == null) {
            return null;
        }
        return userIdentity.getUserIdentityForAudit();
    }

    /**
     * Get the current user identity as a user ref object.
     *
     * @return A user ref object representing the current authenticated user.
     */
    UserRef getUserRef();

    /**
     * Determine if the passed {@link UserRef} is the same user as the current
     * authenticated user. Being administrator has no bearing on the result of this method.
     *
     * @param userRef The user to compare against the current authenticated user.
     * @return True if userRef is equal to the current authenticated user.
     */
    default boolean isCurrentUser(final UserRef userRef) {
        return Objects.equals(getUserRef(), userRef);
    }

    /**
     * Determine if the passed userUuid is the same user as the current
     * authenticated user. Being administrator has no bearing on the result of this method.
     *
     * @param userUuid The user to compare against the current authenticated user.
     * @return True if userUuid is equal to the userUuid of the current authenticated user.
     */
    default boolean isCurrentUser(final String userUuid) {
        final String currentUserUuid = getUserRef().getUuid();
        return Objects.equals(currentUserUuid, userUuid);
    }

    /**
     * Find out if we are running with elevated permissions.
     *
     * @return True if we are running in use as read mode.
     */
    boolean isUseAsRead();

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
    <T> T asUserResult(UserRef userRef, Supplier<T> supplier);

    /**
     * Run the supplied code as the specified user.
     */
    void asUser(UserRef userRef, Runnable runnable);

    /**
     * Allow the current user to read items that they only have 'Use' permission on.
     */
    <T> T useAsReadResult(Supplier<T> supplier);

    /**
     * Allow the current user to read items that they only have 'Use' permission on.
     */
    void useAsRead(Runnable runnable);

    /**
     * See if the current user is in the specified group.
     */
    boolean inGroup(String groupName);
}
