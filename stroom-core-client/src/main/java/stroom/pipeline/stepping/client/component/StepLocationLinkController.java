/*
 * Copyright 2026 Crown Copyright
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
import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.Objects;
import java.util.function.Consumer;

/// Shows the stepping screen's location in its [StepLocationLink], and opens the "Set Location"
/// dialog when it is pressed, once a stream has been chosen to step through.
public class StepLocationLinkController extends Controller {

    private final Provider<StepLocationPickerController> stepLocationPickerProvider;
    private StepLocationLink view;
    private StepLocation stepLocation;
    private boolean enabled = true;
    private Consumer<StepLocation> onStepLocation = stepLocation -> {
    };

    /// Made by GIN, with what makes its dialog; [#attach(StepLocationLink)] gives it its link.
    ///
    /// @param stepLocationPickerProvider Makes the step location picker.
    @Inject
    public StepLocationLinkController(final Provider<StepLocationPickerController> stepLocationPickerProvider) {
        this.stepLocationPickerProvider = stepLocationPickerProvider;
    }

    /// Attaches this to a link, e.g. one a template declares, showing the start of the first
    /// stream. The link is then this controller's widget.
    ///
    /// @param view The link.
    /// @return This, for its owner to keep.
    public StepLocationLinkController attach(final StepLocationLink view) {
        this.view = Objects.requireNonNull(view, "view");
        initWidget(view);
        setStepLocation(new StepLocation(0L, 0L, 0L));
        view.setOnClick(() -> {
            if (enabled) {
                stepLocationPickerProvider.get().edit(this, stepLocation, location -> onStepLocation.accept(location));
            }
        });
        return this;
    }

    /// Sets whether the location can be clicked to set a new one. It is enabled once a stream has
    /// been chosen to step through.
    ///
    /// @param enabled True to let the location be set.
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
        view.setEnabled(enabled);
    }

    /// @param stepLocation The location to show.
    public void setStepLocation(final StepLocation stepLocation) {
        this.stepLocation = stepLocation;
        view.setLocation(text(stepLocation), ariaLabel(stepLocation));
    }

    /// @param onStepLocation What to run with the location the user asks to step to.
    public void setOnStepLocation(final Consumer<StepLocation> onStepLocation) {
        this.onStepLocation = onStepLocation;
    }

    /// @param stepLocation A location, its part and record zero based.
    /// @return It as a code, e.g. `[123:1:4]`, its part and record one based, or `[??:??:??]` for
    ///         none (all zero).
    static String text(final StepLocation stepLocation) {
        if (isNone(stepLocation)) {
            return "[??:??:??]";
        }
        return "["
               + stepLocation.getMetaId() + ":"
               + (stepLocation.getPartIndex() + 1) + ":"
               + (stepLocation.getRecordIndex() + 1)
               + "]";
    }

    /// @param stepLocation A location, its part and record zero based.
    /// @return What the link is named: what pressing it does, its code, and what the code means.
    static String ariaLabel(final StepLocation stepLocation) {
        if (isNone(stepLocation)) {
            return "Set Location ([??:??:??], none chosen)";
        }
        return "Set Location (" + text(stepLocation)
               + ", stream " + stepLocation.getMetaId()
               + ", part " + (stepLocation.getPartIndex() + 1)
               + ", record " + (stepLocation.getRecordIndex() + 1) + ")";
    }

    private static boolean isNone(final StepLocation stepLocation) {
        return stepLocation.getMetaId() == 0
               && stepLocation.getPartIndex() == 0
               && stepLocation.getRecordIndex() == 0;
    }
}
