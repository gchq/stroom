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

package stroom.security.client.component;

import stroom.item.client.SelectionBox;
import stroom.security.shared.PermissionShowLevel;
import stroom.widget.component.client.Component;
import stroom.widget.quickfilter.client.QuickFilterContextHandler;
import stroom.widget.quickfilter.client.QuickFilterPage;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Consumer;

/// A user's or group's Document Permissions tab: which documents are listed (those the user has
/// explicit permissions on, effective permissions on, or all), then the documents (a list its
/// controller shows here) with a quick filter above them.
public class UserPermissionReport extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    SelectionBox<PermissionShowLevel> permissionVisibility;
    @UiField
    QuickFilterPage documents;

    private Consumer<PermissionShowLevel> onPermissionShowLevelChange = showLevel -> {
    };

    /// Makes the tab, listing the documents the user has explicit permissions on.
    public UserPermissionReport() {
        initWidget(BINDER.createAndBindUi(this));
        permissionVisibility.addItems(PermissionShowLevel.ITEMS);
        permissionVisibility.setValue(PermissionShowLevel.SHOW_EXPLICIT);
        permissionVisibility.addValueChangeHandler(event ->
                onPermissionShowLevelChange.accept(permissionVisibility.getValue()));
    }

    /// @return Which documents are listed.
    public PermissionShowLevel getPermissionShowLevel() {
        return permissionVisibility.getValue();
    }

    /// @param documentsList The documents and the user's permissions on each.
    public void setDocumentsList(final IsWidget documentsList) {
        documents.setContent(documentsList);
    }

    /// Shows why the server rejected the quick filter. See `ResultPage.filterError`.
    ///
    /// @param filterError The reason, or `null` to clear it.
    public void setFilterError(final String filterError) {
        documents.setFilterError(filterError);
    }

    /// @param contextHandler Gives the quick filter its recent filters and remembers the ones used.
    public void setQuickFilterContextHandler(final QuickFilterContextHandler contextHandler) {
        documents.setQuickFilterContextHandler(contextHandler);
    }

    /// @param onPermissionShowLevelChange Given which documents are to be listed, when it changes.
    public void setOnPermissionShowLevelChange(final Consumer<PermissionShowLevel> onPermissionShowLevelChange) {
        this.onPermissionShowLevelChange = onPermissionShowLevelChange;
    }

    /// @param onFilterChange Given the quick filter's text when it changes.
    public void setOnFilterChange(final Consumer<String> onFilterChange) {
        documents.setOnFilterChange(onFilterChange);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, UserPermissionReport> {

    }
}
