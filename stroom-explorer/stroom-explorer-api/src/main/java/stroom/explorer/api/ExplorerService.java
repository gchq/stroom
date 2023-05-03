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

package stroom.explorer.api;

import stroom.docref.DocRef;
import stroom.explorer.shared.BulkActionResult;
import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.ExplorerDocContentMatch;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.FetchExplorerNodeResult;
import stroom.explorer.shared.FindExplorerNodeCriteria;
import stroom.explorer.shared.FindExplorerNodeQuery;
import stroom.explorer.shared.PermissionInheritance;
import stroom.util.shared.Clearable;
import stroom.util.shared.ResultPage;

import java.util.List;
import java.util.Optional;

public interface ExplorerService extends Clearable {

    FetchExplorerNodeResult getData(FindExplorerNodeCriteria criteria);

    ExplorerNode create(String docType,
                        String docName,
                        ExplorerNode destinationFolder,
                        PermissionInheritance permissionInheritance);

    BulkActionResult copy(List<ExplorerNode> explorerNodes,
                          ExplorerNode destinationFolder,
                          PermissionInheritance permissionInheritance);

    BulkActionResult move(List<ExplorerNode> explorerNodes,
                          ExplorerNode destinationFolder,
                          PermissionInheritance permissionInheritance);

    ExplorerNode rename(ExplorerNode explorerNode, String docName);

    BulkActionResult delete(List<ExplorerNode> explorerNodes);

    void rebuildTree();

    List<DocumentType> getNonSystemTypes();

    List<DocumentType> getVisibleTypes();

    ResultPage<ExplorerDocContentMatch> findContent(FindExplorerNodeQuery request);

    Optional<ExplorerNode> getFromDocRef(DocRef docRef);
}