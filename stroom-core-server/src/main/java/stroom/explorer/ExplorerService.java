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
import stroom.docref.DocRef;
import stroom.query.api.v2.DocRefInfo;

import java.util.List;

public interface ExplorerService {
    FetchExplorerNodeResult getData(FindExplorerNodeCriteria criteria);

    DocumentTypes getDocumentTypes();

    DocRef create(String docType, String docName, DocRef destinationFolderRef, PermissionInheritance permissionInheritance);

    BulkActionResult copy(List<DocRef> docRefs, DocRef destinationFolderRef, PermissionInheritance permissionInheritance);

    BulkActionResult move(List<DocRef> docRefs, DocRef destinationFolderRef, PermissionInheritance permissionInheritance);

    DocRef rename(DocRef docRef, String docName);

    BulkActionResult delete(List<DocRef> docRefs);

    DocRefInfo info(DocRef docRef);

    void rebuildTree();
}
