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

package stroom.analytics.client.view;

import stroom.analytics.client.presenter.AnalyticRuleProcessingPresenter.AlertRuleProcessingView;
import stroom.document.client.event.DirtyUiHandlers;
import stroom.util.shared.time.SimpleDuration;
import stroom.widget.customdatebox.client.DurationPicker;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class AnalyticRuleProcessingViewImpl
        extends ViewWithUiHandlers<DirtyUiHandlers>
        implements AlertRuleProcessingView {

    private final Widget widget;

    @UiField
    CustomCheckBox enabled;
    @UiField
    DurationPicker timeToWaitForData;

    @Inject
    public AnalyticRuleProcessingViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public boolean isEnabled() {
        return this.enabled.getValue();
    }

    @Override
    public void setEnabled(final boolean enabled) {
        this.enabled.setValue(enabled);
    }


    @Override
    public SimpleDuration getTimeToWaitForData() {
        return timeToWaitForData.getValue();
    }

    @Override
    public void setTimeToWaitForData(final SimpleDuration timeToWaitForData) {
        this.timeToWaitForData.setValue(timeToWaitForData);
    }

    @UiHandler("enabled")
    public void onEnabled(final ValueChangeEvent<Boolean> event) {
        getUiHandlers().onDirty();
    }

    @UiHandler("timeToWaitForData")
    public void onTimeToWaitForData(final ValueChangeEvent<SimpleDuration> event) {
        getUiHandlers().onDirty();
    }

    public interface Binder extends UiBinder<Widget, AnalyticRuleProcessingViewImpl> {

    }
}
