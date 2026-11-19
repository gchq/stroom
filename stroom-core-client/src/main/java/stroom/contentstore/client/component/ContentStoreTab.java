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

import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// The Content Store screen: the content packs, and the selected pack's details.
public class ContentStoreTab extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    ContentStoreContentPackList contentPackList;
    @UiField
    ContentStoreContentPackDetails contentPackDetails;

    /// Makes the screen, with no packs.
    public ContentStoreTab() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @return The list of content packs, which its controller attaches the list's controller to.
    public ContentStoreContentPackList getContentPackList() {
        return contentPackList;
    }

    /// @return The selected pack's details, which its controller attaches the details' controller
    ///         to.
    public ContentStoreContentPackDetails getContentPackDetails() {
        return contentPackDetails;
    }

    // --------------------------------------------------------------------------------

    interface Binder extends UiBinder<Widget, ContentStoreTab> {

    }
}
