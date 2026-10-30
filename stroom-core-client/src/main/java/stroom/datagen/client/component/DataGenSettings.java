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

package stroom.datagen.client.component;

import stroom.editor.client.component.CodeEditor;
import stroom.widget.component.client.Component;
import stroom.widget.dropdowntree.client.DropDownBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// A data generator's Settings tab: the destination feed's picker above the template's editor,
/// which its controller ([DataGenSettingsController]) attaches to.
public class DataGenSettings extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    DropDownBox destinationFeed;
    @UiField
    CodeEditor template;

    /// Makes an empty tab.
    public DataGenSettings() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @return The destination feed's picker, for the controller to attach to.
    public DropDownBox getDestinationFeedBox() {
        return destinationFeed;
    }

    /// @return The template's editor, for the controller to attach to.
    public CodeEditor getTemplateEditor() {
        return template;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, DataGenSettings> {

    }
}
