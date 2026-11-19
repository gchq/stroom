/*
 * Copyright 2018 Crown Copyright
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

package stroom.analytics.client.component;

import stroom.widget.button.client.Button;
import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Consumer;

/// The Run Now dialog's content: runs the execution schedules selected, or all those filtered.
public class ExecutionScheduleRunNow extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    Button applySelectionButton;
    @UiField
    Button applyFilteredButton;

    private Consumer<RunNowAction> onAction = action -> {
    };

    /// Makes the content.
    public ExecutionScheduleRunNow() {
        initWidget(BINDER.createAndBindUi(this));
        applySelectionButton.setOnClick(() -> onAction.accept(RunNowAction.APPLY_TO_SELECTION));
        applyFilteredButton.setOnClick(() -> onAction.accept(RunNowAction.APPLY_TO_FILTERED));
    }

    /// Puts the focus on Apply to Selection.
    public void focus() {
        applySelectionButton.setFocus(true);
    }

    /// @param onAction Told which schedules the user runs.
    public void setOnAction(final Consumer<RunNowAction> onAction) {
        this.onAction = onAction;
    }


    // --------------------------------------------------------------------------------


    /// Which schedules to run.
    public enum RunNowAction {
        /// The schedules selected.
        APPLY_TO_SELECTION,
        /// Every schedule the list's filter matches.
        APPLY_TO_FILTERED
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ExecutionScheduleRunNow> {

    }
}
