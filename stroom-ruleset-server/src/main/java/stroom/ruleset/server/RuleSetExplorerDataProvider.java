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

package stroom.ruleset.server;

import org.springframework.stereotype.Component;
import stroom.entity.shared.Folder;
import stroom.explorer.server.ExplorerDataProvider;
import stroom.explorer.server.ProvidesExplorerData;
import stroom.explorer.server.TreeModel;
import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.ExplorerData;
import stroom.folder.server.FolderExplorerDataProvider;
import stroom.folder.server.FolderRootExplorerDataProvider;
import stroom.query.api.v1.DocRef;
import stroom.ruleset.shared.RuleSet;
import stroom.util.shared.HasNodeState.NodeState;

import java.util.Set;

@ProvidesExplorerData
@Component
class RuleSetExplorerDataProvider implements ExplorerDataProvider {
    private final RuleSetServiceImpl dataReceiptService;

    RuleSetExplorerDataProvider(final RuleSetServiceImpl dataReceiptService) {
        this.dataReceiptService = dataReceiptService;
    }

    @Override
    public void addItems(final TreeModel treeModel) {
        final Set<RuleSet> set = dataReceiptService.list();
        for (final RuleSet entity : set) {
            // Get parent explorer data.
            ExplorerData parent = FolderRootExplorerDataProvider.ROOT;
            final String parentFolderUUID = entity.getParentFolderUUID();
            if (parentFolderUUID != null) {
                // TODO : This is a temporary fudge until the separate explorer service is created - we shouldn't need to poke insecure holes in the document service.
                final DocRef docRef = new DocRef(Folder.ENTITY_TYPE, parentFolderUUID);
                parent = ExplorerData.create(FolderExplorerDataProvider.ICON_URL, docRef, NodeState.LEAF);
            }

            // Get entity explorer data.
            final DocRef docRef = new DocRef(getType(), entity.getUuid(), entity.getName());
            final ExplorerData entityData = ExplorerData.create(getIconUrl(), docRef, NodeState.LEAF);
            treeModel.add(parent, entityData);
        }
    }

    @Override
    public String getDisplayType() {
        return "Rule Set";
    }

    @Override
    public int getPriority() {
        return 100;
    }

    @Override
    public String getIconUrl() {
        return DocumentType.DOC_IMAGE_URL + getType() + ".svg";
    }

    @Override
    public String getType() {
        return RuleSet.DOCUMENT_TYPE;
    }
}
