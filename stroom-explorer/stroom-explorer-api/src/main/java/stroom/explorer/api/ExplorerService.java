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

package stroom.explorer.api;

import stroom.docref.DocRef;
import stroom.docstore.shared.DocumentType;
import stroom.explorer.shared.AdvancedDocumentFindRequest;
import stroom.explorer.shared.AdvancedDocumentFindWithPermissionsRequest;
import stroom.explorer.shared.BulkActionResult;
import stroom.explorer.shared.DocContentHighlights;
import stroom.explorer.shared.DocumentFindRequest;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerResource.TagFetchMode;
import stroom.explorer.shared.FetchExplorerNodeResult;
import stroom.explorer.shared.FetchExplorerNodesRequest;
import stroom.explorer.shared.FetchHighlightsRequest;
import stroom.explorer.shared.FindInContentRequest;
import stroom.explorer.shared.FindInContentResult;
import stroom.explorer.shared.FindResult;
import stroom.explorer.shared.FindResultWithPermissions;
import stroom.explorer.shared.PermissionInheritance;
import stroom.util.shared.Clearable;
import stroom.util.shared.DocPath;
import stroom.util.shared.ResultPage;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ExplorerService extends Clearable {

    FetchExplorerNodeResult getData(FetchExplorerNodesRequest criteria);

    ExplorerNode create(String docType,
                        String docName,
                        ExplorerNode destinationFolder,
                        PermissionInheritance permissionInheritance);

    /**
     * @param docPath The path to ensure. Must be relative if baseNode is not the root node.
     * @return The leaf node in path.
     */
    ExplorerNode ensureFolderPath(DocPath docPath,
                                  PermissionInheritance permissionInheritance);

    /**
     * @param docPath The path to ensure. Must be relative if baseNode is not the root node.
     * @param baseNode The node to append path onto.
     * @return The leaf node in path.
     */
    ExplorerNode ensureFolderPath(DocPath docPath,
                                  final ExplorerNode baseNode, PermissionInheritance permissionInheritance);

    BulkActionResult copy(List<ExplorerNode> explorerNodes,
                          ExplorerNode destinationFolder,
                          boolean allowRename,
                          String docName,
                          PermissionInheritance permissionInheritance);

    BulkActionResult move(List<ExplorerNode> explorerNodes,
                          ExplorerNode destinationFolder,
                          PermissionInheritance permissionInheritance);

    ExplorerNode rename(ExplorerNode explorerNode, String docName);

    ExplorerNode updateTags(ExplorerNode explorerNode);

    /**
     * @param docRefs The nodes to add tags to.
     * @param tags    Tags to add (if not already present) to all the nodes represented by docRefs.
     *                Will not error if tags are already present.
     */
    void addTags(List<DocRef> docRefs, Set<String> tags);

    /**
     * @param docRefs The nodes to remove tags from.
     * @param tags    Tags to remove (if present) from all the nodes represented by docRefs.
     *                Will not error if tags are not present.
     */
    void removeTags(List<DocRef> docRefs, Set<String> tags);

    BulkActionResult delete(List<ExplorerNode> explorerNodes);

    void rebuildTree();

    List<DocumentType> getTypes();

    /**
     * @return All known node tags + tags from {@link stroom.explorer.shared.StandardExplorerTags}
     */
    Set<String> getTags();

    /**
     * @return All tags held by at least one of docRefs
     */
    Set<String> getTags(final Collection<DocRef> docRefs, final TagFetchMode fetchMode);

    List<DocumentType> getVisibleTypes();

    ResultPage<FindResult> find(DocumentFindRequest request);

    ResultPage<FindResult> advancedFind(AdvancedDocumentFindRequest request);

    ResultPage<FindResultWithPermissions> advancedFindWithPermissions(
            AdvancedDocumentFindWithPermissionsRequest request);

    ResultPage<FindInContentResult> findInContent(FindInContentRequest request);

    DocContentHighlights fetchHighlights(FetchHighlightsRequest request);

    Optional<ExplorerNode> getFromDocRef(DocRef docRef);

    Set<String> parseNodeTags(final String tagsStr);

    String nodeTagsToString(final Set<String> tags);
}
