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

package stroom.security.impl;

import stroom.security.shared.DocumentPermission;
import stroom.security.shared.DocumentUserPermissions;
import stroom.security.shared.FetchDocumentUserPermissionsRequest;
import stroom.util.shared.ResultPage;

import java.util.BitSet;
import java.util.Set;

public interface DocumentPermissionDao {

    UserDocumentPermissions getPermissionsForUser(String userUuid);

    DocumentPermission getDocumentUserPermission(String documentUuid, String userUuid);

    void setDocumentUserPermission(String documentUuid, String userUuid, DocumentPermission permission);

    void removeDocumentUserPermission(String documentUuid, String userUuid);

    void removeAllDocumentPermissions(String documentUuid);

    BitSet getDocumentUserCreatePermissionsBitSet(String documentUuid, String userUuid);

    Set<String> getDocumentUserCreatePermissions(String documentUuid, String userUuid);

    void addDocumentUserCreatePermission(String documentUuid, String userUuid, String documentType);

    void removeDocumentUserCreatePermission(String documentUuid, String userUuid, String documentType);

    void setDocumentUserCreatePermissions(String documentUuid, String userUuid, Set<String> documentTypes);

    void removeAllDocumentUserCreatePermissions(String documentUuid, String userUuid);

    void removeAllDocumentCreatePermissions(String documentUuid);

    /**
     * BULK operation to add all permissions from a source document to a target document.
     */
    void addDocumentPermissions(String sourceDocUuid, String destDocUuid);

    /**
     * BULK operation to add all create permissions from a source document to a target document.
     */
    void addDocumentCreatePermissions(String sourceDocUuid, String destDocUuid);

    /**
     * BULK operation to set all permissions from a source document to a target document.
     */
    void setDocumentPermissions(String sourceDocUuid, String destDocUuid);

    /**
     * BULK operation to set all create permissions from a source document to a target document.
     */
    void setDocumentCreatePermissions(String sourceDocUuid, String destDocUuid);

    ResultPage<DocumentUserPermissions> fetchDocumentUserPermissions(FetchDocumentUserPermissionsRequest request);
}
