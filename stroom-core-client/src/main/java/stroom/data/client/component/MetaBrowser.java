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

package stroom.data.client.component;

import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/// A data browser: streams, above the streams related to the one selected, above its data (all
/// shown here by its controller).
public class MetaBrowser extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    SimplePanel streamList;
    @UiField
    SimplePanel streamRelationList;
    @UiField
    SimplePanel data;

    /// Makes the browser, with nothing in it.
    public MetaBrowser() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @param streamList The streams.
    public void setStreamList(final IsWidget streamList) {
        this.streamList.setWidget(streamList);
    }

    /// @param streamRelationList The streams related to the one selected.
    public void setStreamRelationList(final IsWidget streamRelationList) {
        this.streamRelationList.setWidget(streamRelationList);
    }

    /// @param data The data of the stream selected.
    public void setData(final IsWidget data) {
        this.data.setWidget(data);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, MetaBrowser> {

    }
}
