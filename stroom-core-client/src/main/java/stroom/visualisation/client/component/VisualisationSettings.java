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

package stroom.visualisation.client.component;

import stroom.editor.client.component.CodeEditor;
import stroom.widget.component.client.Component;
import stroom.widget.dropdowntree.client.DropDownBox;
import stroom.widget.form.client.TextBox;
import stroom.widget.util.client.DirtyKeyDownHander;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// A visualisation's Settings tab: its function's name, the script that defines it, and the JSON
/// of the options a dashboard offers, whose picker and editor its controller
/// ([VisualisationSettingsController]) attaches to.
public class VisualisationSettings extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    TextBox functionName;
    @UiField
    DropDownBox script;
    @UiField
    CodeEditor settings;

    private Runnable onChange = () -> {
    };

    /// Makes an empty tab.
    public VisualisationSettings() {
        initWidget(BINDER.createAndBindUi(this));
        functionName.addKeyDownHandler(new DirtyKeyDownHander() {
            @Override
            public void onDirty(final KeyDownEvent event) {
                onChange.run();
            }
        });
    }

    /// @return The script's picker, for the controller to attach to.
    public DropDownBox getScriptBox() {
        return script;
    }

    /// @return The options' editor, for the controller to attach to.
    public CodeEditor getSettingsEditor() {
        return settings;
    }

    /// @return The function's name, as typed.
    public String getFunctionName() {
        return functionName.getText();
    }

    /// @param name The function's name.
    public void setFunctionName(final String name) {
        functionName.setText(name);
    }

    /// @param onChange What to run when the user changes the function's name.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        functionName.setReadOnly(readOnly);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, VisualisationSettings> {

    }
}
