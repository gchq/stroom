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

package stroom.security.client;

public interface ClientSecurityContext {
    /**
     * Get the id of the user associated with this security context.
     *
     * @return The id of the user associated with this security context.
     */
    String getUserId();

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
    boolean hasAppPermission(String permission);

    /**
     * Check if the user associated with this security context has the requested
     * permission on the document specified by the document type and document
     * id.
     *
     * @param documentType The type of document.
     * @param documentId   The id of the document.
     * @param permission   The permission we are checking for.
     * @return True if the user associated with the security context has the
     * requested permission.
     */
    Future<Boolean> hasDocumentPermission(String documentType, String documentId, String permission);
}
