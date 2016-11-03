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

package stroom.pipeline.server;

import stroom.entity.shared.FolderService;
import stroom.explorer.server.AbstractExplorerDataProvider;
import stroom.explorer.server.ProvidesExplorerData;
import stroom.explorer.server.TreeModel;
import stroom.explorer.shared.ExplorerData;
import stroom.pipeline.shared.FindXSLTCriteria;
import stroom.pipeline.shared.XSLT;
import stroom.pipeline.shared.XSLTService;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Map;

@ProvidesExplorerData
@Component
public class XSLTExplorerDataProvider extends AbstractExplorerDataProvider<XSLT, FindXSLTCriteria> {
    private final XSLTService xsltService;

    @Inject
    XSLTExplorerDataProvider(@Named("cachedFolderService") final FolderService folderService, final XSLTService xsltService) {
        super(folderService);
        this.xsltService = xsltService;
    }

    @Override
    public void addItems(final TreeModel treeModel) {
        addItems(xsltService, treeModel);
    }

    @Override
    public String getType() {
        return XSLT.ENTITY_TYPE;
    }

    @Override
    public String getDisplayType() {
        return XSLT.ENTITY_TYPE;
    }

    @Override
    public int getPriority() {
        return 5;
    }
}
