/*
 * Copyright 2016 Crown Copyright
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

package stroom.folder.server;

import org.springframework.stereotype.Component;
import stroom.entity.shared.FolderService;
import stroom.explorer.server.ExplorerDataProvider;
import stroom.explorer.server.ProvidesExplorerData;
import stroom.explorer.server.TreeModel;
import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.ExplorerData;
import stroom.explorer.shared.SimpleExplorerItem;

@ProvidesExplorerData
@Component
public class FolderRootExplorerDataProvider implements ExplorerDataProvider {
    private static final String ICON_URL = DocumentType.DOC_IMAGE_URL + FolderService.ROOT + ".svg";
    public static final ExplorerData ROOT = new SimpleExplorerItem(ICON_URL, FolderService.ROOT, "System", null);

    @Override
    public void addItems(final TreeModel treeModel) {
        treeModel.add(null, ROOT);
    }

    @Override
    public String getType() {
        return FolderService.ROOT;
    }

    @Override
    public String getDisplayType() {
        return FolderService.ROOT;
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public String getIconUrl() {
        return ICON_URL;
    }
}
