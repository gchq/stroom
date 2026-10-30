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

import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.StepType;
import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.function.Consumer;

/// The stepping screen's step buttons ([StepControl]): which steps can be taken, given where
/// stepping has got to.
public class StepControlController extends Controller {

    private StepControl view;
    private StepLocation endLocation = null;

    private Consumer<StepType> onStep = stepType -> {
    };

    /// Made by GIN; [#attach(StepControl)] gives it its buttons.
    ///
    /// @param eventBus The application's event bus.
    @Inject
    public StepControlController(final EventBus eventBus) {
        super(eventBus);
    }

    /// Attaches this to the buttons, e.g. ones a template declares. They are then this
    /// controller's widget.
    ///
    /// @param view The buttons.
    /// @return This, for its owner to keep.
    public StepControlController attach(final StepControl view) {
        this.view = Objects.requireNonNull(view, "view");
        initWidget(view);
        view.setOnStep(stepType -> onStep.accept(stepType));
        return this;
    }

    /// Takes a step as if its button were pressed, e.g. from a keyboard shortcut.
    ///
    /// @param stepType The step.
    public void step(final StepType stepType) {
        onStep.accept(stepType);
    }

    /// @param stepType A step.
    /// @return Whether it can be taken now.
    public boolean isEnabled(final StepType stepType) {
        return view.isStepEnabled(stepType);
    }

    /// Disables every step button, as there is nothing to step until a stream has been chosen.
    public void initButtons() {
        for (final StepType stepType : StepType.values()) {
            view.setStepEnabled(stepType, false);
        }
    }

    /// Sets whether the refresh button is enabled. It is enabled once a stream has been chosen to
    /// step through.
    ///
    /// @param enabled True to enable the refresh button.
    public void setRefreshEnabled(final boolean enabled) {
        view.setStepEnabled(StepType.REFRESH, enabled);
    }

    /// Enables the buttons for where a step has got to: back unless at the first record, forward
    /// unless at the last (a step forward that finds nothing marks the end, when not filtered).
    ///
    /// @param stepType      The step taken.
    /// @param foundRecord   Whether the step found a record.
    /// @param isFiltered    Whether a filter is in place, which makes the last record unknown.
    /// @param stepLocation  Where the step got to, or null.
    /// @param isFirstStream Whether the stream is the first.
    /// @param isLastStream  Whether the stream is the last.
    public void setEnabledButtons(final StepType stepType,
                                  final boolean foundRecord,
                                  final boolean isFiltered,
                                  final StepLocation stepLocation,
                                  final boolean isFirstStream,
                                  final boolean isLastStream) {

        // See if we are on the first record
        final boolean isFirstRecord = isFirstStream && stepLocation != null
                && stepLocation.getPartIndex() == 0
                && stepLocation.getRecordIndex() == 0;
        // If a filter is in place the end record is a variable concept
        final boolean isLastRecord = isLastStream && !isFiltered
                && stepLocation != null
                && endLocation != null
                && stepLocation.getPartIndex() == endLocation.getPartIndex()
                && stepLocation.getRecordIndex() == endLocation.getRecordIndex();
        final boolean canStepFurtherBack = foundRecord && !isFirstRecord;
        final boolean canStepFurtherForward = foundRecord && !isLastRecord;

        if (stepType == StepType.FIRST) {
            setEnabled(false, false, true, true);
        } else if (stepType == StepType.BACKWARD) {
            setEnabled(canStepFurtherBack, canStepFurtherBack, true, true);
        } else if (stepType == StepType.FORWARD) {
            markEndLocation(foundRecord, isFiltered, stepLocation);
            setEnabled(true, true, canStepFurtherForward, canStepFurtherForward);
        } else if (stepType == StepType.LAST) {
            markEndLocation(foundRecord, isFiltered, stepLocation);
            setEnabled(true, true, false, false);
        } else if (stepType == StepType.REFRESH) {
            setEnabled(canStepFurtherBack, canStepFurtherBack, canStepFurtherForward, canStepFurtherForward);
        }
    }

    private void markEndLocation(final boolean foundRecord,
                                 final boolean isFiltered,
                                 final StepLocation stepLocation) {
        // Record the end location for future now that we know it.
        if (endLocation == null && !isFiltered && !foundRecord) {
            endLocation = stepLocation;
        }
    }

    /// @param onStep What to run with the step the user asks for.
    public void setOnStep(final Consumer<StepType> onStep) {
        this.onStep = onStep;
    }

    /// @param onFilter What to run when the user asks to change the step filters.
    public void setOnFilter(final Runnable onFilter) {
        view.setOnFilter(onFilter);
    }

    private void setEnabled(final boolean first, final boolean backward, final boolean forward, final boolean last) {
        view.setStepEnabled(StepType.FIRST, first);
        view.setStepEnabled(StepType.BACKWARD, backward);
        view.setStepEnabled(StepType.FORWARD, forward);
        view.setStepEnabled(StepType.LAST, last);
    }
}
