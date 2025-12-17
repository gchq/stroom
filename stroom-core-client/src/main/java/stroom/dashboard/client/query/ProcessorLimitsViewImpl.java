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

package stroom.dashboard.client.query;

import stroom.dashboard.client.query.ProcessorLimitsPresenter.ProcessorLimitsView;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

public class ProcessorLimitsViewImpl extends ViewImpl implements ProcessorLimitsView {

    private final Widget widget;
    @UiField
    ValueSpinner recordLimit;
    @UiField
    ValueSpinner timeLimit;

    @Inject
    public ProcessorLimitsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        recordLimit.setMax(1000000000);
        recordLimit.setMin(1);
        recordLimit.setValue(1000000);

        timeLimit.setMax(1440);
        timeLimit.setMin(1);
        timeLimit.setValue(60);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        recordLimit.focus();
    }

    @Override
    public Long getRecordLimit() {
        return Long.valueOf(recordLimit.getIntValue());
    }

    @Override
    public void setRecordLimit(final Long value) {
        if (value != null) {
            recordLimit.setValue(value);
        }
    }

    @Override
    public Long getTimeLimitMins() {
        return Long.valueOf(timeLimit.getIntValue());
    }

    @Override
    public void setTimeLimitMins(final Long value) {
        if (value != null) {
            timeLimit.setValue(value);
        }
    }

    public interface Binder extends UiBinder<Widget, ProcessorLimitsViewImpl> {

    }
}
