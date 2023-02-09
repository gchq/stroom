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

import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.BulkActionResult;
import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.FetchExplorerNodeResult;
import stroom.explorer.shared.FindExplorerNodeCriteria;
import stroom.explorer.shared.PermissionInheritance;

import java.util.List;

class MockExplorerService implements ExplorerService {

    @Override
    public FetchExplorerNodeResult getData(final FindExplorerNodeCriteria criteria) {
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
    public BulkActionResult copy(final List<ExplorerNode> explorerNodes,
                                 final ExplorerNode destinationFolder,
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
    public List<DocumentType> getNonSystemTypes() {
        return null;
    }

    @Override
    public List<DocumentType> getVisibleTypes() {
        return null;
    }
}
