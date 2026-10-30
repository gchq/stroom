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

package stroom.processor.client.component;

import stroom.widget.component.client.Component;
import stroom.widget.dropdowntree.client.DropDownBox;
import stroom.widget.selection.client.SelectionBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// A feed dependency's feed and stream type, as its dialog shows them.
public class FeedDependencyEditor extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    DropDownBox feed;
    @UiField
    SelectionBox<String> type;

    /// Makes empty content.
    public FeedDependencyEditor() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @return The feed's picker, for its controller to attach to.
    public DropDownBox getFeedBox() {
        return feed;
    }

    /// @return The stream type's box, for its controller to fill.
    public SelectionBox<String> getTypeBox() {
        return type;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, FeedDependencyEditor> {

    }
}
