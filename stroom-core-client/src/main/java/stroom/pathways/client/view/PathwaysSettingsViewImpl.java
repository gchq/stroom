/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.pathways.client.view;

import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.pathways.client.presenter.PathwaysSettingsPresenter.PathwaysSettingsView;
import stroom.pathways.client.presenter.PathwaysSettingsUiHandlers;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;
import stroom.widget.customdatebox.client.DurationPicker;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class PathwaysSettingsViewImpl
        extends ViewWithUiHandlers<PathwaysSettingsUiHandlers>
        implements PathwaysSettingsView, ReadOnlyChangeHandler {

    private final Widget widget;

    @UiField
    SimplePanel traceStore;
    @UiField
    SimplePanel infoFeed;
    @UiField
    DurationPicker temporalOrderingTolerance;
    @UiField
    CustomCheckBox allowPathwayCreation;
    @UiField
    CustomCheckBox allowPathwayMutation;
    @UiField
    CustomCheckBox allowConstraintCreation;
    @UiField
    CustomCheckBox allowConstraintMutation;
    @UiField
    TextBox processingNode;

    @Inject
    public PathwaysSettingsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        temporalOrderingTolerance.smallTimeMode();
        temporalOrderingTolerance.setValue(new SimpleDuration(0, TimeUnit.NANOSECONDS));
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setTraceStoreView(final View view) {
        this.traceStore.setWidget(view.asWidget());
    }

    @Override
    public void setInfoFeedView(final View view) {
        this.infoFeed.setWidget(view.asWidget());
    }

    @Override
    public SimpleDuration getTemporalOrderingTolerance() {
        return temporalOrderingTolerance.getValue();
    }

    @Override
    public void setTemporalOrderingTolerance(final SimpleDuration temporalOrderingTolerance) {
        if (temporalOrderingTolerance == null) {
            this.temporalOrderingTolerance.setValue(new SimpleDuration(0, TimeUnit.NANOSECONDS));
        } else {
            this.temporalOrderingTolerance.setValue(temporalOrderingTolerance);
        }
    }

    @Override
    public boolean isAllowPathwayCreation() {
        return allowPathwayCreation.getValue();
    }

    @Override
    public void setAllowPathwayCreation(final boolean allowPathwayCreation) {
        this.allowPathwayCreation.setValue(allowPathwayCreation);
    }

    @Override
    public boolean isAllowPathwayMutation() {
        return allowPathwayMutation.getValue();
    }

    @Override
    public void setAllowPathwayMutation(final boolean allowPathwayMutation) {
        this.allowPathwayMutation.setValue(allowPathwayMutation);
    }

    @Override
    public boolean isAllowConstraintCreation() {
        return allowConstraintCreation.getValue();
    }

    @Override
    public void setAllowConstraintCreation(final boolean allowConstraintCreation) {
        this.allowConstraintCreation.setValue(allowConstraintCreation);
    }

    @Override
    public boolean isAllowConstraintMutation() {
        return allowConstraintMutation.getValue();
    }

    @Override
    public void setAllowConstraintMutation(final boolean allowConstraintMutation) {
        this.allowConstraintMutation.setValue(allowConstraintMutation);
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        temporalOrderingTolerance.setEnabled(!readOnly);
    }

    @Override
    public String getProcessingNode() {
        return processingNode.getValue();
    }

    @Override
    public void setProcessingNode(final String processingNode) {
        this.processingNode.setValue(processingNode);
    }

    @UiHandler("temporalOrderingTolerance")
    public void onTemporalOrderingTolerance(final ValueChangeEvent<SimpleDuration> e) {
        fireChange();
    }

    @UiHandler("allowPathwayCreation")
    public void onAllowPathwayCreation(final ValueChangeEvent<Boolean> e) {
        fireChange();
    }

    @UiHandler("allowPathwayMutation")
    public void onAllowPathwayMutation(final ValueChangeEvent<Boolean> e) {
        fireChange();
    }

    @UiHandler("allowConstraintCreation")
    public void onAllowConstraintCreation(final ValueChangeEvent<Boolean> e) {
        fireChange();
    }

    @UiHandler("allowConstraintMutation")
    public void onAllowConstraintMutation(final ValueChangeEvent<Boolean> e) {
        fireChange();
    }

    @UiHandler("processingNode")
    public void onProcessingNode(final ValueChangeEvent<String> e) {
        fireChange();
    }

    private void fireChange() {
        if (getUiHandlers() != null) {
            getUiHandlers().onChange();
        }
    }

    public interface Binder extends UiBinder<Widget, PathwaysSettingsViewImpl> {

    }
}
