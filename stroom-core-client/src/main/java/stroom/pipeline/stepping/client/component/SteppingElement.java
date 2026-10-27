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

package stroom.pipeline.stepping.client.component;

import stroom.data.client.component.ClassificationWrapper;
import stroom.data.client.view.ClassificationLabel;
import stroom.editor.client.component.CodeEditor;
import stroom.widget.component.client.Component;
import stroom.widget.layout.client.SplitPane;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// One pipeline element's panes on the stepping screen, one above another: its code (if it has
/// any), its input (if it changes it) beside its output, and the log of its errors and warnings
/// (when there are any). Each editor is attached to a controller by the owner.
public class SteppingElement extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    SplitPane layout;
    @UiField
    CodeEditor code;
    @UiField
    SplitPane inputAndOutput;
    @UiField
    ClassificationWrapper input;
    @UiField
    CodeEditor inputEditor;
    @UiField
    ClassificationWrapper output;
    @UiField
    CodeEditor outputEditor;
    @UiField
    CodeEditor log;

    /// Makes the panes, with the log hidden.
    public SteppingElement() {
        initWidget(BINDER.createAndBindUi(this));
        layout.setPaneHidden(log, true);
    }

    /// @param visible Whether the code pane is shown, for an element that has code.
    public void setCodeVisible(final boolean visible) {
        layout.setPaneHidden(code, !visible);
    }

    /// @param visible Whether the input pane is shown beside the output, for an element that
    ///                changes its input.
    public void setInputVisible(final boolean visible) {
        inputAndOutput.setPaneHidden(input, !visible);
    }

    /// @param visible Whether the log pane is shown.
    public void setLogVisible(final boolean visible) {
        layout.setPaneHidden(log, !visible);
    }

    /// Shows the data's classification below the input and the output.
    ///
    /// @param classification The classification, or `null` for none.
    public void setClassification(final String classification) {
        input.setClassification(classification);
        output.setClassification(classification);
    }

    /// @return The code's editor.
    public CodeEditor getCode() {
        return code;
    }

    /// @return The input's editor.
    public CodeEditor getInput() {
        return inputEditor;
    }

    /// @return The output's editor.
    public CodeEditor getOutput() {
        return outputEditor;
    }

    /// @return The log's editor.
    public CodeEditor getLog() {
        return log;
    }

    /// @return The classification banner below the input.
    public ClassificationLabel getInputClassificationLabel() {
        return input.getClassificationLabel();
    }

    /// @return The classification banner below the output.
    public ClassificationLabel getOutputClassificationLabel() {
        return output.getClassificationLabel();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, SteppingElement> {

    }
}
