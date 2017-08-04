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

package stroom.folder.server;

import org.springframework.stereotype.Component;
import stroom.entity.server.FolderService;
import stroom.entity.shared.FindFolderCriteria;
import stroom.entity.shared.Folder;
import stroom.explorer.server.AbstractExplorerDataProvider;
import stroom.explorer.server.ProvidesExplorerData;
import stroom.explorer.server.TreeModel;
import stroom.explorer.shared.DocumentType;

import javax.inject.Inject;
import javax.inject.Named;

@ProvidesExplorerData
@Component
public class FolderExplorerDataProvider extends AbstractExplorerDataProvider<Folder, FindFolderCriteria> {
    public static final String ICON_URL = DocumentType.DOC_IMAGE_URL + Folder.ENTITY_TYPE + ".svg";

    private final FolderService folderService;

    @Inject
    FolderExplorerDataProvider(@Named("cachedFolderService") final FolderService cachedFolderService, final FolderService folderService) {
        super(cachedFolderService);
        this.folderService = folderService;
    }

    @Override
    public void addItems(final TreeModel treeModel) {
        addItems(folderService, treeModel);
    }

    @Override
    public String getType() {
        return Folder.ENTITY_TYPE;
    }

    @Override
    public String getDisplayType() {
        return Folder.ENTITY_TYPE;
    }

    @Override
    public int getPriority() {
        return 1;
    }
}
