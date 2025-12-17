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

package stroom.annotation.client;

import stroom.annotation.client.DurationPresenter.DurationView;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;
import stroom.widget.customdatebox.client.DurationPicker;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

public class DurationViewImpl extends ViewImpl implements DurationView {

    private final Widget widget;

    @UiField
    CustomCheckBox forever;
    @UiField
    DurationPicker durationPicker;

    @Inject
    DurationViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public void setDuration(final SimpleDuration duration) {
        forever.setValue(duration == null);
        durationPicker.setEnabled(!forever.getValue());
        durationPicker.setValue(duration != null
                ? duration
                : SimpleDuration.builder().time(5).timeUnit(TimeUnit.YEARS).build());
    }

    @Override
    public SimpleDuration getDuration() {
        if (forever.getValue()) {
            return null;
        }
        return durationPicker.getValue();
    }

    @Override
    public void focus() {
        forever.focus();
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @UiHandler("forever")
    public void onForever(final ValueChangeEvent<Boolean> event) {
        durationPicker.setEnabled(!forever.getValue());
    }

    public interface Binder extends UiBinder<Widget, DurationViewImpl> {

    }
}
