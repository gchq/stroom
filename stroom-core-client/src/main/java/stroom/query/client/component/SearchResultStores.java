/*
 * Copyright 2021 Crown Copyright
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

package stroom.query.client.component;

import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/// The content of Search Result Stores: the stores above the details of the one chosen.
public class SearchResultStores extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    ResultStoreList list;
    @UiField
    SimplePanel infoPanel;

    /// Makes empty content.
    public SearchResultStores() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @return The list of stores, which its controller attaches the list's controller to.
    public ResultStoreList getList() {
        return list;
    }

    /// @param details The chosen store's details, a table.
    public void setDetails(final SafeHtml details) {
        infoPanel.getElement().setInnerHTML(details.asString());
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, SearchResultStores> {

    }
}
