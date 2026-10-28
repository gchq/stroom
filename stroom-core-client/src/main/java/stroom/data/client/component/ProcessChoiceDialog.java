/*
 * Copyright 2020 Crown Copyright
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

package stroom.data.client.component;

import stroom.widget.component.client.Component;
import stroom.widget.datepicker.client.DateTimeBox;
import stroom.widget.tickbox.client.TickBox;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// The Create Processors dialog's content, for streams chosen in the data browser: the new
/// processor filters' priority, whether to reprocess, whether they start enabled, and the range
/// of stream create times they take.
public class ProcessChoiceDialog extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final int DEFAULT_PRIORITY = 10;

    @UiField
    ValueSpinner priority;
    @UiField
    TickBox autoPriority;
    @UiField
    TickBox reprocess;
    @UiField
    TickBox enabled;
    @UiField
    DateTimeBox minMetaCreateTimeMs;
    @UiField
    DateTimeBox maxMetaCreateTimeMs;

    private Runnable onReprocessChange = () -> {
    };

    /// Makes the content.
    public ProcessChoiceDialog() {
        initWidget(BINDER.createAndBindUi(this));
        reprocess.setOnChange(value -> onReprocess());
        // setValue is overloaded, so it can't be set in the template
        priority.setValue(DEFAULT_PRIORITY);
    }

    /// Puts the focus in the priority.
    public void focus() {
        priority.focus();
    }

    /// @return The new filters' priority.
    public int getPriority() {
        return priority.getIntValue();
    }

    /// @return Whether the priority is taken from the pipeline's existing filter.
    public boolean isAutoPriority() {
        return autoPriority.getValue();
    }

    /// @return Whether the data is reprocessed.
    public boolean isReprocess() {
        return reprocess.getValue();
    }

    /// @return Whether the new filters start enabled.
    public boolean isEnabled() {
        return enabled.getValue();
    }

    /// @return The earliest stream create time, or null for no lower limit.
    public Long getMinMetaCreateTimeMs() {
        return minMetaCreateTimeMs.getValue();
    }

    /// @return The latest stream create time, or null for no upper limit.
    public Long getMaxMetaCreateTimeMs() {
        return maxMetaCreateTimeMs.getValue();
    }

    /// @param maxMetaCreateTimeMs The latest stream create time, or null for no upper limit.
    public void setMaxMetaCreateTimeMs(final Long maxMetaCreateTimeMs) {
        this.maxMetaCreateTimeMs.setValue(maxMetaCreateTimeMs);
    }

    /// @return The earliest stream create time box, for its controller to attach to.
    public DateTimeBox getMinMetaCreateTimeBox() {
        return minMetaCreateTimeMs;
    }

    /// @return The latest stream create time box, for its controller to attach to.
    public DateTimeBox getMaxMetaCreateTimeBox() {
        return maxMetaCreateTimeMs;
    }

    /// @param onReprocessChange Run when Reprocess data is ticked or unticked.
    public void setOnReprocessChange(final Runnable onReprocessChange) {
        this.onReprocessChange = onReprocessChange;
    }

    private void onReprocess() {
        onReprocessChange.run();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ProcessChoiceDialog> {

    }
}
