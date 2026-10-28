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

import stroom.item.client.SelectionBox;
import stroom.item.client.SelectionListModel;
import stroom.item.client.SimpleSelectionItemWrapper;
import stroom.processor.shared.ProcessorFilter;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;
import stroom.widget.button.client.Button;
import stroom.widget.component.client.Component;
import stroom.widget.customdatebox.client.DurationPicker;
import stroom.widget.datepicker.client.DateTimeBox;
import stroom.widget.dropdowntree.client.DropDownBox;
import stroom.widget.tickbox.client.TickBox;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.Objects;

/// The Add Filter and Edit Filter dialog's content: the expression that selects a processor
/// filter's streams (shown here by its controller), the create time bounds, how many tasks may
/// run, its processing profile, its task creation delay, whether it is exported, who it runs as (a
/// picker its controller attaches) and a button to set its feed dependencies.
public class ProcessorEdit extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    private static final SimpleDuration DEFAULT_MAX_TASK_CREATION_DELAY =
            new SimpleDuration(30L, TimeUnit.SECONDS);

    @UiField
    SimplePanel expression;
    @UiField
    DateTimeBox minMetaCreateTimeMs;
    @UiField
    DateTimeBox maxMetaCreateTimeMs;
    @UiField
    ValueSpinner maxProcessingTasks;
    @UiField
    SelectionBox<String> profile;
    @UiField
    TickBox maxTaskCreationDelayEnabled;
    @UiField
    DurationPicker maxTaskCreationDelay;
    @UiField
    TickBox export;
    @UiField
    DropDownBox runAsUser;
    @UiField
    Button editFeedDependencies;

    /// Makes the content, with no expression.
    public ProcessorEdit() {
        initWidget(BINDER.createAndBindUi(this));
        // Zero means unlimited so must remain reachable.
        maxProcessingTasks.setMin(ProcessorFilter.MIN_MAX_PROCESSING_TASKS);
        maxProcessingTasks.setMax(ProcessorFilter.MAX_MAX_PROCESSING_TASKS);
        // Sub second delays make no sense here as task creation only runs periodically anyway.
        maxTaskCreationDelay.setValue(DEFAULT_MAX_TASK_CREATION_DELAY);
        updateEnabled();
        maxTaskCreationDelayEnabled.addValueChangeHandler(e -> updateEnabled());
    }

    /// @param view The expression that selects the filter's streams.
    public void setExpressionView(final IsWidget view) {
        expression.setWidget(view);
    }

    /// @return The earliest create time of the streams processed, or `null` for none.
    public Long getMinMetaCreateTimeMs() {
        return minMetaCreateTimeMs.getValue();
    }

    /// @param minMetaCreateTimeMs The earliest create time of the streams processed, or `null` for
    ///                            none.
    public void setMinMetaCreateTimeMs(final Long minMetaCreateTimeMs) {
        this.minMetaCreateTimeMs.setValue(minMetaCreateTimeMs);
    }

    /// @return The latest create time of the streams processed, or `null` for none.
    public Long getMaxMetaCreateTimeMs() {
        return maxMetaCreateTimeMs.getValue();
    }

    /// @param maxMetaCreateTimeMs The latest create time of the streams processed, or `null` for
    ///                            none.
    public void setMaxMetaCreateTimeMs(final Long maxMetaCreateTimeMs) {
        this.maxMetaCreateTimeMs.setValue(maxMetaCreateTimeMs);
    }

    /// @return The minimum stream create time box, for a controller to open its dialog.
    public DateTimeBox getMinMetaCreateTimeBox() {
        return minMetaCreateTimeMs;
    }

    /// @return The maximum stream create time box, for a controller to open its dialog.
    public DateTimeBox getMaxMetaCreateTimeBox() {
        return maxMetaCreateTimeMs;
    }

    /// @return The most tasks that may run at once, 0 for no limit.
    public Integer getMaxProcessingTasks() {
        return maxProcessingTasks.getIntValue();
    }

    /// @param maxProcessingTasks The most tasks that may run at once, or `null` for the default (no
    ///                           limit).
    public void setMaxProcessingTasks(final Integer maxProcessingTasks) {
        // Show the unlimited value rather than leaving the spinner blank if we have no value.
        this.maxProcessingTasks.setValue(Objects.requireNonNullElse(
                maxProcessingTasks,
                ProcessorFilter.DEFAULT_MAX_PROCESSING_TASKS));
    }

    /// @param model The processing profiles that can be chosen.
    public void setProfileModel(final SelectionListModel<String, SimpleSelectionItemWrapper<String>> model) {
        profile.setModel(model);
    }

    /// @return The name of the processing profile chosen, or `null` for none.
    public String getProfile() {
        return profile.getValue();
    }

    /// @param profileName The name of the processing profile, or `null` for none.
    public void setProfile(final String profileName) {
        profile.setValue(profileName);
    }

    /// @return The longest wait between checks for new data, or `null` for the cluster's limit.
    public SimpleDuration getMaxTaskCreationDelay() {
        if (maxTaskCreationDelayEnabled.getValue()) {
            return maxTaskCreationDelay.getValue();
        }
        return null;
    }

    /// @param maxTaskCreationDelay The longest wait between checks for new data, or `null` for the
    ///                             cluster's limit (the box unticked, showing 30 seconds).
    public void setMaxTaskCreationDelay(final SimpleDuration maxTaskCreationDelay) {
        // A null delay means this filter just uses the cluster wide limit.
        this.maxTaskCreationDelayEnabled.setValue(maxTaskCreationDelay != null);
        this.maxTaskCreationDelay.setValue(Objects.requireNonNullElse(
                maxTaskCreationDelay,
                DEFAULT_MAX_TASK_CREATION_DELAY));
        updateEnabled();
    }

    private void updateEnabled() {
        maxTaskCreationDelay.setEnabled(maxTaskCreationDelayEnabled.getValue());
    }

    /// @return Whether the filter is exported with its pipeline.
    public boolean isExport() {
        return this.export.getValue();
    }

    /// @param export Whether the filter is exported with its pipeline.
    public void setExport(final boolean export) {
        this.export.setValue(export);
    }

    /// @return The box its controller attaches the Run As User picker to.
    public DropDownBox getRunAsUserBox() {
        return runAsUser;
    }

    /// @param onEditFeedDependencies Run when Set Feed Dependencies is pressed.
    public void setOnEditFeedDependencies(final Runnable onEditFeedDependencies) {
        editFeedDependencies.setOnClick(onEditFeedDependencies);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ProcessorEdit> {

    }
}
