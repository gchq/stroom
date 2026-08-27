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

package stroom.processor.client.view;

import stroom.item.client.SelectionBox;
import stroom.preferences.client.UserPreferencesManager;
import stroom.processor.client.presenter.ProcessorEditPresenter.ProcessorEditView;
import stroom.processor.client.presenter.ProcessorEditUiHandlers;
import stroom.processor.shared.ProcessorFilter;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;
import stroom.widget.button.client.Button;
import stroom.widget.customdatebox.client.DurationPicker;
import stroom.widget.customdatebox.client.MyDateBox;
import stroom.widget.tickbox.client.view.CustomCheckBox;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.Objects;

public class ProcessorEditViewImpl
        extends ViewWithUiHandlers<ProcessorEditUiHandlers>
        implements ProcessorEditView {

    private static final SimpleDuration DEFAULT_MAX_TASK_CREATION_DELAY =
            new SimpleDuration(30, TimeUnit.SECONDS);

    private final Widget widget;

    @UiField
    SimplePanel expression;
    @UiField
    MyDateBox minMetaCreateTimeMs;
    @UiField
    MyDateBox maxMetaCreateTimeMs;
    @UiField
    ValueSpinner maxProcessingTasks;
    @UiField
    SelectionBox<String> profile;
    @UiField
    CustomCheckBox maxTaskCreationDelayEnabled;
    @UiField
    DurationPicker maxTaskCreationDelay;
    @UiField
    CustomCheckBox export;
    @UiField
    SimplePanel runAsUser;
    @UiField
    Button editFeedDependencies;

    @Inject
    public ProcessorEditViewImpl(final ProcessorEditViewImpl.Binder binder,
                                 final UserPreferencesManager userPreferencesManager) {
        widget = binder.createAndBindUi(this);
        minMetaCreateTimeMs.setUtc(userPreferencesManager.isUtc());
        maxMetaCreateTimeMs.setUtc(userPreferencesManager.isUtc());
        // Zero means unlimited so must remain reachable.
        maxProcessingTasks.setMin(ProcessorFilter.MIN_MAX_PROCESSING_TASKS);
        maxProcessingTasks.setMax(ProcessorFilter.MAX_MAX_PROCESSING_TASKS);
        // Sub second delays make no sense here as task creation only runs periodically anyway.
        maxTaskCreationDelay.setValue(DEFAULT_MAX_TASK_CREATION_DELAY);
        updateEnabled();
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setExpressionView(final View view) {
        expression.setWidget(view.asWidget());
    }

    @Override
    public Long getMinMetaCreateTimeMs() {
        return minMetaCreateTimeMs.getMilliseconds();
    }

    @Override
    public void setMinMetaCreateTimeMs(final Long minMetaCreateTimeMs) {
        this.minMetaCreateTimeMs.setMilliseconds(minMetaCreateTimeMs);
    }

    @Override
    public Long getMaxMetaCreateTimeMs() {
        return maxMetaCreateTimeMs.getMilliseconds();
    }

    @Override
    public void setMaxMetaCreateTimeMs(final Long maxMetaCreateTimeMs) {
        this.maxMetaCreateTimeMs.setMilliseconds(maxMetaCreateTimeMs);
    }

    @Override
    public Integer getMaxProcessingTasks() {
        return maxProcessingTasks.getIntValue();
    }

    @Override
    public void setMaxProcessingTasks(final Integer maxProcessingTasks) {
        // Show the unlimited value rather than leaving the spinner blank if we have no value.
        this.maxProcessingTasks.setValue(Objects.requireNonNullElse(
                maxProcessingTasks,
                ProcessorFilter.DEFAULT_MAX_PROCESSING_TASKS));
    }

    @Override
    public SelectionBox<String> getProfile() {
        return profile;
    }

    @Override
    public SimpleDuration getMaxTaskCreationDelay() {
        if (maxTaskCreationDelayEnabled.getValue()) {
            return maxTaskCreationDelay.getValue();
        }
        return null;
    }

    @Override
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

    @UiHandler("maxTaskCreationDelayEnabled")
    public void onMaxTaskCreationDelayEnabledChange(final ValueChangeEvent<Boolean> event) {
        updateEnabled();
    }

    @Override
    public boolean isExport() {
        return this.export.getValue();
    }

    @Override
    public void setExport(final boolean export) {
        this.export.setValue(export);
    }

    @Override
    public void setRunAsUserView(final View view) {
        this.runAsUser.setWidget(view.asWidget());
    }

    @UiHandler("editFeedDependencies")
    public void onEditFeedDependencies(final ClickEvent e) {
        if (getUiHandlers() != null) {
            getUiHandlers().onEditFeedDependencies();
        }
    }

    public interface Binder extends UiBinder<Widget, ProcessorEditViewImpl> {

    }
}
