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

package stroom.analytics.client.component;

import stroom.widget.button.client.Button;
import stroom.widget.component.client.Component;
import stroom.widget.dropdowntree.client.DropDownBox;
import stroom.widget.tickbox.client.TickBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// Where a notification's detections are written as streams: the destination feed (a picker its
/// controller attaches to), with a button to make it the default, and whether a streaming rule
/// writes to the feed of the stream it processes instead. [AnalyticStreamDestinationController]
/// fills it.
public class AnalyticStreamDestination extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    DropDownBox destinationFeed;
    @UiField
    Button setDefaultDestinationFeed;
    @UiField
    TickBox useSourceFeedIfPossible;

    private Runnable onChange = () -> {
    };
    private Runnable onSetDefault = () -> {
    };

    public AnalyticStreamDestination() {
        initWidget(BINDER.createAndBindUi(this));
        setDefaultDestinationFeed.setOnClick(this::onSetDefaultDestinationFeed);
        useSourceFeedIfPossible.setOnChange(value -> onUseSourceFeedIfPossible());
    }

    /// @return The destination feed's picker.
    public DropDownBox getDestinationFeedBox() {
        return destinationFeed;
    }

    /// @param visible Whether to offer to make the feed the default (only administrators may).
    public void setSetDefaultVisible(final boolean visible) {
        setDefaultDestinationFeed.setVisible(visible);
    }

    /// @return Whether a streaming rule writes to the feed of the stream it processes.
    public boolean isUseSourceFeedIfPossible() {
        return useSourceFeedIfPossible.getValue();
    }

    /// @param useSourceFeedIfPossible Whether a streaming rule writes to the feed of the stream it
    ///                                processes.
    public void setUseSourceFeedIfPossible(final boolean useSourceFeedIfPossible) {
        this.useSourceFeedIfPossible.setValue(useSourceFeedIfPossible);
    }

    /// @param enabled Whether the source feed may be used (only for a streaming rule).
    public void setUseSourceFeedIfPossibleEnabled(final boolean enabled) {
        useSourceFeedIfPossible.setEnabled(enabled);
    }

    /// @param onChange Run when the user changes whether the source feed is used.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    /// @param onSetDefault Run when the user asks to make the feed the default.
    public void setOnSetDefault(final Runnable onSetDefault) {
        this.onSetDefault = onSetDefault;
    }

    private void onSetDefaultDestinationFeed() {
        onSetDefault.run();
    }

    private void onUseSourceFeedIfPossible() {
        onChange.run();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, AnalyticStreamDestination> {

    }
}
