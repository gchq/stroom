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

package stroom.explorer.shared;

import stroom.entity.shared.Action;
import stroom.util.shared.SharedMap;

import java.util.List;

public class FetchExplorerPermissionsAction extends Action<SharedMap<ExplorerNode, ExplorerPermissions>> {
    private static final long serialVersionUID = 6474393620176001033L;

    private List<ExplorerNode> explorerNodeList;

    public FetchExplorerPermissionsAction() {
        // Default constructor necessary for GWT serialisation.
    }

    public FetchExplorerPermissionsAction(final List<ExplorerNode> explorerNodeList) {
        this.explorerNodeList = explorerNodeList;
    }

    public List<ExplorerNode> getExplorerNodeList() {
        return explorerNodeList;
    }

    @Override
    public String getTaskName() {
        return "Fetch Explorer Permissions";
    }
}
