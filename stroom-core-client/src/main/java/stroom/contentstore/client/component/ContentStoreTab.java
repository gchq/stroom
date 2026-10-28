/*
 * Copyright 2025 Crown Copyright
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

package stroom.contentstore.client.component;

import stroom.widget.component.client.AriaPanel;
import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/// The Content Store screen: the content packs, and the selected pack's details.
public class ContentStoreTab extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    SimplePanel contentPackList;
    @UiField
    AriaPanel contentPackDetails;

    /// Makes the screen, with no content until it is given it.
    public ContentStoreTab() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// Shows the list of content packs.
    ///
    /// @param view Its view.
    public void setContentPackListView(final IsWidget view) {
        contentPackList.setWidget(view.asWidget());
    }

    /// Shows the selected pack's details.
    ///
    /// @param view Their view.
    public void setContentPackDetailsView(final IsWidget view) {
        contentPackDetails.clear();
        contentPackDetails.add(view);
    }

    // --------------------------------------------------------------------------------

    interface Binder extends UiBinder<Widget, ContentStoreTab> {

    }
}
