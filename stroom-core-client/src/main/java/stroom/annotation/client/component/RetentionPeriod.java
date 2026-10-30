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

package stroom.annotation.client.component;

import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;
import stroom.widget.component.client.Component;
import stroom.widget.duration.client.DurationPicker;
import stroom.widget.tickbox.client.TickBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// The Set Retention Period dialog's content: how long an annotation is kept, or for ever.
public class RetentionPeriod extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    TickBox forever;
    @UiField
    DurationPicker durationPicker;

    /// Makes the content.
    public RetentionPeriod() {
        initWidget(BINDER.createAndBindUi(this));
        forever.setOnChange(value -> onForever());
    }

    /// @param duration The period, or null to keep the annotation for ever (5 years is offered).
    public void setDuration(final SimpleDuration duration) {
        forever.setValue(duration == null);
        durationPicker.setEnabled(!forever.getValue());
        durationPicker.setValue(duration != null
                ? duration
                : SimpleDuration.builder().time(5).timeUnit(TimeUnit.YEARS).build());
    }

    /// @return The period chosen, or null for ever.
    public SimpleDuration getDuration() {
        if (forever.getValue()) {
            return null;
        }
        return durationPicker.getValue();
    }

    /// Puts the focus on Keep Forever.
    public void focus() {
        forever.focus();
    }

    private void onForever() {
        durationPicker.setEnabled(!forever.getValue());
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, RetentionPeriod> {

    }
}
