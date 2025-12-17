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

package stroom.credentials.impl.db;

import stroom.docref.DocRef;
import stroom.security.api.DocumentPermissionService;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.DocumentUserPermissions;
import stroom.security.shared.FetchDocumentUserPermissionsRequest;
import stroom.security.shared.SingleDocumentPermissionChangeRequest;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;

import java.util.Set;

public class MockDocumentPermissionService implements DocumentPermissionService {

    @Override
    public DocumentPermission getPermission(final DocRef docRef, final UserRef userRef) {
        return null;
    }

    @Override
    public void setPermission(final DocRef docRef,
                              final UserRef userRef,
                              final DocumentPermission permission) {

    }

    @Override
    public void removeAllDocumentPermissions(final DocRef docRef) {

    }

    @Override
    public void removeAllDocumentPermissions(final Set<DocRef> docRefs) {

    }

    @Override
    public void addDocumentPermissions(final DocRef sourceDocRef, final DocRef destDocRef) {

    }

    @Override
    public Boolean changeDocumentPermissions(final SingleDocumentPermissionChangeRequest request) {
        return null;
    }

    @Override
    public ResultPage<DocumentUserPermissions>
        fetchDocumentUserPermissions(final FetchDocumentUserPermissionsRequest request) {
        return null;
    }
}
