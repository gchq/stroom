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

package stroom.dashboard.client.component;

import stroom.data.client.component.ClassificationLabel;
import stroom.widget.button.client.FabButton;
import stroom.widget.component.client.Component;
import stroom.widget.spinner.client.LargeBusySpinner;
import stroom.widget.task.client.TaskMonitorFactory;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/// A dashboard's Text pane: the source of the row selected in a table, as text or HTML (shown here
/// by its controller), with a button to step it and its classification.
public class TextPane extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    SimplePanel content;
    @UiField
    ClassificationLabel classification;
    @UiField
    FabButton steppingButton;
    @UiField
    LargeBusySpinner spinner;

    private Runnable onStep = () -> {
    };

    /// Makes the pane, empty.
    public TextPane() {
        initWidget(BINDER.createAndBindUi(this));
        steppingButton.setOnClick(() -> onStep.run());
    }

    /// @param content What is shown: the text's editor or the HTML.
    public void setContent(final IsWidget content) {
        this.content.setWidget(content);
    }

    /// @param text The data's classification.
    public void setClassification(final String text) {
        classification.setClassification(text);
    }

    /// @return The classification banner, for a controller to attach.
    public ClassificationLabel getClassificationLabel() {
        return classification;
    }

    /// @param visible Whether the Step button shows.
    public void setSteppingVisible(final boolean visible) {
        steppingButton.setVisible(visible);
    }

    /// @param enabled Whether the Step button can be pressed, i.e. a row is selected.
    public void setSteppingEnabled(final boolean enabled) {
        steppingButton.setEnabled(enabled);
        steppingButton.setTitle(enabled
                ? "Enter Stepping Mode"
                : "Select a row to step through its source");
    }

    /// @return The pane's spinner, which shows its fetches.
    public TaskMonitorFactory getTaskMonitor() {
        return spinner;
    }

    /// @param onStep Run when the Step button is pressed.
    public void setOnStep(final Runnable onStep) {
        this.onStep = onStep;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, TextPane> {

    }
}
