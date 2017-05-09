/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.explorer.client.presenter;

import com.google.inject.Inject;
import stroom.cell.dropdowntree.client.DropDownCell;
import stroom.explorer.shared.EntityData;
import stroom.explorer.shared.ExplorerData;
import stroom.query.api.DocRef;

public class EntityDropDownCell extends DropDownCell<DocRef> {
    private final ExplorerDropDownTreePresenter explorerDropDownTreePresenter;

    private String unselectedText;

    @Inject
    public EntityDropDownCell(final ExplorerDropDownTreePresenter explorerDropDownTreePresenter) {
        this.explorerDropDownTreePresenter = explorerDropDownTreePresenter;
        changeSelection(null);

        explorerDropDownTreePresenter.addDataSelectionHandler(event -> changeSelection(event.getSelectedItem()));
    }

    @Override
    protected String getUnselectedText() {
        return unselectedText;
    }

    public void setUnselectedText(final String unselectedText) {
        this.unselectedText = unselectedText;
    }

    public void setIncludedTypes(final String... includedTypes) {
        explorerDropDownTreePresenter.setIncludedTypes(includedTypes);
    }

    public void setTags(final String... tags) {
        explorerDropDownTreePresenter.setTags(tags);
    }

    public void setRequiredPermissions(final String... requiredPermissions) {
        explorerDropDownTreePresenter.setRequiredPermissions(requiredPermissions);
    }

    public void setAllowFolderSelection(final boolean allowFolderSelection) {
        explorerDropDownTreePresenter.setAllowFolderSelection(allowFolderSelection);
    }

    @Override
    public void showPopup(final DocRef value) {
        explorerDropDownTreePresenter.setSelectedEntityReference(value);
        explorerDropDownTreePresenter.show();
    }

    private void changeSelection(final ExplorerData selection) {
        if (selection == null || !(selection instanceof EntityData)) {
            setValue(null);
        } else {
            setValue(((EntityData)selection).getDocRef());
        }
    }
}
