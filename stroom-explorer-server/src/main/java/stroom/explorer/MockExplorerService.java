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
 *
 */

package stroom.explorer;

import stroom.entity.shared.PermissionInheritance;
import stroom.explorer.shared.BulkActionResult;
import stroom.explorer.shared.DocumentTypes;
import stroom.explorer.shared.FetchExplorerNodeResult;
import stroom.explorer.shared.FindExplorerNodeCriteria;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.DocRefInfo;

import java.util.List;

class MockExplorerService implements ExplorerService {
    @Override
    public FetchExplorerNodeResult getData(final FindExplorerNodeCriteria criteria) {
        return null;
    }

    @Override
    public DocumentTypes getDocumentTypes() {
        return null;
    }

    @Override
    public DocRef create(final String docType, final String docName, final DocRef destinationFolderRef, final PermissionInheritance permissionInheritance) {
        return null;
    }

    @Override
    public BulkActionResult copy(final List<DocRef> docRefs, final DocRef destinationFolderRef, final PermissionInheritance permissionInheritance) {
        return null;
    }

    @Override
    public BulkActionResult move(final List<DocRef> docRefs, final DocRef destinationFolderRef, final PermissionInheritance permissionInheritance) {
        return null;
    }

    @Override
    public DocRef rename(final DocRef docRef, final String docName) {
        return null;
    }

    @Override
    public BulkActionResult delete(final List<DocRef> docRefs) {
        return null;
    }

    @Override
    public DocRefInfo info(final DocRef docRef) {
        return null;
    }

    @Override
    public void rebuildTree() {

    }
}