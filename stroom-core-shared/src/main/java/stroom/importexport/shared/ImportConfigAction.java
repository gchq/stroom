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

package stroom.importexport.shared;

import stroom.entity.shared.Action;
import stroom.entity.shared.ImportState;
import stroom.util.shared.ResourceKey;

import java.util.List;

public class ImportConfigAction extends Action<ResourceKey> {
    private static final long serialVersionUID = 1799514675431383541L;
    private ResourceKey key;
    private List<ImportState> confirmList;

    public ImportConfigAction() {
        // Default constructor necessary for GWT serialisation.
    }

    public ImportConfigAction(final ResourceKey resourceKey, final List<ImportState> confirmList) {
        this.key = resourceKey;
        this.confirmList = confirmList;
    }

    public ResourceKey getKey() {
        return key;
    }

    public void setKey(final ResourceKey key) {
        this.key = key;
    }

    public void setConfirmList(final List<ImportState> confirmList) {
        this.confirmList = confirmList;
    }

    public List<ImportState> getConfirmList() {
        return confirmList;
    }

    @Override
    public String getTaskName() {
        return "Import";
    }
}
