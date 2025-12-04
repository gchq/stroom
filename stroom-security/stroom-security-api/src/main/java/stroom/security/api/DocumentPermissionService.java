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
import stroom.security.shared.DocumentUserPermissions;
import stroom.security.shared.FetchDocumentUserPermissionsRequest;
import stroom.security.shared.SingleDocumentPermissionChangeRequest;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;

import java.util.Set;

public interface DocumentPermissionService {

    DocumentPermission getPermission(DocRef docRef, UserRef userRef);

    void setPermission(DocRef docRef, UserRef userRef, DocumentPermission permission);

    void removeAllDocumentPermissions(DocRef docRef);

    void removeAllDocumentPermissions(Set<DocRef> docRefs);

    /**
     * Add all permissions from one doc to another.
     *
     * @param sourceDocRef The source doc to copy permissions from.
     * @param destDocRef   The dest doc to copy permissions to.
     */
    void addDocumentPermissions(DocRef sourceDocRef,
                                DocRef destDocRef);

    Boolean changeDocumentPermissions(SingleDocumentPermissionChangeRequest request);

    ResultPage<DocumentUserPermissions> fetchDocumentUserPermissions(FetchDocumentUserPermissionsRequest request);
}
