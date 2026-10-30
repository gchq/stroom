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

package stroom.dashboard.client.component;

import stroom.widget.component.client.Component;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// The Process Search Results dialog's content: how many records to process, and for how long.
public class ProcessorLimits extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    ValueSpinner recordLimit;
    @UiField
    ValueSpinner timeLimit;

    /// Makes the content with 1,000,000 records and 60 minutes.
    public ProcessorLimits() {
        initWidget(BINDER.createAndBindUi(this));
        // ValueSpinner's setValue is overloaded, which UiBinder can't choose between
        recordLimit.setValue(1000000);
        timeLimit.setValue(60);
    }

    /// Puts the focus in the record limit.
    public void focus() {
        recordLimit.focus();
    }

    /// @return The number of records to process.
    public long getRecordLimit() {
        return recordLimit.getIntValue();
    }

    /// @param recordLimit The number of records to process, or null to leave it as it is.
    public void setRecordLimit(final Long recordLimit) {
        if (recordLimit != null) {
            this.recordLimit.setValue(recordLimit);
        }
    }

    /// @return The number of minutes to process for.
    public long getTimeLimitMins() {
        return timeLimit.getIntValue();
    }

    /// @param timeLimitMins The number of minutes to process for, or null to leave it as it is.
    public void setTimeLimitMins(final Long timeLimitMins) {
        if (timeLimitMins != null) {
            timeLimit.setValue(timeLimitMins);
        }
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ProcessorLimits> {

    }
}
