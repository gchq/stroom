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
import com.google.gwt.user.client.ui.Widget;

/// A data browser: streams, above the streams related to the one selected, above its data (all
/// shown here by its controller).
public class MetaBrowser extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    MetaList streamList;
    @UiField
    MetaList streamRelationList;
    @UiField
    DataViewer data;

    /// Makes the browser, with nothing in it.
    public MetaBrowser() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @return The streams, which its controller attaches the list's controller to.
    public MetaList getStreamList() {
        return streamList;
    }

    /// @return The streams related to the one selected, which its controller attaches the list's
    /// controller to.
    public MetaList getStreamRelationList() {
        return streamRelationList;
    }

    /// @return The data of the stream selected, which its controller attaches the viewer's
    /// controller to.
    public DataViewer getData() {
        return data;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, MetaBrowser> {

    }
}
