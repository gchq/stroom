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

package stroom.security.client.api;

import stroom.docref.DocRef;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.UserRef;

import java.util.function.Consumer;

public interface ClientSecurityContext {

    /**
     * Get the id of the user associated with this security context.
     *
     * @return The id of the user associated with this security context.
     */
    UserRef getUserRef();

    /**
     * Determine if the passed {@link UserRef} is the same user as the current
     * authenticated user.
     *
     * @param userRef The user to compare against the current authenticated user.
     * @return True if userRef is equal to the current authenticated user.
     */
    boolean isCurrentUser(final UserRef userRef);

    /**
     * Check if the user associated with this security context is logged in.
     *
     * @return True if the user is logged in.
     */
    boolean isLoggedIn();

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
     * permission on the document specified by the document uuid.
     *
     * @param docRef     The ref of the document.
     * @param permission The permission we are checking for.
     * @return True if the user associated with the security context has the
     * requested permission.
     */
    void hasDocumentPermission(DocRef docRef,
                               DocumentPermission permission,
                               Consumer<Boolean> consumer,
                               Consumer<Throwable> errorHandler,
                               TaskMonitorFactory taskMonitorFactory);
}
