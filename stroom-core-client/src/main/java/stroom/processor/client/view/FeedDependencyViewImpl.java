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

package stroom.processor.client.view;

import stroom.processor.client.presenter.FeedDependencyPresenter.FeedDependencyView;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;
import stroom.widget.customdatebox.client.DurationPicker;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;

public class FeedDependencyViewImpl extends ViewImpl implements FeedDependencyView {

    private final Widget widget;

    @UiField
    SimplePanel feedDependencies;
    @UiField
    CustomCheckBox minProcessingDelayEnabled;
    @UiField
    DurationPicker minProcessingDelay;
    @UiField
    CustomCheckBox maxProcessingDelayEnabled;
    @UiField
    DurationPicker maxProcessingDelay;

    @Inject
    public FeedDependencyViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setFeedDependencyList(final View view) {
        feedDependencies.setWidget(view.asWidget());
    }

    @Override
    public void setMinProcessingDelay(final SimpleDuration minProcessingDelay) {
        if (minProcessingDelay == null) {
            this.minProcessingDelayEnabled.setValue(false);
            this.minProcessingDelay.setValue(new SimpleDuration(30, TimeUnit.MINUTES));
        } else {
            this.minProcessingDelayEnabled.setValue(true);
            this.minProcessingDelay.setValue(minProcessingDelay);
        }
        updateEnabled();
    }

    @Override
    public SimpleDuration getMinProcessingDelay() {
        if (minProcessingDelayEnabled.getValue()) {
            return minProcessingDelay.getValue();
        }
        return null;
    }

    @Override
    public void setMaxProcessingDelay(final SimpleDuration maxProcessingDelay) {
        if (maxProcessingDelay == null) {
            this.maxProcessingDelayEnabled.setValue(false);
            this.maxProcessingDelay.setValue(new SimpleDuration(1, TimeUnit.DAYS));
        } else {
            this.maxProcessingDelayEnabled.setValue(true);
            this.maxProcessingDelay.setValue(maxProcessingDelay);
        }
        updateEnabled();
    }

    @Override
    public SimpleDuration getMaxProcessingDelay() {
        if (maxProcessingDelayEnabled.getValue()) {
            return maxProcessingDelay.getValue();
        }
        return null;
    }

    private void updateEnabled() {
        minProcessingDelay.setEnabled(minProcessingDelayEnabled.getValue());
        maxProcessingDelay.setEnabled(maxProcessingDelayEnabled.getValue());
    }

    @UiHandler({"minProcessingDelayEnabled", "maxProcessingDelayEnabled"})
    public void onChange(final ValueChangeEvent<Boolean> event) {
        updateEnabled();
    }

    public interface Binder extends UiBinder<Widget, FeedDependencyViewImpl> {

    }
}
