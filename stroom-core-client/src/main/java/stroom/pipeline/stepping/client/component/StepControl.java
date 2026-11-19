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

import stroom.pipeline.shared.stepping.StepType;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Consumer;

/// The stepping screen's step buttons: first, backward, forward, last and refresh, and the step
/// filters.
public class StepControl extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    ToolbarButton filterButton;
    @UiField
    ToolbarButton firstButton;
    @UiField
    ToolbarButton backwardButton;
    @UiField
    ToolbarButton forwardButton;
    @UiField
    ToolbarButton lastButton;
    @UiField
    ToolbarButton refreshButton;

    private Consumer<StepType> onStep = stepType -> {
    };
    private Runnable onFilter = () -> {
    };

    /// Makes the buttons, every step disabled.
    public StepControl() {
        initWidget(BINDER.createAndBindUi(this));
        filterButton.setOnClick(() -> onFilter.run());
        firstButton.setOnClick(() -> onStep.accept(StepType.FIRST));
        backwardButton.setOnClick(() -> onStep.accept(StepType.BACKWARD));
        forwardButton.setOnClick(() -> onStep.accept(StepType.FORWARD));
        lastButton.setOnClick(() -> onStep.accept(StepType.LAST));
        refreshButton.setOnClick(() -> onStep.accept(StepType.REFRESH));
    }

    /// @param stepType A step.
    /// @param enabled  Whether its button can be pressed.
    public void setStepEnabled(final StepType stepType, final boolean enabled) {
        button(stepType).setEnabled(enabled);
    }

    /// @param stepType A step.
    /// @return Whether its button can be pressed.
    public boolean isStepEnabled(final StepType stepType) {
        return button(stepType).isEnabled();
    }

    /// @param onStep Told the step the user presses.
    public void setOnStep(final Consumer<StepType> onStep) {
        this.onStep = onStep;
    }

    /// @param onFilter What Manage Step Filters does.
    public void setOnFilter(final Runnable onFilter) {
        this.onFilter = onFilter;
    }

    private ToolbarButton button(final StepType stepType) {
        //noinspection EnhancedSwitchMigration // cos GWT
        switch (stepType) {
            case FIRST:
                return firstButton;
            case BACKWARD:
                return backwardButton;
            case FORWARD:
                return forwardButton;
            case LAST:
                return lastButton;
            case REFRESH:
                return refreshButton;
            default:
                throw new IllegalArgumentException("Unknown type " + stepType);
        }
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, StepControl> {

    }
}
