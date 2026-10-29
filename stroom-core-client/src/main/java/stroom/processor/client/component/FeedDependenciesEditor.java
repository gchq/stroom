/*
 * Copyright 2026 Crown Copyright
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

package stroom.processor.client.component;

import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;
import stroom.widget.component.client.Component;
import stroom.widget.customdatebox.client.DurationPicker;
import stroom.widget.tickbox.client.TickBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/// The Set Feed Dependencies dialog's content: the feeds a processor filter's streams wait for (a
/// list its controller shows here), and the minimum and maximum processing delays, each enabled by
/// its tick box.
public class FeedDependenciesEditor extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    FeedDependencyList feedDependencies;
    @UiField
    TickBox minProcessingDelayEnabled;
    @UiField
    DurationPicker minProcessingDelay;
    @UiField
    TickBox maxProcessingDelayEnabled;
    @UiField
    DurationPicker maxProcessingDelay;

    /// Makes the content, with no list.
    public FeedDependenciesEditor() {
        initWidget(BINDER.createAndBindUi(this));
        minProcessingDelayEnabled.addValueChangeHandler(e -> updateEnabled());
        maxProcessingDelayEnabled.addValueChangeHandler(e -> updateEnabled());
    }

    /// @return The feed dependencies, which its controller attaches the list's controller to.
    public FeedDependencyList getFeedDependencyList() {
        return feedDependencies;
    }

    /// @param minProcessingDelay The minimum processing delay, or `null` for none (then the box is
    ///                           unticked, showing 30 minutes).
    public void setMinProcessingDelay(final SimpleDuration minProcessingDelay) {
        if (minProcessingDelay == null) {
            this.minProcessingDelayEnabled.setValue(false);
            this.minProcessingDelay.setValue(new SimpleDuration(30L, TimeUnit.MINUTES));
        } else {
            this.minProcessingDelayEnabled.setValue(true);
            this.minProcessingDelay.setValue(minProcessingDelay);
        }
        updateEnabled();
    }

    /// @return The minimum processing delay, or `null` if its box is unticked.
    public SimpleDuration getMinProcessingDelay() {
        if (minProcessingDelayEnabled.getValue()) {
            return minProcessingDelay.getValue();
        }
        return null;
    }

    /// @param maxProcessingDelay The maximum processing delay, or `null` for none (then the box is
    ///                           unticked, showing 1 day).
    public void setMaxProcessingDelay(final SimpleDuration maxProcessingDelay) {
        if (maxProcessingDelay == null) {
            this.maxProcessingDelayEnabled.setValue(false);
            this.maxProcessingDelay.setValue(new SimpleDuration(1L, TimeUnit.DAYS));
        } else {
            this.maxProcessingDelayEnabled.setValue(true);
            this.maxProcessingDelay.setValue(maxProcessingDelay);
        }
        updateEnabled();
    }

    /// @return The maximum processing delay, or `null` if its box is unticked.
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


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, FeedDependenciesEditor> {

    }
}
