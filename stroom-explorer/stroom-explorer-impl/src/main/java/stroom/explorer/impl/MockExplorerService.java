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

package stroom.explorer.impl;

import stroom.docref.DocRef;
import stroom.docstore.shared.DocumentType;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.AdvancedDocumentFindRequest;
import stroom.explorer.shared.AdvancedDocumentFindWithPermissionsRequest;
import stroom.explorer.shared.BulkActionResult;
import stroom.explorer.shared.DocContentHighlights;
import stroom.explorer.shared.DocumentFindRequest;
import stroom.explorer.shared.ExplorerConstants;
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
import stroom.util.shared.DocPath;
import stroom.util.shared.ResultPage;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

class MockExplorerService implements ExplorerService {

    @Override
    public FetchExplorerNodeResult getData(final FetchExplorerNodesRequest criteria) {
        return null;
    }

    @Override
    public ExplorerNode create(final String docType,
                               final String docName,
                               final ExplorerNode destinationFolder,
                               final PermissionInheritance permissionInheritance) {
        return null;
    }

    @Override
    public ExplorerNode ensureFolderPath(final DocPath docPath, final PermissionInheritance permissionInheritance) {
        return ExplorerConstants.SYSTEM_NODE;
    }

    @Override
    public ExplorerNode ensureFolderPath(final DocPath docPath,
                                         final ExplorerNode baseNode,
                                         final PermissionInheritance permissionInheritance) {
        return ExplorerConstants.SYSTEM_NODE;
    }

    @Override
    public BulkActionResult copy(final List<ExplorerNode> explorerNodes,
                                 final ExplorerNode destinationFolder,
                                 final boolean allowRename,
                                 final String docName,
                                 final PermissionInheritance permissionInheritance) {
        return null;
    }

    @Override
    public BulkActionResult move(final List<ExplorerNode> explorerNodes,
                                 final ExplorerNode destinationFolder,
                                 final PermissionInheritance permissionInheritance) {
        return null;
    }

    @Override
    public ExplorerNode rename(final ExplorerNode explorerNode, final String docName) {
        return null;
    }

    @Override
    public ExplorerNode updateTags(final ExplorerNode explorerNode) {
        return null;
    }

    @Override
    public void addTags(final List<DocRef> docRefs, final Set<String> tags) {

    }

    @Override
    public void removeTags(final List<DocRef> docRefs, final Set<String> tags) {

    }

    @Override
    public BulkActionResult delete(final List<ExplorerNode> explorerNodes) {
        return null;
    }

    @Override
    public void rebuildTree() {
    }

    @Override
    public void clear() {
    }

    @Override
    public List<DocumentType> getTypes() {
        return null;
    }

    @Override
    public Set<String> getTags() {
        return null;
    }

    @Override
    public Set<String> getTags(final Collection<DocRef> docRefs, final TagFetchMode fetchMode) {
        return null;
    }

    @Override
    public List<DocumentType> getVisibleTypes() {
        return null;
    }

    @Override
    public ResultPage<FindResult> find(final DocumentFindRequest request) {
        return null;
    }

    @Override
    public ResultPage<FindResult> advancedFind(final AdvancedDocumentFindRequest request) {
        return null;
    }

    @Override
    public ResultPage<FindResultWithPermissions> advancedFindWithPermissions(
            final AdvancedDocumentFindWithPermissionsRequest request) {
        return null;
    }

    @Override
    public ResultPage<FindInContentResult> findInContent(final FindInContentRequest request) {
        return null;
    }

    @Override
    public DocContentHighlights fetchHighlights(final FetchHighlightsRequest request) {
        return null;
    }

    @Override
    public Optional<ExplorerNode> getFromDocRef(final DocRef docRef) {
        return Optional.empty();
    }

    @Override
    public Set<String> parseNodeTags(final String tagsStr) {
        return Collections.emptySet();
    }

    @Override
    public String nodeTagsToString(final Set<String> tags) {
        return null;
    }

}
