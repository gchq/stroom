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

package stroom.planb.client.view;

import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.planb.shared.TraceSettings;
import stroom.util.shared.ModelStringUtil;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

/**
 * General settings that only apply to a Traces store, shown alongside
 * {@link GeneralSettingsWidget} so that widget stays store-type agnostic.
 */
public class TraceGeneralSettingsWidget extends AbstractSettingsWidget implements ReadOnlyChangeHandler {

    private final Widget widget;

    @UiField
    TextBox maxSpansPerTrace;

    @Inject
    public TraceGeneralSettingsWidget(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    Widget asWidget() {
        return widget;
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
    public void onReadOnly(final boolean readOnly) {
        maxSpansPerTrace.setEnabled(!readOnly);
    }

    @UiHandler("maxSpansPerTrace")
    public void onMaxSpansPerTrace(final ValueChangeEvent<String> event) {
        getUiHandlers().onChange();
    }

    public interface Binder extends UiBinder<Widget, TraceGeneralSettingsWidget> {

    }
}
