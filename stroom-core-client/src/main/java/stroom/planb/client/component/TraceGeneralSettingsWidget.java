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
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Widget;

/**
 * General settings that only apply to a Traces store, shown alongside
 * {@link GeneralSettingsWidget} so that widget stays store-type agnostic.
 */
public class TraceGeneralSettingsWidget extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    private Runnable onChange = () -> {
    };

    @UiField
    TextBox maxSpansPerTrace;

    /// Makes the settings, at their defaults.
    public TraceGeneralSettingsWidget() {
        initWidget(BINDER.createAndBindUi(this));
    }

    public Long getMaxSpansPerTrace() {
        final String value = maxSpansPerTrace.getValue();
        try {
            final String string = value.trim();
            if (!string.isEmpty()) {
                return ModelStringUtil.parseNumberString(string);
            }
        } catch (final RuntimeException e) {
            // Ignore.
        }
        setMaxSpansPerTrace(TraceSettings.DEFAULT_MAX_SPANS_PER_TRACE);
        return TraceSettings.DEFAULT_MAX_SPANS_PER_TRACE;
    }

    public void setMaxSpansPerTrace(final Long maxSpansPerTrace) {
        this.maxSpansPerTrace.setValue(String.valueOf(maxSpansPerTrace == null
                ? TraceSettings.DEFAULT_MAX_SPANS_PER_TRACE
                : maxSpansPerTrace));
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        maxSpansPerTrace.setReadOnly(readOnly);
    }

    @UiHandler("maxSpansPerTrace")
    public void onMaxSpansPerTrace(final ValueChangeEvent<String> event) {
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


    interface Binder extends UiBinder<Widget, TraceGeneralSettingsWidget> {

    }
}
