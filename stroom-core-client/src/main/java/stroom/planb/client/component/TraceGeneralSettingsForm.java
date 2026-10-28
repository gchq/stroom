/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.planb.client.component;

import stroom.planb.shared.TraceSettings;
import stroom.util.shared.ModelStringUtil;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.TextBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/**
 * General settings that only apply to a Traces store, shown alongside
 * {@link GeneralSettingsForm} so that widget stays store-type agnostic.
 */
public class TraceGeneralSettingsForm extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    private Runnable onChange = () -> {
    };

    @UiField
    TextBox maxSpansPerTrace;

    // The number last set, kept while the text entered isn't a number
    private Long maxSpansPerTraceSet = TraceSettings.DEFAULT_MAX_SPANS_PER_TRACE;

    /// Makes the settings, at their defaults.
    public TraceGeneralSettingsForm() {
        initWidget(BINDER.createAndBindUi(this));
        maxSpansPerTrace.addValueChangeHandler(this::onMaxSpansPerTrace);
        maxSpansPerTrace.setInvalidReason(text -> text.trim().isEmpty() || parseNumber(text.trim()) != null
                ? null
                : "Must be a number, e.g. 100000");
    }

    /// @return The number entered: the default if the box is blank or, while its text isn't a number
    /// (when Save refuses, so it is never saved), the number last set. Before, such text was
    /// replaced by the default, 100,000, and saved.
    public Long getMaxSpansPerTrace() {
        final String text = maxSpansPerTrace.getValue().trim();
        if (text.isEmpty()) {
            return TraceSettings.DEFAULT_MAX_SPANS_PER_TRACE;
        }
        final Long number = parseNumber(text);
        return number == null
                ? maxSpansPerTraceSet
                : number;
    }

    // A number, e.g. 100000, or null if the text isn't one
    private static Long parseNumber(final String text) {
        try {
            return ModelStringUtil.parseNumberString(text);
        } catch (final RuntimeException e) {
            return null;
        }
    }

    /// @param maxSpansPerTrace The number to show, or null for the default.
    public void setMaxSpansPerTrace(final Long maxSpansPerTrace) {
        maxSpansPerTraceSet = maxSpansPerTrace == null
                ? TraceSettings.DEFAULT_MAX_SPANS_PER_TRACE
                : maxSpansPerTrace;
        this.maxSpansPerTrace.setValue(String.valueOf(maxSpansPerTrace == null
                ? TraceSettings.DEFAULT_MAX_SPANS_PER_TRACE
                : maxSpansPerTrace));
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        maxSpansPerTrace.setReadOnly(readOnly);
    }

    private void onMaxSpansPerTrace(final ValueChangeEvent<String> event) {
        changed();
    }

    /// Sets what is told when the user changes a setting.
    ///
    /// @param onChange Run each time, or null for nothing.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange != null
                ? onChange
                : () -> {
                };
    }

    // Says that the user changed a setting
    private void changed() {
        onChange.run();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, TraceGeneralSettingsForm> {

    }
}
