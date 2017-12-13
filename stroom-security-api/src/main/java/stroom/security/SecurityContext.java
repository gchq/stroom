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

package stroom.security;

public interface SecurityContext {
    /**
     * Temporarily set a different user to perform an action.
     *
     * @param token The user token to push.
     */
    void pushUser(String token);

    /**
     * Remove a temporary user from the stack.
     *
     * @return The removed user.
     */
    String popUser();

    /**
     * Get the id of the user associated with this security context.
     *
     * @return The id of the user associated with this security context.
     */
    String getUserId();

    /**
     * Get the uuid of the user associated with this security context.
     *
     * @return The uuid of the user associated with this security context.
     */
    String getUserUuid();

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
     * Temporarily elevate a users permissions so that documents can be read that they have 'use' permission on.
     */
    void elevatePermissions();

    /**
     * Restore permissions to their pre-elevated state.
     */
    void restorePermissions();

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
     * @param documentType The type of document.
     * @param documentUuid The id of the document.
     * @param permission   The permission we are checking for.
     * @return True if the user associated with the security context has the
     * requested permission.
     */
    boolean hasDocumentPermission(String documentType, String documentUuid, String permission);

    void clearDocumentPermissions(String documentType, String documentUuid);

    void addDocumentPermissions(String sourceType, String sourceUuid, String documentType, String documentUuid, boolean owner);
}
