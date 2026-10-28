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

import stroom.widget.button.client.ToolbarButton;
import stroom.widget.button.client.ToolbarToggleButton;
import stroom.widget.component.client.Component;
import stroom.widget.presenter.client.Layer;
import stroom.widget.tab.client.LayerContainerImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.ProvidesResize;
import com.google.gwt.user.client.ui.RequiresResize;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Consumer;

/// The stepping screen: the pipeline's bar, the log pane toggle, the step's progress (announced to
/// screen readers), where the step is and the step controls (shown here by its controller), and
/// Terminate, above the selected element's panes.
public class SteppingScreen extends Component implements RequiresResize, ProvidesResize {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    PipelinePathBar pipelinePathBar;
    @UiField
    FlowPanel right;
    @UiField
    ToolbarToggleButton toggleLogPane;
    @UiField
    SimplePanel stepMessage;
    @UiField
    DivElement stepAnnouncement;
    @UiField
    ToolbarButton terminate;
    @UiField
    LayerContainerImpl layerContainer;

    private Consumer<Boolean> onToggleLogPane = on -> {
    };
    private Runnable onTerminate = () -> {
    };

    /// Makes the screen, with the log pane on and Terminate disabled.
    public SteppingScreen() {
        initWidget(BINDER.createAndBindUi(this));
        toggleLogPane.setOnToggle(on -> onToggleLogPane.accept(on));
        toggleLogPane.setOn();
        terminate.setOnClick(() -> onTerminate.run());
    }

    @Override
    public void onResize() {
        layerContainer.onResize();
    }

    /// @return The pipeline's bar, for the controller to attach to and fill.
    public PipelinePathBar getPipelinePathBar() {
        return pipelinePathBar;
    }

    /// Puts where the step is and the step controls between the step's progress and Terminate.
    ///
    /// @param stepLocationLink Where the step is.
    /// @param stepControl      The step controls.
    public void setStepControls(final IsWidget stepLocationLink, final IsWidget stepControl) {
        right.insert(stepLocationLink, 1);
        right.insert(stepControl, 2);
    }

    /// @param content The selected element's panes.
    public void showContent(final Layer content) {
        layerContainer.show(content);
    }

    /// Shows the step's progress.
    ///
    /// @param message The progress, or blank for none.
    public void setStepMessage(final String message) {
        stepMessage.getElement().setInnerText(message);
        stepMessage.setTitle(message);
    }

    /// @param visible Whether the step's progress shows.
    public void setStepMessageVisible(final boolean visible) {
        stepMessage.setVisible(visible);
    }

    /// Tells screen reader users of a step, once the screen has caught up with it.
    ///
    /// @param message What to announce.
    public void announce(final String message) {
        stepAnnouncement.setInnerText("");
        Scheduler.get().scheduleDeferred(() -> stepAnnouncement.setInnerText(message));
    }

    /// @param enabled Whether Terminate can be pressed.
    public void setTerminateEnabled(final boolean enabled) {
        terminate.setEnabled(enabled);
    }

    /// Shows the log pane toggle's state and name.
    ///
    /// @param visible Whether the toggle shows.
    /// @param on      Whether the log pane is on.
    /// @param title   The toggle's name, e.g. with the count of its messages.
    public void setLogPaneToggle(final boolean visible, final boolean on, final String title) {
        if (title != null) {
            toggleLogPane.setTitle(title);
        }
        toggleLogPane.setVisible(visible);
        if (visible) {
            toggleLogPane.setState(on);
        }
    }

    /// @param onToggleLogPane Given whether the user turned the log pane on.
    public void setOnToggleLogPane(final Consumer<Boolean> onToggleLogPane) {
        this.onToggleLogPane = onToggleLogPane;
    }

    /// @param onTerminate What to run when Terminate is pressed.
    public void setOnTerminate(final Runnable onTerminate) {
        this.onTerminate = onTerminate;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, SteppingScreen> {

    }
}
